package org.loongoffice.ofd.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 临时文件管理器
 * 负责创建和清理临时目录
 */
public class TempFileManager {
    private static final Logger logger = LoggerFactory.getLogger(TempFileManager.class);

    private final Path tempDir;
    private boolean deleted = false;

    /**
     * 创建临时文件管理器
     *
     * @throws IOException 如果创建临时目录失败
     */
    public TempFileManager() throws IOException {
        this.tempDir = Files.createTempDirectory("ofd-tmp-");
        logger.debug("Created temporary directory: {}", tempDir);
    }

    /**
     * 获取临时目录路径
     *
     * @return 临时目录Path对象
     */
    public Path getTempDir() {
        return tempDir;
    }

    /**
     * 清理临时目录
     * 删除目录及其所有内容
     */
    public void cleanup() {
        if (deleted) {
            return;
        }

        try {
            if (Files.exists(tempDir)) {
                logger.debug("Deleting temporary directory: {}", tempDir);
                Files.walk(tempDir)
                        .sorted((a, b) -> -a.compareTo(b))  // Delete files before directories
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                                logger.trace("Deleted: {}", p);
                            } catch (IOException e) {
                                logger.warn("Failed to delete: {}", p, e);
                            }
                        });
                logger.debug("Temporary directory deleted successfully: {}", tempDir);
            }
            deleted = true;
        } catch (IOException e) {
            logger.error("Failed to delete temporary directory: {}", tempDir, e);
        }
    }

    /**
     * 确保临时目录被清理
     * 可在finally块中调用
     */
    public void ensureCleanup() {
        if (!deleted) {
            cleanup();
        }
    }

    /**
     * 检查临时目录是否已被删除
     *
     * @return true如果已删除
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * 获取临时目录中的文件
     *
     * @param fileName 文件名
     * @return 文件的完整路径
     */
    public Path resolve(String fileName) {
        return tempDir.resolve(fileName);
    }
}
