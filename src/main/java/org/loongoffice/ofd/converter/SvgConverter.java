package org.loongoffice.ofd.converter;

import com.sun.star.awt.Point;
import com.sun.star.awt.Size;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.XPropertySet;
import com.sun.star.drawing.XDrawPage;
import com.sun.star.drawing.XDrawPagesSupplier;
import com.sun.star.drawing.XShape;
import com.sun.star.graphic.XGraphic;
import com.sun.star.graphic.XGraphicProvider;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import org.loongoffice.ofd.config.FontMappingConfig;
import org.loongoffice.ofd.document.DocumentHandler;
import org.ofdrw.converter.export.SVGExporter;
import org.ofdrw.reader.OFDReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.loongoffice.ofd.util.I18nMessages;
import org.loongoffice.ofd.util.ProgressTracker;
import org.loongoffice.ofd.util.TempFileManager;

import java.nio.file.Path;

/**
 * SVG转换器
 * 负责将OFD文件转换为SVG并插入到LibreOffice文档
 */
public class SvgConverter {
    private static final Logger logger = LoggerFactory.getLogger(SvgConverter.class);
    private static final double DEFAULT_PPM = 15d; // Pixels Per Millimeter

    private final XComponentContext context;
    private final DocumentHandler documentHandler;

    /**
     * 创建SVG转换器
     *
     * @param context UNO组件上下文
     * @param documentHandler 文档处理器
     */
    public SvgConverter(XComponentContext context, DocumentHandler documentHandler) {
        this.context = context;
        this.documentHandler = documentHandler;
    }

    /**
     * 转换OFD文件并插入到文档
     *
     * @param ofdPath OFD文件路径
     * @param tempFileManager 临时文件管理器
     * @param progressTracker 进度跟踪器
     * @return true如果转换成功
     */
    public boolean convertAndInsert(Path ofdPath, TempFileManager tempFileManager, ProgressTracker progressTracker) {
        try {
            // 1. Read OFD to get page count
            int ofdPageCount = readOfdPageCount(ofdPath);
            if (ofdPageCount == 0) {
                logger.warn("OFD file has no pages");
                return false;
            }

            logger.info("OFD document has {} page(s)", ofdPageCount);

            // 2. Initialize font mappings
            FontMappingConfig.initialize();

            // 3. Export all pages to SVG
            if (!exportToSvg(ofdPath, tempFileManager.getTempDir(), ofdPageCount, progressTracker)) {
                return false;
            }

            // 4. Insert SVG pages into document
            return insertSvgPages(ofdPath, tempFileManager, ofdPageCount, progressTracker);

        } catch (Exception e) {
            logger.error("Error during OFD conversion", e);
            return false;
        }
    }

    /**
     * 读取OFD文件的页数
     *
     * @param ofdPath OFD文件路径
     * @return 页数，如果失败则返回0
     */
    private int readOfdPageCount(Path ofdPath) {
        try (OFDReader reader = new OFDReader(ofdPath)) {
            return reader.getNumberOfPages();
        } catch (Exception e) {
            logger.error("Failed to read OFD page count", e);
            return 0;
        }
    }

    /**
     * 导出OFD到SVG
     *
     * @param ofdPath OFD文件路径
     * @param tempDir 临时目录
     * @param pageCount 页数
     * @param progressTracker 进度跟踪器
     * @return true如果导出成功
     */
    private boolean exportToSvg(Path ofdPath, Path tempDir, int pageCount, ProgressTracker progressTracker) {
        try (SVGExporter exporter = new SVGExporter(ofdPath, tempDir, DEFAULT_PPM)) {
            for (int i = 0; i < pageCount; i++) {
                exporter.export(i);
                logger.debug("Exported page {}/{}", i + 1, pageCount);
            }

            if (progressTracker.isAvailable()) {
                progressTracker.setText(I18nMessages.getMessage("progress.converting.svg"));
                progressTracker.setValue(60);
            }

            logger.info("SVG export completed");
            return true;
        } catch (Exception e) {
            logger.error("SVG export failed", e);
            return false;
        }
    }

