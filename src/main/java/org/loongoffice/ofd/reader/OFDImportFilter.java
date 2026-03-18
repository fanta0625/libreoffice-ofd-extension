package org.loongoffice.ofd.reader;

import com.sun.star.beans.PropertyValue;
import com.sun.star.document.XExtendedFilterDetection;
import com.sun.star.document.XFilter;
import com.sun.star.document.XImporter;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XInitialization;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.lib.uno.helper.Factory;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import org.loongoffice.ofd.config.ReadOnlyConfig;
import org.loongoffice.ofd.converter.SvgConverter;
import org.loongoffice.ofd.document.DocumentHandler;
import org.loongoffice.ofd.font.FontChecker;
import org.loongoffice.ofd.util.FontWarningDialog;
import org.loongoffice.ofd.util.I18nMessages;
import org.loongoffice.ofd.util.MessageBox;
import org.loongoffice.ofd.util.ProgressTracker;
import org.loongoffice.ofd.util.TempFileManager;
import org.loongoffice.ofd.util.UrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;

/**
 * OFD Import Filter that converts OFD pages to SVG and inserts them into LibreOffice Draw.
 * This is the main filter class that coordinates the conversion process.
 *
 * Refactored to use helper classes for better separation of concerns.
 */
public class OFDImportFilter extends WeakBase implements
        XImporter, XFilter, XExtendedFilterDetection, XInitialization,
        com.sun.star.container.XNamed, XServiceInfo {

    private static final Logger logger = LoggerFactory.getLogger(OFDImportFilter.class);

    private static final String IMPLEMENTATION_NAME = "org.loongoffice.ofd.reader.OFDImportFilter";
    private static final String[] SERVICE_NAMES = {
            "org.loongoffice.ofd.reader.OFDImportFilter",
            "com.sun.star.document.ImportFilter"
    };

    // Component factory for modern UNO (LibreOffice 4.0+)
    public static XSingleComponentFactory __getComponentFactory(String sImplementationName) {
        logStatic("__getComponentFactory called: " + sImplementationName);
        XSingleComponentFactory xFactory = null;
        if (sImplementationName.equals(IMPLEMENTATION_NAME)) {
            xFactory = Factory.createComponentFactory(OFDImportFilter.class, SERVICE_NAMES);
        }
        return xFactory;
    }

    // Registration method - required for backwards compatibility
    public static boolean __writeRegistryServiceInfo(XRegistryKey xRegistryKey) {
        logStatic("__writeRegistryServiceInfo called");
        return Factory.writeRegistryServiceInfo(IMPLEMENTATION_NAME, SERVICE_NAMES, xRegistryKey);
    }

    private static Path getLogFilePath() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        return Paths.get(tmpDir, "ofd-import-filter.log");
    }

    private static void logStatic(String message) {
        try {
            Path logFile = getLogFilePath();
            String timestamp = new Date().toString();
            String logMessage = timestamp + " - [STATIC] " + message + "\n";
            Files.write(logFile, logMessage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("[STATIC] {}", message);
        } catch (IOException e) {
            logger.warn("Failed to write log file", e);
        }
    }


    // Instance variables
    private DocumentHandler documentHandler;
    private String internalName = IMPLEMENTATION_NAME;
    private XComponentContext context;

    /**
     * Constructor with context (required for modern UNO components)
     */
    public OFDImportFilter(XComponentContext xContext) {
        this.context = xContext;
        this.documentHandler = new DocumentHandler(context);
        logger.debug("Constructor called with context");
    }

    // XImporter
    @Override
    public void setTargetDocument(XComponent document) {
        documentHandler.setTargetDocument(document);
    }

    // XFilter
    @Override
    public boolean filter(PropertyValue[] aDescriptor) {
        logger.info("OFDImportFilter.filter called");

        TempFileManager tempFileManager = null;
        ProgressTracker progressTracker = null;

        try {
            // Step 1: Initialize progress
            progressTracker = new ProgressTracker(aDescriptor, documentHandler.getTargetDocument());
            progressTracker.start(I18nMessages.getMessage("progress.converting"), 100);

            // Step 2: Extract and validate URL
            String url = UrlResolver.extractUrl(aDescriptor);
            if (url.isEmpty()) {
                logger.debug("findUrl: no URL property found");
                return false;
            }

            logger.debug("URL: " + url);
            progressTracker.setText(I18nMessages.getMessage("progress.initializing"));
            progressTracker.setValue(10);

            // Step 3: Ensure target document exists
            if (!documentHandler.ensureTargetDocument()) {
                logger.debug("targetDocument is still null after creation attempt");
                return false;
            }

            progressTracker.setText(I18nMessages.getMessage("progress.parsing"));
            progressTracker.setValue(20);

            // Step 4: Validate OFD file exists
            Path ofdPath = UrlResolver.urlToPath(url);
            if (!Files.exists(ofdPath)) {
                logger.warn("OFD file not found at: " + ofdPath);
                return false;
            }

            logger.debug("OFD file found: " + ofdPath + " (size: " + Files.size(ofdPath) + " bytes)");

            // Step 5: Check fonts and show warning if needed
            progressTracker.setText(I18nMessages.getMessage("progress.checking.fonts"));
            progressTracker.setValue(25);

            // 检查字体
            FontChecker.FontCheckResult fontCheckResult = FontChecker.checkFonts(ofdPath.toString());

            // 检查是否需要显示警告对话框
            boolean hasGenericFont = fontCheckResult.getAvailableFonts().stream()
                    .anyMatch(FontChecker.FontInfo::isGenericFont);
            boolean hasFallbackMapped = fontCheckResult.getAvailableFonts().stream()
                    .anyMatch(FontChecker.FontInfo::isFallbackMapped);

            // 如果有缺失字体、通用字体替换或 fallback 映射，显示警告对话框
            if (fontCheckResult.hasMissingFonts() || hasGenericFont || hasFallbackMapped) {
                String title = FontWarningDialog.buildCaption(fontCheckResult);

                // 如果 title 为 null，说明没有警告需要显示，直接继续
                if (title == null) {
                    logger.info("Font check passed, continuing with conversion");
                } else {
                    // 检测是否是 headless 模式（批量打印场景）
                    if (isHeadlessMode()) {
                        // Headless 模式：将警告记录到日志，跳过对话框
                        String message = FontWarningDialog.buildWarningMessage(fontCheckResult);
                        logger.info("HEADLESS MODE: Font warning suppressed (batch printing mode)");
                        logger.info("HEADLESS MODE: Warning title: " + title);
                        logger.info("HEADLESS MODE: Warning message:\n" + message.replace("\n", " | "));
                        logger.info("Font check passed (auto-continue in headless mode), continuing with conversion");
                    } else {
                        // GUI 模式：显示警告对话框
                        String message = FontWarningDialog.buildWarningMessage(fontCheckResult);

                        // 使用 WARNINGBOX + YES/NO 按钮显示警告对话框
                        short userChoice = MessageBox.showWarningConfirm(context, documentHandler.getTargetDocument(), title, message);
                        logger.debug("User choice return value: " + userChoice + " (0=YES/First button, 1=NO/Second button)");

                        // LibreOffice 标准返回值：YES=0, NO=1（左边按钮值小于右边按钮）
                        // LibreOffice 25.8 观察到：YES=2, NO=3
                        // 判断逻辑：较小的值 = YES（继续），较大的值 = NO（取消）
                        // 0 和 2 是有效范围（2个按钮：0,1 或 2,3）
                        if (userChoice == 0 || userChoice == 2) {
                            // 用户点击了"是"（左边按钮，值较小）
                            logger.info("Font check passed, continuing with conversion");
                        } else {
                            // 用户点击了"否"或按ESC（右边按钮或其他，值较大）
                            logger.info("Font check cancelled by user");
                            return false; // User chose to cancel, LibreOffice 会显示错误消息框并关闭文档窗口
                        }
                    }
                }
            } else {
                logger.info("Font check passed, continuing with conversion");
            }

            // Step 6: Create temp file manager
            tempFileManager = new TempFileManager();
            logger.debug("Created temp directory: " + tempFileManager.getTempDir());

            // Step 7: Convert OFD to SVG and insert
            SvgConverter svgConverter = new SvgConverter(context, documentHandler);
            boolean success = svgConverter.convertAndInsert(ofdPath, tempFileManager, progressTracker);

            // Step 8: Update progress
            if (success) {
                if (progressTracker.isAvailable()) {
                    progressTracker.setText(I18nMessages.getMessage("progress.completed"));
                    progressTracker.setValue(100);
                }
            }

            return success;

        } catch (Exception e) {
            logException(e);
            return false;
        } finally {
            // Clean up
            if (progressTracker != null) {
                progressTracker.complete();
            }

            if (tempFileManager != null) {
                logger.debug("Cleaning up temp directory: " + tempFileManager.getTempDir());
                tempFileManager.cleanup();
                logger.debug("Temp directory cleanup completed");
            }
        }
    }

    // XFilter
    @Override
    public void cancel() {
        // No-op for this implementation
    }

    // XInitialization
    @Override
    public void initialize(Object[] aArguments) {
        // No initialization needed
    }

    // XNamed
    @Override
    public String getName() {
        return internalName;
    }

    @Override
    public void setName(String aName) {
        // Ignore; name comes from FilterFactory
    }

    // XExtendedFilterDetection
    @Override
    public String detect(PropertyValue[][] aDescriptor) {
        // IMPORTANT: This method is called for ALL document types in LibreOffice
        // Use logSimple() to ensure logging works even if SLF4J fails

        // Log entry to verify if this method is being called for non-OFD files
        logger.debug("detect() CALLED - checking file type");

        if (aDescriptor == null || aDescriptor.length == 0) {
            logger.debug("detect: descriptor is null or empty, returning empty");
            return "";
        }

        String url = UrlResolver.extractUrl(aDescriptor[0]);
        String fileName = url.contains("/") ? url.substring(url.lastIndexOf("/") + 1) : url;
        logger.debug("detect: file = " + fileName);

        // EARLY RETURN: Not an OFD file
        if (!UrlResolver.isOfdFile(url)) {
            // Not an OFD file - log it and return immediately
            logger.debug("detect: ✗ NOT .ofd, skipping");
            return "";
        }

        // It's an OFD file - process it
        logger.info("detect: ✓ IS .ofd file, processing");
        addReadOnlyPropertiesToDescriptor(aDescriptor);
        return "ofd_OpenFormDocument";
    }

    // XServiceInfo
    @Override
    public String getImplementationName() {
        return IMPLEMENTATION_NAME;
    }

    @Override
    public boolean supportsService(String serviceName) {
        for (String name : SERVICE_NAMES) {
            if (name.equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String[] getSupportedServiceNames() {
        return SERVICE_NAMES;
    }

    private void logException(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            String exceptionDetails = "Exception: " + t.toString() + "\n" + sw.toString();

            Path logFile = getLogFilePath();
            String timestamp = new Date().toString();
            String logMessage = timestamp + " - " + exceptionDetails + "\n";
            Files.write(logFile, logMessage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            logger.error("Exception occurred", t);
        } catch (IOException e) {
            logger.warn("Failed to log exception", e);
        }
    }

    /**
     * Detect if running in headless mode (no GUI available)
     * This is used to skip interactive dialogs in batch printing scenarios
     *
     * @return true if running in headless mode, false otherwise
     */
    private boolean isHeadlessMode() {
        // Method 1: Check Java system property
        if (Boolean.parseBoolean(System.getProperty("java.awt.headless"))) {
            logger.info("Headless mode detected (java.awt.headless=true)");
            return true;
        }

        // Method 2: Check DISPLAY environment variable (Linux/Unix)
        String display = System.getenv("DISPLAY");
        if (display == null || display.isEmpty()) {
            logger.info("Headless mode detected (DISPLAY environment variable not set)");
            return true;
        }

        // Method 3: Check if running with LibreOffice --headless flag
        // by checking command line arguments or LibreOffice specific properties
        String libreOfficeMode = System.getProperty("soffice.mode");
        if ("headless".equals(libreOfficeMode)) {
            logger.info("Headless mode detected (soffice.mode=headless)");
            return true;
        }

        logger.debug("GUI mode detected (java.awt.headless=false, DISPLAY=" + display + ")");
        return false;
    }

    /**
     * Add read-only properties to the descriptor
     * This is called during type detection to pass read-only settings to the loader
     */
    private void addReadOnlyPropertiesToDescriptor(PropertyValue[][] aDescriptor) {
        if (aDescriptor == null || aDescriptor.length == 0) {
            return;
        }
        aDescriptor[0] = ReadOnlyConfig.addReadOnlyProperties(aDescriptor[0]);
    }
}
