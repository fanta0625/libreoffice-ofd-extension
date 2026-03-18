package org.loongoffice.ofd.config;

import org.ofdrw.converter.FontLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 字体映射配置类
 *
 * 负责：
 * 1. 初始化 ofdrw FontLoader 的字体映射
 * 2. 从配置文件加载自定义字体映射
 * 3. 提供统一的字体配置入口
 * 4. 记录使用了 fallback 映射的字体，用于警告提示
 *
 * 配置文件位置（可选）：
 * - src/main/resources/font-mappings.properties
 * - /etc/loongoffice/font-mappings.properties (Linux)
 * - 用户目录: ~/.loongoffice/font-mappings.properties
 */
public class FontMappingConfig {
    private static final Logger logger = LoggerFactory.getLogger(FontMappingConfig.class);

    private static final String CONFIG_FILE = "font-mappings.properties";

    /**
     * 记录使用了 fallback 映射的字体名称
     * Key: 字体名称，Value: fallback 字体路径
     */
    private static final java.util.Map<String, String> fallbackMappedFonts = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 初始化字体映射配置
     *
     * 加载顺序：
     * 1. 清除之前的 fallback 映射记录
     * 2. 加载默认的中文字体映射
     * 3. 扫描项目字体目录（src/main/resources/fonts）
     * 4. 加载用户自定义配置文件（如果存在）
     *
     * 注意：每次调用都会重新检测字体，确保反映系统字体的最新状态
     */
    public static synchronized void initialize() {
        logger.info("Initializing font mappings...");

        // 清除之前的 fallback 映射记录，确保重新检测
        fallbackMappedFonts.clear();

        // 1. 加载默认中文字体映射（每次都重新检测）
        loadDefaultChineseFontMappings();

        // 2. 扫描项目字体目录
        scanProjectFontsDirectory();

        // 3. 加载用户自定义配置
        loadUserConfiguration();

        // 4. 启用相似字体替换
        FontLoader.setSimilarFontReplace(true);

        logger.info("Font mappings initialized successfully");
    }

