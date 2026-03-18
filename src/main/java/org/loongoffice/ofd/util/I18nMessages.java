package org.loongoffice.ofd.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * 国际化消息管理器
 * 负责加载和格式化多语言消息
 */
public class I18nMessages {
    private static ResourceBundle messages;

    static {
        try {
            // Try to load messages based on system locale
            messages = ResourceBundle.getBundle("i18n.Messages", Locale.getDefault());
        } catch (Exception e) {
            try {
                // Fallback to default (English)
                messages = ResourceBundle.getBundle("i18n.Messages", Locale.ENGLISH);
            } catch (Throwable t2) {
                // If all fails, messages will be null and we'll use hardcoded strings
                System.err.println("Failed to load i18n resources: " + t2);
            }
        }
    }

    /**
     * 获取本地化消息
     *
     * @param key 消息键
     * @param params 格式化参数
     * @return 格式化后的消息
     */
    public static String getMessage(String key, Object... params) {
        if (messages == null) {
            // Fallback to key if resource bundle not loaded
            return key + (params.length > 0 ? " " + java.util.Arrays.toString(params) : "");
        }
        try {
            String pattern = messages.getString(key);
            if (params.length > 0) {
                return MessageFormat.format(pattern, params);
            }
            return pattern;
        } catch (Exception e) {
            // Fallback to key if message not found
            return key + (params.length > 0 ? " " + java.util.Arrays.toString(params) : "");
        }
    }

    /**
     * 检查消息资源是否已加载
     *
     * @return true if messages are available
     */
    public static boolean isAvailable() {
        return messages != null;
    }
}
