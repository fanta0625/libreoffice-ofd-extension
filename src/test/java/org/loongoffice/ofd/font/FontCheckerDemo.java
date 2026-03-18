package org.loongoffice.ofd.font;

import org.loongoffice.ofd.config.FontMappingConfig;
import org.ofdrw.converter.FontLoader;
import org.ofdrw.core.text.font.CT_Font;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.reader.ResourceManage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * OFD 文档字体检查工具
 *
 * 功能：
 * 1. 获取 OFD 文档中使用的所有字体
 * 2. 检查系统中是否包含这些字体
 * 3. 生成缺失字体报告
 *
 * 使用方法：
 * java org.loongoffice.ofd.font.FontCheckerDemo <ofd文件路径>
 */
public class FontCheckerDemo {

    /**
     * 字体信息类
     */
    public static class FontInfo {
        private final String familyName;
        private final String fontName;
        private final String fontFile;
        private final boolean isEmbedded;
        private final boolean isAvailable;
        private final String systemFontPath;  // 实际找到的系统字体路径
        private final boolean isGenericFont;  // 是否使用了通用字体替换

        public FontInfo(String familyName, String fontName, String fontFile, boolean isEmbedded, boolean isAvailable, String systemFontPath, boolean isGenericFont) {
            this.familyName = familyName;
            this.fontName = fontName;
            this.fontFile = fontFile;
            this.isEmbedded = isEmbedded;
            this.isAvailable = isAvailable;
            this.systemFontPath = systemFontPath;
            this.isGenericFont = isGenericFont;
        }

        public String getFamilyName() {
            return familyName;
        }

        public String getFontName() {
            return fontName;
        }

        public String getFontFile() {
            return fontFile;
        }

        public boolean isEmbedded() {
            return isEmbedded;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public String getSystemFontPath() {
            return systemFontPath;
        }

        public boolean isGenericFont() {
            return isGenericFont;
        }

        @Override
        public String toString() {
            return String.format("FontInfo{familyName='%s', fontName='%s', fontFile='%s', isEmbedded=%s, isAvailable=%s, systemFontPath='%s', isGenericFont=%s}",
                    familyName, fontName, fontFile, isEmbedded, isAvailable, systemFontPath, isGenericFont);
        }
    }

    /**
     * 字体检查结果
     */
    public static class FontCheckResult {
        private final List<FontInfo> allFonts;
        private final List<FontInfo> missingFonts;
        private final List<FontInfo> availableFonts;

        public FontCheckResult(List<FontInfo> allFonts, List<FontInfo> missingFonts, List<FontInfo> availableFonts) {
            this.allFonts = allFonts;
            this.missingFonts = missingFonts;
            this.availableFonts = availableFonts;
        }

        public List<FontInfo> getAllFonts() {
            return allFonts;
        }

        public List<FontInfo> getMissingFonts() {
            return missingFonts;
        }

        public List<FontInfo> getAvailableFonts() {
            return availableFonts;
        }

        public boolean hasMissingFonts() {
            return !missingFonts.isEmpty();
        }

        public void printReport() {
            System.out.println("=".repeat(80));
            System.out.println("OFD 文档字体检查报告");
            System.out.println("=".repeat(80));
            System.out.println("总字体数: " + allFonts.size());
            System.out.println("可用字体数: " + availableFonts.size());
            System.out.println("缺失字体数: " + missingFonts.size());
            System.out.println();

            if (!availableFonts.isEmpty()) {
                System.out.println("✓ 可用字体列表:");
                System.out.println("-".repeat(80));
                for (FontInfo font : availableFonts) {
                    System.out.printf("  ✓ %s (%s)%n",
                            font.getFontName(),
                            font.getFamilyName() != null ? font.getFamilyName() : "N/A");

                    // 显示嵌入字体文件
                    if (font.getFontFile() != null) {
                        System.out.println("    嵌入文件: " + font.getFontFile());
                    }

                    // 显示系统字体路径（如果有的话）
                    if (font.getSystemFontPath() != null) {
                        System.out.println("    系统字体: " + font.getSystemFontPath());
                        // 如果使用了通用字体，给出警告
                        if (font.isGenericFont()) {
                            System.out.println("    ⚠️  使用通用字体，原始字体样式可能会丢失");
                        }
                    }
                }
                System.out.println();
            }

            if (!missingFonts.isEmpty()) {
                System.out.println("✗ 缺失字体列表:");
                System.out.println("-".repeat(80));
                for (FontInfo font : missingFonts) {
                    System.out.printf("  ✗ %s (%s)%n",
                            font.getFontName(),
                            font.getFamilyName() != null ? font.getFamilyName() : "N/A");
                }
                System.out.println();
                System.out.println("警告: 文档中使用了 " + missingFonts.size() + " 种系统中不存在的字体。");
                System.out.println("这些字体将被替换为默认字体，可能影响文档显示效果。");
            } else {
                System.out.println("✓ 所有字体都已安装，文档可以正常显示。");

                // 检查是否有通用字体替换的情况
                boolean hasGenericFont = availableFonts.stream().anyMatch(FontInfo::isGenericFont);
                if (hasGenericFont) {
                    System.out.println();
                    System.out.println("ℹ️  说明: 部分字体使用了通用字体（如 NotoSansCJK），虽然可以正常显示中文");
                    System.out.println("    但可能会丢失原始字体的样式（如宋体、楷体的不同风格）。");
                    System.out.println("    如需完美显示，建议安装对应的专用字体。");
                }
            }

            System.out.println("=".repeat(80));
        }
    }

