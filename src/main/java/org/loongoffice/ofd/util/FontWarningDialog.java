package org.loongoffice.ofd.util;

import org.loongoffice.ofd.font.FontChecker.FontCheckResult;
import org.loongoffice.ofd.font.FontChecker.FontInfo;
import org.loongoffice.ofd.util.I18nMessages;

/**
 * Font warning dialog message builder
 *
 * Provides static methods to build font warning dialog titles and message content
 * Works with MessageBox to display dialogs
 *
 * @see MessageBox#showWarningConfirm MessageBox.showWarningConfirm()
 */
public class FontWarningDialog {

    /**
     * Build dialog caption
     *
     * @param result Font check result
     * @return Dialog caption: "Font Missing Warning" or "Font Replacement Notice"
     */
    public static String buildCaption(FontCheckResult result) {
        if (result.hasMissingFonts()) {
            return I18nMessages.getMessage("font.warning.caption.missing");
        }

        // Check if there are generic fonts or fallback mappings
        boolean hasGenericFont = result.getAvailableFonts().stream().anyMatch(FontInfo::isGenericFont);
        boolean hasFallbackMapped = result.getAvailableFonts().stream().anyMatch(FontInfo::isFallbackMapped);

        if (hasGenericFont || hasFallbackMapped) {
            return I18nMessages.getMessage("font.warning.caption.replacement");
        }

        return null;  // No warning, no need to show dialog
    }

    /**
     * Build warning message content
     *
     * Message includes:
     * 1. List of missing fonts (if any)
     * 2. List of fonts using replacements (including generic fonts and fallback mappings)
     * 3. Confirmation prompt for continuing to open
     *
     * @param result Font check result
     * @return Warning message content
     */
    public static String buildWarningMessage(FontCheckResult result) {
        StringBuilder sb = new StringBuilder();

        // Missing fonts (deduplicated by fontName)
        if (!result.getMissingFonts().isEmpty()) {
            var uniqueMissingFonts = result.getMissingFonts().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        FontInfo::getFontName,  // key: font name
                        font -> font,             // value: FontInfo object
                        (existing, replacement) -> existing  // Keep first if duplicate
                    ))
                    .values()
                    .stream()
                    .sorted(java.util.Comparator.comparing(FontInfo::getFontName))
                    .toList();

            sb.append(I18nMessages.getMessage(
                "font.warning.missing.prefix",
                uniqueMissingFonts.size()
            )).append("\n\n");

            for (FontInfo font : uniqueMissingFonts) {
                sb.append(I18nMessages.getMessage("font.warning.item.bullet"))
                  .append(font.getFontName());
                if (font.getFamilyName() != null) {
                    sb.append(" (").append(font.getFamilyName()).append(")");
                }
                sb.append("\n");
            }

            sb.append("\n")
              .append(I18nMessages.getMessage("font.warning.missing.suffix"))
              .append("\n\n");
        }

        // Handle all replacement fonts (generic fonts + fallback mappings)
        var replacedFonts = result.getAvailableFonts().stream()
                .filter(f -> f.isGenericFont() || f.isFallbackMapped())
                .collect(java.util.stream.Collectors.toMap(
                    FontInfo::getFontName,  // key: font name
                    font -> font,            // value: FontInfo object
                    (existing, replacement) -> existing  // Keep first if duplicate
                ))
                .values()
                .stream()
                .sorted(java.util.Comparator.comparing(FontInfo::getFontName))
                .toList();

        if (!replacedFonts.isEmpty()) {
            sb.append(I18nMessages.getMessage(
                "font.warning.replacement.prefix",
                replacedFonts.size()
            )).append("\n\n");

            for (FontInfo font : replacedFonts) {
                sb.append(I18nMessages.getMessage("font.warning.item.bullet"))
                  .append(font.getFontName());
                if (font.getFamilyName() != null) {
                    sb.append(" (").append(font.getFamilyName()).append(")");
                }
                sb.append("\n");
            }

            sb.append("\n")
              .append(I18nMessages.getMessage("font.warning.replacement.suffix"))
              .append("\n\n");
        }

        sb.append(I18nMessages.getMessage("font.warning.confirm"));

        return sb.toString();
    }
}
