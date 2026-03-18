package org.loongoffice.ofd.document;

import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.XPropertySet;
import com.sun.star.drawing.XDrawPage;
import com.sun.star.drawing.XDrawPagesSupplier;
import com.sun.star.drawing.XShape;
import com.sun.star.drawing.XShapes;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.loongoffice.ofd.config.ReadOnlyConfig;

/**
 * 文档处理器
 * 负责创建和配置LibreOffice Draw文档
 */
public class DocumentHandler {
    private static final Logger logger = LoggerFactory.getLogger(DocumentHandler.class);

    private final XComponentContext context;
    private XComponent targetDocument;

    /**
     * 创建文档处理器
     *
     * @param context UNO组件上下文
     */
    public DocumentHandler(XComponentContext context) {
        this.context = context;
    }

    /**
     * 设置目标文档
     *
     * @param document 目标文档
     */
    public void setTargetDocument(XComponent document) {
        this.targetDocument = document;
    }

    /**
     * 获取目标文档
     *
     * @return 目标文档，如果未创建则返回null
     */
    public XComponent getTargetDocument() {
        return targetDocument;
    }

    /**
     * 确保目标文档存在
     * 如果不存在则创建新的Draw文档
     *
     * @return true如果文档存在或创建成功
     */
    public boolean ensureTargetDocument() {
        if (targetDocument != null) {
            // Apply read-only properties to existing document
            applyReadOnlyProperties(targetDocument);
            return true;
        }

        try {
            XMultiComponentFactory mcf = context.getServiceManager();
            Object desktop = mcf.createInstanceWithContext("com.sun.star.frame.Desktop", context);
            XComponentLoader loader = UnoRuntime.queryInterface(XComponentLoader.class, desktop);

            if (loader != null) {
                // Set read-only protection properties
                PropertyValue[] loadProps = ReadOnlyConfig.createReadOnlyProperties();

                targetDocument = loader.loadComponentFromURL(
                        "private:factory/sdraw", "_blank", 0, loadProps);

                logger.debug("Created new Draw document with read-only protection");
                return targetDocument != null;
            }
        } catch (Exception e) {
            logger.error("Failed to create target document", e);
        }

        return false;
    }

    /**
     * 应用只读属性到文档
     *
     * @param doc 目标文档
     */
    public void applyReadOnlyProperties(XComponent doc) {
        ReadOnlyConfig.applyReadOnlyProperties(doc, message -> logger.debug(message));
    }

    /**
     * 获取Draw文档的页面集合
     *
     * @return DrawPagesSupplier，如果不是Draw文档则返回null
     */
    public XDrawPagesSupplier getDrawPagesSupplier() {
        if (targetDocument == null) {
            return null;
        }
        return UnoRuntime.queryInterface(XDrawPagesSupplier.class, targetDocument);
    }

    /**
     * 获取指定索引的页面
     *
     * @param index 页面索引
     * @return DrawPage，如果不存在则返回null
     */
    public XDrawPage getDrawPage(int index) {
        XDrawPagesSupplier supplier = getDrawPagesSupplier();
        if (supplier == null) {
            return null;
        }

        try {
            return UnoRuntime.queryInterface(
                    XDrawPage.class,
                    supplier.getDrawPages().getByIndex(index));
        } catch (Exception e) {
            logger.error("Failed to get draw page at index " + index, e);
            return null;
        }
    }

    /**
     * 在指定页面插入图形对象
     *
     * @param pageIndex 页面索引
     * @param shape 图形对象
     * @return true如果成功插入
     */
    public boolean insertShape(int pageIndex, XShape shape) {
        XDrawPage page = getDrawPage(pageIndex);
        if (page == null) {
            return false;
        }

        XShapes shapes = UnoRuntime.queryInterface(XShapes.class, page);
        if (shapes == null) {
            return false;
        }

        shapes.add(shape);
        return true;
    }

    /**
     * 设置页面大小
     *
     * @param pageIndex 页面索引
     * @param width 宽度（LibreOffice单位）
     * @param height 高度（LibreOffice单位）
     * @return true如果成功设置
     */
    public boolean setPageSize(int pageIndex, int width, int height) {
        XDrawPage page = getDrawPage(pageIndex);
        if (page == null) {
            return false;
        }

        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, page);
        if (props == null) {
            return false;
        }

        try {
            props.setPropertyValue("Width", width);
            props.setPropertyValue("Height", height);
            logger.debug("Set page {} size: {} × {}", pageIndex, width, height);
            return true;
        } catch (Exception e) {
            logger.error("Failed to set page size", e);
            return false;
        }
    }

    /**
     * 获取页面数量
     *
     * @return 页面数量，如果不是Draw文档则返回0
     */
    public int getPageCount() {
        XDrawPagesSupplier supplier = getDrawPagesSupplier();
        if (supplier == null) {
            return 0;
        }

        try {
            return supplier.getDrawPages().getCount();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 插入新页面
     *
     * @param index 插入位置索引
     * @return 新创建的页面，如果失败则返回null
     */
    public XDrawPage insertNewPage(int index) {
        XDrawPagesSupplier supplier = getDrawPagesSupplier();
        if (supplier == null) {
            return null;
        }

        try {
            return UnoRuntime.queryInterface(
                    XDrawPage.class,
                    supplier.getDrawPages().insertNewByIndex(index));
        } catch (Exception e) {
            logger.error("Failed to insert new page at index " + index, e);
            return null;
        }
    }
}
