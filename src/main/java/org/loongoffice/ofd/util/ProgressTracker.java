package org.loongoffice.ofd.util;

import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.XController;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.lang.XComponent;
import com.sun.star.task.XStatusIndicator;
import com.sun.star.task.XStatusIndicatorFactory;
import com.sun.star.uno.UnoRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进度跟踪器
 * 负责创建和管理LibreOffice进度指示器
 */
public class ProgressTracker {
    private static final Logger logger = LoggerFactory.getLogger(ProgressTracker.class);

    private final XStatusIndicator indicator;
    private boolean started = false;

    /**
     * 创建进度跟踪器
     *
     * @param descriptor 过滤器描述符
     * @param targetDocument 目标文档
     */
    public ProgressTracker(PropertyValue[] descriptor, XComponent targetDocument) {
        this.indicator = createIndicator(descriptor, targetDocument);
    }

    /**
     * 启动进度显示
     *
     * @param text 进度文本
     * @param range 进度范围（通常为100）
     */
    public void start(String text, int range) {
        if (indicator != null && !started) {
            indicator.start(text, range);
            started = true;
            logger.debug("Progress started: {}", text);
        }
    }

    /**
     * 更新进度文本
     *
     * @param text 新的进度文本
     */
    public void setText(String text) {
        if (indicator != null) {
            indicator.setText(text);
        }
    }

    /**
     * 设置进度值
     *
     * @param value 进度值
     */
    public void setValue(int value) {
        if (indicator != null) {
            indicator.setValue(value);
        }
    }

    /**
     * 完成进度显示
     */
    public void complete() {
        if (indicator != null && started) {
            indicator.end();
            started = false;
            logger.debug("Progress completed");
        }
    }

    /**
     * 检查进度指示器是否可用
     *
     * @return true如果可用
     */
    public boolean isAvailable() {
        return indicator != null;
    }

    /**
     * 创建进度指示器
     * 尝试从描述符或文档框架创建
     */
    private XStatusIndicator createIndicator(PropertyValue[] descriptor, XComponent targetDocument) {
        // Try to get status indicator from descriptor first
        if (descriptor != null) {
            for (PropertyValue prop : descriptor) {
                if (prop != null && "StatusIndicator".equals(prop.Name)) {
                    try {
                        XStatusIndicator ind = UnoRuntime.queryInterface(XStatusIndicator.class, prop.Value);
                        if (ind != null) {
                            logger.debug("Got StatusIndicator from descriptor");
                            return ind;
                        }
                    } catch (Throwable t) {
                        logger.trace("Failed to get StatusIndicator from descriptor", t);
                    }
                }
            }
        }

        // Try to get status indicator from target document's frame
        if (targetDocument != null) {
            try {
                XModel xModel = UnoRuntime.queryInterface(XModel.class, targetDocument);
                if (xModel != null) {
                    XController xController = xModel.getCurrentController();
                    if (xController != null) {
                        XFrame xFrame = xController.getFrame();
                        if (xFrame != null) {
                            XStatusIndicatorFactory factory = UnoRuntime.queryInterface(
                                    XStatusIndicatorFactory.class, xFrame);
                            if (factory != null) {
                                XStatusIndicator ind = factory.createStatusIndicator();
                                if (ind != null) {
                                    logger.debug("Created StatusIndicator from frame");
                                    return ind;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                logger.trace("Failed to create StatusIndicator from frame", t);
            }
        }

        logger.debug("Could not create StatusIndicator");
        return null;
    }
}