    /**
     * 加载默认中文字体映射（智能映射）
     *
     * 将 Windows 常用中文字体映射到 Linux 开源字体
     * 使用智能检测：优先使用 fontconfig 找到的字体，只在必要时添加 fallback 映射
     */
    private static void loadDefaultChineseFontMappings() {
        logger.debug("Loading default Chinese font mappings (smart mode)...");

        FontLoader loader = FontLoader.getInstance();

        // 楷体映射
        addSmartMapping(loader, "楷体", "/usr/share/fonts/truetype/arphic/ukai.ttc");
        addSmartMapping(loader, "KaiTi", "/usr/share/fonts/truetype/arphic/ukai.ttc");
        loader.addAliasMapping("AR PL UKai CN", "楷体");

        // 宋体映射（衬线字体）
        addSmartMapping(loader, "宋体", "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc");
        addSmartMapping(loader, "SimSun", "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc");
        loader.addAliasMapping("Noto Serif CJK SC", "宋体");

        // 黑体映射（无衬线字体）
        addSmartMapping(loader, "黑体", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        addSmartMapping(loader, "SimHei", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        loader.addAliasMapping("Noto Sans CJK SC", "黑体");

        // 微软雅黑映射
        addSmartMapping(loader, "微软雅黑", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        addSmartMapping(loader, "Microsoft YaHei", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");

        // 等线映射
        addSmartMapping(loader, "等线", "/usr/share/fonts/opentype/noto/NotoSansCJK-DemiLight.ttc");
        addSmartMapping(loader, "DengXian", "/usr/share/fonts/opentype/noto/NotoSansCJK-DemiLight.ttc");

        // 仿宋映射
        addSmartMapping(loader, "仿宋", "/usr/share/fonts/truetype/cwtex/cwfs.ttf");
        addSmartMapping(loader, "FangSong", "/usr/share/fonts/truetype/cwtex/cwfs.ttf");

        logger.debug("Default Chinese font mappings loaded");
    }

    /**
     * 智能添加字体映射
     *
     * 逻辑：
     * 1. 先使用 FontLoader 的 getSystemFontPath() 检测字体是否已存在
     * 2. 如果已存在（说明系统中有这个字体，如 Windows 字体），不添加映射
     * 3. 如果不存在，检查 fallback 字体文件是否存在
     * 4. 只有 fallback 字体文件存在时才添加映射
     *
     * @param loader FontLoader 实例
     * @param fontName 字体名称
     * @param fallbackPath fallback 字体文件路径
     */
    private static void addSmartMapping(FontLoader loader, String fontName, String fallbackPath) {
        // 先尝试查找字体是否已经存在于系统中
        String existingPath = loader.getSystemFontPath(null, fontName);

        if (existingPath != null) {
            // 系统中已经找到了这个字体
            logger.debug("✓ SmartMapping[{}]: Font already exists in system: {} (skipping fallback)", fontName, existingPath);
            return;
        }

        // 系统中没找到，检查 fallback 字体文件是否存在
        if (Files.exists(Paths.get(fallbackPath))) {
            loader.addSystemFontMapping(fontName, fallbackPath);
            fallbackMappedFonts.put(fontName, fallbackPath);  // 记录使用了 fallback 映射
            logger.info("→ SmartMapping[{}]: Added fallback mapping: {}", fontName, fallbackPath);
        } else {
            logger.debug("Fallback font file not found for '{}': {}", fontName, fallbackPath);
        }
    }

    /**
     * 判断指定字体是否使用了 fallback 映射
     *
     * @param fontName 字体名称
     * @return 如果使用了 fallback 映射返回 true，否则返回 false
     */
    public static boolean isFallbackMappedFont(String fontName) {
        return fallbackMappedFonts.containsKey(fontName);
    }

    /**
     * 获取字体的 fallback 映射路径
     *
     * @param fontName 字体名称
     * @return fallback 字体路径，如果没有使用 fallback 返回 null
     */
    public static String getFallbackMapping(String fontName) {
        return fallbackMappedFonts.get(fontName);
    }

    /**
     * 获取所有使用了 fallback 映射的字体
     *
     * @return 字体名称到 fallback 路径的映射
     */
    public static java.util.Map<String, String> getAllFallbackMappings() {
        return new java.util.HashMap<>(fallbackMappedFonts);
    }

    /**
     * 扫描项目字体目录
     *
     * 如果 src/main/resources/fonts 目录存在，扫描其中的字体文件
     */
    private static void scanProjectFontsDirectory() {
        File fontDir = new File("src/main/resources/fonts");
        if (fontDir.exists() && fontDir.isDirectory()) {
            logger.info("Scanning project fonts directory: {}", fontDir.getAbsolutePath());
            FontLoader.getInstance().scanFontDir(fontDir);
            logger.debug("Project fonts directory scanned");
        } else {
            logger.debug("Project fonts directory not found: {}", fontDir.getAbsolutePath());
        }
    }

    /**
     * 加载用户自定义配置
     *
     * 按优先级查找配置文件：
     * 1. classpath:font-mappings.properties
     * 2. 用户目录: ~/.loongoffice/font-mappings.properties
     * 3. 系统目录: /etc/loongoffice/font-mappings.properties (仅 Linux)
     */
    private static void loadUserConfiguration() {
        // 1. 尝试从 classpath 加载
        loadFromClasspath();

        // 2. 尝试从用户目录加载
        loadFromUserDirectory();

        // 3. 尝试从系统目录加载（仅 Linux）
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            loadFromSystemDirectory();
        }
    }

    /**
     * 从 classpath 加载配置
     */
    private static void loadFromClasspath() {
        try (InputStream is = FontMappingConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                loadFromInputStream(is, "classpath:" + CONFIG_FILE);
            } else {
                logger.debug("No font mapping configuration found in classpath");
            }
        } catch (IOException e) {
            logger.warn("Failed to load font mapping from classpath: {}", e.getMessage());
        }
    }

    /**
     * 从用户目录加载配置
     */
    private static void loadFromUserDirectory() {
        String userHome = System.getProperty("user.home");
        Path userConfigPath = Path.of(userHome, ".loongoffice", CONFIG_FILE);

        if (Files.exists(userConfigPath)) {
            try (InputStream is = Files.newInputStream(userConfigPath)) {
                loadFromInputStream(is, userConfigPath.toString());
            } catch (IOException e) {
                logger.warn("Failed to load font mapping from user directory: {}", e.getMessage());
            }
        } else {
            logger.debug("No user font mapping configuration found: {}", userConfigPath);
        }
    }

    /**
     * 从系统目录加载配置（仅 Linux）
     */
    private static void loadFromSystemDirectory() {
        Path systemConfigPath = Path.of("/etc/loongoffice", CONFIG_FILE);

        if (Files.exists(systemConfigPath)) {
            try (InputStream is = Files.newInputStream(systemConfigPath)) {
                loadFromInputStream(is, systemConfigPath.toString());
            } catch (IOException e) {
                logger.warn("Failed to load font mapping from system directory: {}", e.getMessage());
            }
        } else {
            logger.debug("No system font mapping configuration found: {}", systemConfigPath);
        }
    }

    /**
     * 从输入流加载配置
     *
     * 配置文件格式（Properties）：
     * font.楷体=/usr/share/fonts/truetype/arphic/ukai.ttc
     * font.宋体=/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc
     * alias.AR PL UKai CN=楷体
     */
    private static void loadFromInputStream(InputStream is, String source) {
        try {
            Properties props = new Properties();
            props.load(is);

            FontLoader loader = FontLoader.getInstance();
            int fontMappingCount = 0;
            int aliasMappingCount = 0;

            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);

                if (key.startsWith("font.")) {
                    // 字体映射：font.字体名=字体文件路径
                    String fontName = key.substring(5);
                    loader.addSystemFontMapping(fontName, value);
                    fontMappingCount++;
                    logger.debug("Font mapping: {} -> {}", fontName, value);

                } else if (key.startsWith("alias.")) {
                    // 别名映射：alias.别名=真实字体名
                    String alias = key.substring(6);
                    loader.addAliasMapping(alias, value);
                    aliasMappingCount++;
                    logger.debug("Alias mapping: {} -> {}", alias, value);

                } else {
                    logger.warn("Unknown configuration key: {}", key);
                }
            }

            logger.info("Loaded {} font mappings and {} alias mappings from {}",
                    fontMappingCount, aliasMappingCount, source);

        } catch (IOException e) {
            logger.error("Failed to parse font mapping configuration from {}: {}", source, e.getMessage());
        }
    }

    /**
     * 重置初始化状态（主要用于测试）
     */
    static synchronized void reset() {
        fallbackMappedFonts.clear();
    }
}
