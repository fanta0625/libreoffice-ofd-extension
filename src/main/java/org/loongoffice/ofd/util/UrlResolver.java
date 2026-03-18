package org.loongoffice.ofd.util;

import com.sun.star.beans.PropertyValue;
import com.sun.star.uno.AnyConverter;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * URL解析器
 * 负责从LibreOffice过滤器描述符中提取和解析URL
 */
public class UrlResolver {

    /**
     * 从PropertyValue数组中查找URL
     *
     * @param props 过滤器描述符属性数组
     * @return 找到的URL字符串，如果未找到则返回空字符串
     */
    public static String extractUrl(PropertyValue[] props) {
        if (props == null) {
            return "";
        }

        for (PropertyValue p : props) {
            if (p == null) continue;

            if ("URL".equals(p.Name) && p.Value != null) {
                try {
                    String s = AnyConverter.toString(p.Value);
                    return normalizeUrl(s);
                } catch (Exception e) {
                    return p.Value.toString();
                }
            }
        }

        return "";
    }

    /**
     * 规范化URL为文件路径
     * 处理file://协议、URL编码、Windows路径等问题
     *
     * @param url 原始URL
     * @return 规范化后的文件路径
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        // Handle file:/// URIs
        if (url.startsWith("file://")) {
            try {
                String path = new URI(url).getPath();
                // On Windows, URI.getPath() returns /C:/path, need to remove leading slash
                if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                    path = path.substring(1);
                }
                return path;
            } catch (URISyntaxException e) {
                // Fallback: remove file:// prefix
                String path = url.substring(7);
                if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                    path = path.substring(1);
                }
                return path;
            }
        }

        // Try URL decoding for any percent-encoded characters
        try {
            return java.net.URLDecoder.decode(url, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return url;
        }
    }

    /**
     * 将URL字符串转换为Path对象
     *
     * @param url URL字符串
     * @return Path对象
     */
    public static Path urlToPath(String url) {
        String normalizedUrl = normalizeUrl(url);

        if (normalizedUrl.startsWith("file://")) {
            try {
                return Paths.get(new URI(normalizedUrl));
            } catch (URISyntaxException e) {
                // Fallback: remove file:// prefix
                String path = normalizedUrl.substring(7);
                if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                    path = path.substring(1);
                }
                return Paths.get(path);
            }
        } else {
            return Paths.get(normalizedUrl);
        }
    }

    /**
     * 验证URL是否为OFD文件
     *
     * @param url URL字符串
     * @return true如果URL以.ofd结尾
     */
    public static boolean isOfdFile(String url) {
        return url != null && url.toLowerCase().endsWith(".ofd");
    }
}
