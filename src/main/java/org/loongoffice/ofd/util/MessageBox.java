package org.loongoffice.ofd.util;

import com.sun.star.awt.MessageBoxType;
import com.sun.star.awt.XMessageBox;
import com.sun.star.awt.XMessageBoxFactory;
import com.sun.star.awt.XWindow;
import com.sun.star.awt.XWindowPeer;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.lang.XComponent;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息框工具类
 * 用于显示简单的信息对话框
 *
 * 基于 LibreOffice UNO API 和项目现有实现
 */
public class MessageBox {
    private static final Logger logger = LoggerFactory.getLogger(MessageBox.class);

    // 按钮类型常量 (根据 LibreOffice 官方文档)
    private static final short BUTTONS_OK = 1;
    private static final short BUTTONS_YES_NO = 3;  // 官方定义：YES/NO = 3，不是 2！

    public static final short RESULT_SECOND_BUTTON = 1;  // CANCEL 或 NO（第二个按钮）

    // 向后兼容的别名（已废弃，请使用上面的标准常量）
    @Deprecated
    public static final short RESULT_OK = 0;
    @Deprecated
    public static final short RESULT_YES = 0;
    @Deprecated
    public static final short RESULT_NO = 1;
    @Deprecated
    public static final short RESULT_CANCEL = 2;
    @Deprecated
    public static final short RESULT_RETRY = 2;

    /**
     * 显示信息消息框
     *
     * @param context UNO 组件上下文
     * @param targetDocument 目标文档
     * @param title 标题
     * @param message 消息内容
     * @return 用户点击的按钮 (OK = 0)
     */
    public static short showInfo(XComponentContext context, XComponent targetDocument,
                               String title, String message) {
        logger.debug("MessageBox.showInfo called - Title: {}", title);

        // 首先尝试使用目标文档
        XWindowPeer peer = getWindowPeerOfFrame(targetDocument);

        // 如果目标文档的 XWindowPeer 不可用，尝试使用 Desktop
        if (peer == null) {
            peer = getDesktopWindowPeer(context);
        }

        if (peer == null) {
            logger.warn("Unable to get any XWindowPeer");
            return RESULT_CANCEL;
        }

        logger.debug("XWindowPeer obtained, showing message box");
        return showWithPeer(context, peer, title, message, MessageBoxType.INFOBOX, BUTTONS_OK);
    }