    /**
     * 检查 OFD 文档的字体
     *
     * @param ofdFilePath OFD 文件路径
     * @return 字体检查结果
     * @throws Exception 读取文件异常
     */
    public static FontCheckResult checkFonts(String ofdFilePath) throws Exception {
        Path ofdPath = Paths.get(ofdFilePath);

        if (!java.nio.file.Files.exists(ofdPath)) {
            throw new IllegalArgumentException("OFD 文件不存在: " + ofdFilePath);
        }

        // 初始化字体映射配置（包含默认映射和自定义配置）
        FontMappingConfig.initialize();

        // 初始化字体加载器
        FontLoader fontLoader = FontLoader.getInstance();

        List<FontInfo> allFonts = new ArrayList<>();
        List<FontInfo> missingFonts = new ArrayList<>();
        List<FontInfo> availableFonts = new ArrayList<>();

        try (OFDReader reader = new OFDReader(ofdPath)) {
            ResourceManage resMgt = reader.getResMgt();
            List<CT_Font> fonts = resMgt.getFonts();

            System.out.println("从文档中读取到 " + fonts.size() + " 个字体定义");

            for (CT_Font ctFont : fonts) {
                String familyName = ctFont.getFamilyName();
                String fontName = ctFont.getFontName();
                String fontFile = ctFont.getFontFile() != null ? ctFont.getFontFile().toString() : null;

                // 检查是否是嵌入字体
                boolean isEmbedded = fontFile != null;

                // 检查系统是否有该字体
                String systemFontPath = fontLoader.getSystemFontPath(familyName, fontName);

                // 验证字体文件是否真的是中文字体
                // 如果返回的是 NotoSans-Regular 或其他明显的西文字体，认为是无效替换
                boolean isChineseFont = true;
                boolean isGenericFont = false;  // 是否使用了通用字体替换

                if (systemFontPath != null) {
                    String pathLower = systemFontPath.toLowerCase();

                    // 检测明显的非中文字体文件名（无效替换）
                    if (pathLower.contains("notosans-regular") ||
                        pathLower.contains("arial") ||
                        pathLower.contains("times") ||
                        pathLower.contains("courier") ||
                        (pathLower.contains("noto") && !pathLower.contains("cjk"))) {
                        isChineseFont = false;
                    }

                    // 检测通用字体替换（NotoSansCJK 等）
                    // 虽然 CJK 可以显示中文，但会丢失字体样式
                    if (pathLower.contains("notosanscjk") ||
                        pathLower.contains("notoserifcjk")) {
                        isGenericFont = true;
                    }
                }

                boolean isAvailable = (systemFontPath != null && isChineseFont) || isEmbedded;

                FontInfo fontInfo = new FontInfo(familyName, fontName, fontFile, isEmbedded, isAvailable, systemFontPath, isGenericFont);
                allFonts.add(fontInfo);

                if (isAvailable) {
                    availableFonts.add(fontInfo);
                } else {
                    missingFonts.add(fontInfo);
                }

                // 显示详细信息
                System.out.printf("  字体: %s (%s) - 嵌入: %s, 可用: %s%n",
                        fontName,
                        familyName != null ? familyName : "N/A",
                        isEmbedded ? "是" : "否",
                        isAvailable ? "是" : "否");

                if (systemFontPath != null) {
                    System.out.println("    → 系统字体路径: " + systemFontPath);
                    if (!isChineseFont) {
                        System.out.println("    ⚠️  警告: 这是无效的字体替换，不能正确显示中文");
                    } else if (isGenericFont) {
                        System.out.println("    ⚠️  注意: 使用通用字体，可能会丢失原始字体样式");
                    }
                } else if (!isEmbedded) {
                    System.out.println("    ✗ 系统中未找到该字体");
                }
            }
        }

        return new FontCheckResult(allFonts, missingFonts, availableFonts);
    }

    /**
     * 主函数
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("使用方法: java org.loongoffice.ofd.font.FontCheckerDemo <ofd文件路径>");
            System.out.println();
            System.out.println("示例:");
            System.out.println("  java org.loongoffice.ofd.font.FontCheckerDemo /path/to/document.ofd");
            System.exit(1);
        }

        String ofdFilePath = args[0];

        try {
            System.out.println("正在分析 OFD 文档: " + ofdFilePath);
            System.out.println();

            FontCheckResult result = checkFonts(ofdFilePath);
            System.out.println();

            result.printReport();

            // 返回退出码：有缺失字体返回 1，否则返回 0
            System.exit(result.hasMissingFonts() ? 1 : 0);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