    /**
     * 插入SVG页面到文档
     *
     * @param ofdPath OFD文件路径
     * @param tempFileManager 临时文件管理器
     * @param pageCount 页数
     * @param progressTracker 进度跟踪器
     * @return true如果插入成功
     */
    private boolean insertSvgPages(Path ofdPath, TempFileManager tempFileManager, int pageCount,
                                   ProgressTracker progressTracker) {
        XComponent targetDocument = documentHandler.getTargetDocument();
        if (targetDocument == null) {
            logger.error("Target document is null");
            return false;
        }

        XDrawPagesSupplier drawSupplier = documentHandler.getDrawPagesSupplier();
        if (drawSupplier == null) {
            logger.error("Not a Draw document");
            return false;
        }

        // Reopen OFDReader to get page dimensions
        try (OFDReader reader = new OFDReader(ofdPath)) {
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                if (!insertSinglePage(reader, pageIndex, pageCount, tempFileManager, progressTracker)) {
                    logger.error("Failed to insert page {}", pageIndex + 1);
                }

                if (progressTracker.isAvailable()) {
                    int progress = 70 + (int) ((pageIndex + 1.0) / pageCount * 20);
                    progressTracker.setValue(progress);
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to insert SVG pages", e);
            return false;
        }
    }

    /**
     * 插入单个页面
     *
     * @param reader OFD阅读器
     * @param pageIndex 页面索引
     * @param pageCount 总页数
     * @param tempFileManager 临时文件管理器
     * @param progressTracker 进度跟踪器
     * @return true如果成功
     */
    private boolean insertSinglePage(OFDReader reader, int pageIndex, int pageCount,
                                     TempFileManager tempFileManager, ProgressTracker progressTracker) {
        try {
            // Get or create Draw page
            XDrawPage drawPage = getOrCreateDrawPage(pageIndex);
            if (drawPage == null) {
                return false;
            }

            // Get and set page dimensions
            setPageDimensions(reader, pageIndex, drawPage);

            // Find SVG file
            Path svgPath = tempFileManager.resolve(pageIndex + ".svg");
            if (!svgPath.toFile().exists()) {
                logger.warn("SVG not found for page {}: {}", pageIndex + 1, svgPath);
                return false;
            }

            // Load and insert SVG graphic
            return insertSvgGraphic(svgPath, drawPage);

        } catch (Exception e) {
            logger.error("Failed to insert page " + (pageIndex + 1), e);
            return false;
        }
    }

    /**
     * 获取或创建Draw页面
     *
     * @param pageIndex 页面索引
     * @return DrawPage对象
     */
    private XDrawPage getOrCreateDrawPage(int pageIndex) {
        if (pageIndex == 0) {
            // Use existing first page
            return documentHandler.getDrawPage(0);
        } else {
            // Insert new page
            XDrawPage newPage = documentHandler.insertNewPage(pageIndex);
            if (newPage != null) {
                logger.debug("Created new Draw page {}", pageIndex + 1);
            }
            return newPage;
        }
    }

    /**
     * 设置页面尺寸
     *
     * @param reader OFD阅读器
     * @param pageIndex 页面索引
     * @param drawPage Draw页面
     */
    private void setPageDimensions(OFDReader reader, int pageIndex, XDrawPage drawPage) {
        try {
            // Note: ofdrw uses 1-based page index
            org.ofdrw.core.basicStructure.pageObj.Page ofdPage = reader.getPage(pageIndex + 1);
            if (ofdPage != null && ofdPage.getArea() != null && ofdPage.getArea().getPhysicalBox() != null) {
                double widthMM = ofdPage.getArea().getPhysicalBox().getWidth();
                double heightMM = ofdPage.getArea().getPhysicalBox().getHeight();
                int widthLO = (int) (widthMM * 100);  // Convert to LibreOffice units
                int heightLO = (int) (heightMM * 100);

                documentHandler.setPageSize(pageIndex, widthLO, heightLO);
                logger.debug("Page {} size: {}mm × {}mm", pageIndex + 1, widthMM, heightMM);
            }
        } catch (Exception e) {
            logger.warn("Failed to get page {} dimensions, using default", pageIndex + 1);
        }
    }

    /**
     * 插入SVG图形到页面
     *
     * @param svgPath SVG文件路径
     * @param drawPage Draw页面
     * @return true如果成功
     */
    private boolean insertSvgGraphic(Path svgPath, XDrawPage drawPage) {
        try {
            XComponent targetDocument = documentHandler.getTargetDocument();
            XMultiServiceFactory msf = UnoRuntime.queryInterface(XMultiServiceFactory.class, targetDocument);
            if (msf == null) {
                logger.error("Failed to get MultiServiceFactory");
                return false;
            }

            // Create graphic shape
            Object graphicObj = msf.createInstance("com.sun.star.drawing.GraphicObjectShape");
            XShape graphicShape = UnoRuntime.queryInterface(XShape.class, graphicObj);
            XPropertySet graphicProps = UnoRuntime.queryInterface(XPropertySet.class, graphicObj);

            // Load SVG
            if (!loadSvgGraphic(svgPath, graphicProps)) {
                return false;
            }

            // Set position and size (will be set by caller based on page size)
            XDrawPagesSupplier supplier = documentHandler.getDrawPagesSupplier();
            int pageIndex = supplier.getDrawPages().getCount() - 1;

            // Get page dimensions
            com.sun.star.beans.XPropertySet pageProps = UnoRuntime.queryInterface(
                    com.sun.star.beans.XPropertySet.class, drawPage);

            int width = 21000; // Default A4
            int height = 29700;

            if (pageProps != null) {
                try {
                    width = (Integer) pageProps.getPropertyValue("Width");
                    height = (Integer) pageProps.getPropertyValue("Height");
                } catch (Exception e) {
                    // Use default
                }
            }

            graphicShape.setPosition(new Point(0, 0));
            graphicShape.setSize(new Size(width, height));

            // Add to page
            return documentHandler.insertShape(pageIndex, graphicShape);

        } catch (Exception e) {
            logger.error("Failed to insert SVG graphic", e);
            return false;
        }
    }

    /**
     * 加载SVG图形
     *
     * @param svgPath SVG文件路径
     * @param graphicProps 图形属性
     * @return true如果成功
     */
    private boolean loadSvgGraphic(Path svgPath, XPropertySet graphicProps) {
        try {
            XMultiComponentFactory mcf = context.getServiceManager();
            Object graphicProvider = mcf.createInstanceWithContext(
                    "com.sun.star.graphic.GraphicProvider", context);
            XGraphicProvider xGraphicProvider = UnoRuntime.queryInterface(
                    XGraphicProvider.class, graphicProvider);

            String svgUrl = svgPath.toUri().toString();
            PropertyValue[] loadProps = new PropertyValue[1];
            loadProps[0] = new PropertyValue();
            loadProps[0].Name = "URL";
            loadProps[0].Value = svgUrl;

            XGraphic xGraphic = xGraphicProvider.queryGraphic(loadProps);

            if (xGraphic != null) {
                graphicProps.setPropertyValue("Graphic", xGraphic);
            } else {
                graphicProps.setPropertyValue("GraphicURL", svgUrl);
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to load SVG graphic", e);
            return false;
        }
    }
}