    /**
     * 使用 Desktop 获取 XWindowPeer
     */
    private static XWindowPeer getDesktopWindowPeer(XComponentContext context) {
        try {
            Object desktop = context.getServiceManager().createInstanceWithContext(
                    "com.sun.star.frame.Desktop", context);

            com.sun.star.frame.XDesktop desktop2 = UnoRuntime.queryInterface(
                    com.sun.star.frame.XDesktop.class, desktop);

            if (desktop2 != null) {
                // 获取当前活动框架
                XFrame frame = desktop2.getCurrentFrame();
                if (frame != null) {
                    // 方法1: 尝试 getComponentWindow()
                    XWindow xWindow = frame.getComponentWindow();
                    if (xWindow != null) {
                        XWindowPeer peer = UnoRuntime.queryInterface(XWindowPeer.class, xWindow);
                        if (peer != null) {
                            logger.debug("Desktop XWindowPeer obtained (from component window)");
                            return peer;
                        }
                    }

                    // 方法2: 尝试 getContainerWindow()
                    xWindow = frame.getContainerWindow();
                    if (xWindow != null) {
                        XWindowPeer peer = UnoRuntime.queryInterface(XWindowPeer.class, xWindow);
                        if (peer != null) {
                            logger.debug("Desktop XWindowPeer obtained (from container window)");
                            return peer;
                        }
                    }
                }

                // 方法3: 直接从 Desktop 获取容器窗口
                try {
                    XFrame desktopFrame = UnoRuntime.queryInterface(XFrame.class, desktop);
                    if (desktopFrame != null) {
                        XWindow xWindow = desktopFrame.getContainerWindow();
                        if (xWindow != null) {
                            XWindowPeer peer = UnoRuntime.queryInterface(XWindowPeer.class, xWindow);
                            if (peer != null) {
                                logger.debug("Desktop XWindowPeer obtained (from desktop container)");
                                return peer;
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.trace("Failed to get Desktop container window", t);
                }
            }
        } catch (Throwable t) {
            logger.warn("Failed to get Desktop XWindowPeer", t);
        }
        return null;
    }

    /**
     * 使用指定的 XWindowPeer 显示消息框
     */
    private static short showWithPeer(XComponentContext context, XWindowPeer peer,
                                     String title, String message,
                                     MessageBoxType boxType, short buttons) {
        XComponent xComponent = null;
        try {
            Object oToolkit = context.getServiceManager().createInstanceWithContext(
                    "com.sun.star.awt.Toolkit", context);

            XMessageBoxFactory xMessageBoxFactory = UnoRuntime.queryInterface(
                    XMessageBoxFactory.class, oToolkit);

            if (xMessageBoxFactory == null) {
                logger.warn("Unable to create XMessageBoxFactory");
                return RESULT_CANCEL;
            }

            XMessageBox xMessageBox = xMessageBoxFactory.createMessageBox(
                    peer,
                    boxType,
                    buttons,
                    title,
                    message);

            if (xMessageBox == null) {
                logger.warn("createMessageBox returned null");
                return RESULT_CANCEL;
            }

            logger.debug("MessageBox created, calling execute()...");
            xComponent = UnoRuntime.queryInterface(XComponent.class, xMessageBox);
            if (xMessageBox != null) {
                short result = xMessageBox.execute();
                logger.debug("MessageBox.execute() returned: {}", result);
                return result;
            }

        } catch (Throwable e) {
            logger.error("Failed to show message box", e);
        } finally {
            if (xComponent != null) {
                try {
                    xComponent.dispose();
                } catch (Throwable t) {
                    logger.trace("Failed to dispose MessageBox component", t);
                }
            }
        }

        return RESULT_CANCEL;
    }

    /**
     * 显示警告消息框
     *
     * @param context UNO 组件上下文
     * @param targetDocument 目标文档
     * @param title 标题
     * @param message 消息内容
     * @return 用户点击的按钮 (OK = 0)
     */
    public static short showWarning(XComponentContext context, XComponent targetDocument,
                                  String title, String message) {
        // 首先尝试使用目标文档
        XWindowPeer peer = getWindowPeerOfFrame(targetDocument);

        // 如果目标文档的 XWindowPeer 不可用，尝试使用 Desktop
        if (peer == null) {
            logger.debug("Target document XWindowPeer unavailable, trying Desktop");
            peer = getDesktopWindowPeer(context);
        }

        if (peer == null) {
            logger.warn("Unable to get any XWindowPeer");
            return RESULT_CANCEL;
        }

        return showWithPeer(context, peer, title, message, MessageBoxType.WARNINGBOX, BUTTONS_OK);
    }

    /**
     * 显示错误消息框
     *
     * @param context UNO 组件上下文
     * @param targetDocument 目标文档
     * @param title 标题
     * @param message 消息内容
     * @return 用户点击的按钮 (OK = 0)
     */
    public static short showError(XComponentContext context, XComponent targetDocument,
                                String title, String message) {
        // 首先尝试使用目标文档
        XWindowPeer peer = getWindowPeerOfFrame(targetDocument);

        // 如果目标文档的 XWindowPeer 不可用，尝试使用 Desktop
        if (peer == null) {
            logger.debug("Target document XWindowPeer unavailable, trying Desktop");
            peer = getDesktopWindowPeer(context);
        }

        if (peer == null) {
            logger.warn("Unable to get any XWindowPeer");
            return RESULT_CANCEL;
        }

        return showWithPeer(context, peer, title, message, MessageBoxType.ERRORBOX, BUTTONS_OK);
    }

    /**
     * 显示确认消息框
     *
     * @param context UNO 组件上下文
     * @param targetDocument 目标文档
     * @param title 标题
     * @param message 消息内容
     * @return 用户点击的按钮 (YES = 0, NO = 1)
     */
    public static short showConfirm(XComponentContext context, XComponent targetDocument,
                                  String title, String message) {
        logger.debug("MessageBox.showConfirm called - Title: {}, Message length: {}", title, message.length());

        // 首先尝试使用目标文档
        XWindowPeer peer = getWindowPeerOfFrame(targetDocument);

        // 如果目标文档的 XWindowPeer 不可用，尝试使用 Desktop
        if (peer == null) {
            logger.debug("Target document XWindowPeer unavailable, trying Desktop");
            peer = getDesktopWindowPeer(context);
        }

        if (peer == null) {
            logger.warn("Unable to get any XWindowPeer, returning RESULT_CANCEL");
            return RESULT_CANCEL;
        }

        logger.debug("XWindowPeer obtained successfully, showing confirmation dialog");
        short result = showWithPeer(context, peer, title, message, MessageBoxType.QUERYBOX, BUTTONS_YES_NO);
        logger.debug("Dialog closed, return value: {}", result);
        return result;
    }

    /**
     * 显示警告确认消息框（WARNINGBOX + YES/NO 按钮）
     * 用于需要用户确认的警告场景
     *
     * @param context UNO 组件上下文
     * @param targetDocument 目标文档
     * @param title 标题
     * @param message 消息内容
     * @return 用户点击的按钮 (YES = 0, NO = 1)
     */
    public static short showWarningConfirm(XComponentContext context, XComponent targetDocument,
                                         String title, String message) {
        logger.debug("MessageBox.showWarningConfirm called - Title: {}, Message length: {}", title, message.length());

        // 首先尝试使用目标文档
        XWindowPeer peer = getWindowPeerOfFrame(targetDocument);

        // 如果目标文档的 XWindowPeer 不可用，尝试使用 Desktop
        if (peer == null) {
            logger.debug("Target document XWindowPeer unavailable, trying Desktop");
            peer = getDesktopWindowPeer(context);
        }

        if (peer == null) {
            logger.warn("Unable to get any XWindowPeer, returning RESULT_SECOND_BUTTON (NO)");
            return RESULT_SECOND_BUTTON;
        }

        logger.debug("XWindowPeer obtained successfully, showing warning confirmation dialog (YES/NO)");
        short result = showWithPeer(context, peer, title, message, MessageBoxType.WARNINGBOX, BUTTONS_YES_NO);
        logger.debug("Dialog closed, return value: {} (0=YES/First button, 1=NO/Second button)", result);
        return result;
    }

    /**
     * 从文档组件获取 XWindowPeer
     *
     * @param document 文档组件
     * @return XWindowPeer，如果无法获取则返回 null
     */
    private static XWindowPeer getWindowPeerOfFrame(XComponent document) {
        if (document == null) {
            logger.warn("document is null");
            return null;
        }

        try {
            XModel xModel = UnoRuntime.queryInterface(XModel.class, document);
            if (xModel == null) {
                logger.warn("XModel is null");
                return null;
            }

            logger.debug("XModel obtained: {}", xModel);

            if (xModel.getCurrentController() == null) {
                logger.warn("XController is null");
                return null;
            }

            XFrame xFrame = xModel.getCurrentController().getFrame();
            if (xFrame == null) {
                logger.warn("XFrame is null");
                return null;
            }

            logger.debug("XFrame obtained: {}", xFrame);

            XWindow xWindow = xFrame.getContainerWindow();
            if (xWindow == null) {
                logger.warn("XWindow is null");
                return null;
            }

            logger.debug("XWindow obtained: {}", xWindow);

            XWindowPeer xWindowPeer = UnoRuntime.queryInterface(XWindowPeer.class, xWindow);
            if (xWindowPeer == null) {
                logger.warn("XWindowPeer is null after query");
            } else {
                logger.debug("XWindowPeer obtained successfully");
            }

            return xWindowPeer;
        } catch (Throwable t) {
            logger.warn("Failed to get XWindowPeer", t);
            return null;
        }
    }
}
