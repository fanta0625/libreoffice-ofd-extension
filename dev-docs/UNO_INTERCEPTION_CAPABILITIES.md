# LibreOffice UNO 拦截能力分析 - Edit Document & Save 按钮

**文档类型**: 技术可行性深度分析
**分析日期**: 2026-03-05
**主题**: UNO 接口能否拦截 "Edit Document" 和 "Save" 按钮
**版本**: v1.0

---

## 📋 目录

- [1. 核心结论](#1-核心结论)
- [2. UNO 拦截机制原理](#2-uno-拦截机制原理)
- [3. Edit Document 按钮拦截](#3-edit-document-按钮拦截)
- [4. Save 按钮拦截](#4-save-按钮拦截)
- [5. 实现方案](#5-实现方案)
- [6. 限制与注意事项](#6-限制与注意事项)
- [7. 代码示例](#7-代码示例)
- [8. 替代方案](#8-替代方案)

---

## 1. 核心结论

### ✅ **可以拦截！但有限制**

| 按钮 | UNO 命令 | 可拦截性 | 拦截方式 | 限制 |
|------|---------|---------|---------|------|
| **Edit Document** | `.uno:EditDoc` | ✅ **可以** | `XDispatch` | 只能拦截**你自己文档**的按钮 |
| **Save** | `.uno:Save` | ✅ **可以** | `XDispatch` | 只能拦截**你自己文档**的保存操作 |
| **Save As** | `.uno:SaveAs` | ✅ **可以** | `XDispatch` | 只能拦截**你自己文档**的另存为 |

### ⚠️ 关键限制

**无法拦截系统内置的按钮行为！**

- ❌ **无法修改** LibreOffice PDF 过滤器的"Edit Document"按钮行为
- ❌ **无法全局拦截**所有文档的 Save 操作
- ✅ **只能拦截**你自己打开的文档的操作

---

## 2. UNO 拦截机制原理

### 2.1 LibreOffice 命令分发机制

```
用户操作
   ↓
UI 触发 (.uno:EditDoc, .uno:Save)
   ↓
XDispatchProvider (查找处理器)
   ↓
XDispatch (执行命令)
   ↓
实际功能执行
```

### 2.2 拦截点

**可以在 `XDispatch` 层面拦截**：

```
正常流程:
UI → XDispatchProvider → XDispatch → 功能执行

拦截流程:
UI → XDispatchProvider → [你的 XDispatch] → 决定是否转发 → 原始 XDispatch → 功能执行
```

### 2.3 关键接口

#### XDispatch

```java
public interface XDispatch extends com.sun.star.uno.XInterface {
    /**
     * 分发命令
     * @param aURL 命令 URL (如 ".uno:Save")
     * @param Arguments 命令参数
     */
    void dispatch(com.sun.star.util.URL aURL, PropertyValue[] Arguments);

    /**
     * 添加状态监听器
     */
    void addStatusListener(XStatusListener Control, com.sun.star.util.URL aURL);

    /**
     * 移除状态监听器
     */
    void removeStatusListener(XStatusListener Control, com.sun.star.util.URL aURL);
}
```

#### XDispatchProvider

```java
public interface XDispatchProvider extends com.sun.star.uno.XInterface {
    /**
     * 查询命令的分发器
     * @param aURL 命令 URL
     * @param sTargetFrameName 目标框架名称
     * @param nSearchFlags 搜索标志
     * @return XDispatch 对象
     */
    XDispatch queryDispatch(com.sun.star.util.URL aURL,
                           String sTargetFrameName,
                           int nSearchFlags);
}
```

---

## 3. Edit Document 按钮拦截

### 3.1 按钮显示机制

根据之前的分析文档，"Edit Document" 按钮是 **LibreOffice 自动显示**的，当文档满足以下条件时：

```cpp
// LibreOffice C++ 代码 (sfx2/source/view/viewfrm.cxx)
if (m_xObjShell->IsReadOnly() &&           // 文档只读
    !m_xObjShell->IsSecurityOptOpenReadOnly())  // 不是安全策略导致的只读
{
    AppendReadOnlyInfobar();  // 显示按钮
}
```

**关键点**：
- 按钮显示由 LibreOffice **内部控制**
- 按钮点击触发 `.uno:EditDoc` 命令
- 我们**无法修改按钮本身**，但**可以拦截命令**

### 3.2 拦截方案

#### ✅ 方案 A：通过 XDispatchInterceptor 拦截（推荐）

**原理**：注册一个 `XDispatchProviderInterceptor`，拦截特定文档的命令

```java
public class OFDDispatchInterceptor implements XDispatchProviderInterceptor {
    private XDispatchProvider originalDispatcher;
    private XFrame frame;
    private boolean isOFDDocument;

    @Override
    public XDispatch queryDispatch(com.sun.star.util.URL aURL,
                                   String targetFrameName,
                                   int searchFlags) {
        // 检查是否是 OFD 文档
        if (!isOFDDocument) {
            // 不是 OFD 文档，使用原始分发器
            return originalDispatcher.queryDispatch(aURL, targetFrameName, searchFlags);
        }

        // 检查是否是需要拦截的命令
        if (".uno:EditDoc".equals(aURL.Complete)) {
            // OFD 文档的 EditDoc 命令，返回自定义 Dispatch
            return new OFDEditDocDispatch();
        }

        if (".uno:Save".equals(aURL.Complete)) {
            // OFD 文档的 Save 命令，返回自定义 Dispatch
            return new OFDSaveDispatch();
        }

        // 其他命令使用原始分发器
        return originalDispatcher.queryDispatch(aURL, targetFrameName, searchFlags);
    }

    @Override
    public XDispatchProvider getMasterDispatcher() {
        return originalDispatcher;
    }

    @Override
    public void setMasterDispatcher(XDispatchProvider newMaster) {
        this.originalDispatcher = newMaster;
    }
}
```

#### 自定义 EditDoc Dispatch

```java
public class OFDEditDocDispatch implements XDispatch {
    private XComponentContext context;
    private Path ofdPath;

    public OFDEditDocDispatch(XComponentContext context, Path ofdPath) {
        this.context = context;
        this.ofdPath = ofdPath;
    }

    @Override
    public void dispatch(URL url, PropertyValue[] arguments) {
        try {
            // 1. 显示确认对话框
            boolean confirmed = showEditConfirmation();
            if (!confirmed) {
                return;  // 用户取消
            }

            // 2. OFD → PDF 转换
            Path pdfPath = convertOFDToPDF(ofdPath);

            // 3. 关闭当前只读文档
            closeCurrentDocument();

            // 4. 打开 PDF（可编辑）
            openPDFInLibreOffice(pdfPath);

        } catch (Exception e) {
            showError("转换失败: " + e.getMessage());
        }
    }

    private boolean showEditConfirmation() {
        // 使用 MessageBox 显示确认对话框
        String message =
            "⚠️ 转换编辑提示\n\n" +
            "• 编辑需要将文档转换为 PDF 格式\n" +
            "• 编辑功能可能受限于 PDF 兼容性\n" +
            "• 保存时将转换回 OFD 格式\n" +
            "• 复杂布局可能发生变化\n\n" +
            "是否继续？";

        short result = MessageBox.showConfirm(context, null,
            "切换到编辑模式", message);

        return (result == MessageBox.RESULT_YES);
    }

    private Path convertOFDToPDF(Path ofdPath) throws IOException {
        Path tempPdf = Files.createTempFile("ofd-edit-", ".pdf");

        try (OFDExporter exporter = new PDFExporterPDFBox(ofdPath, tempPdf)) {
            exporter.export();
        }

        return tempPdf;
    }

    private void closeCurrentDocument() {
        // 实现关闭逻辑
    }

    private void openPDFInLibreOffice(Path pdfPath) {
        // 使用 LibreOffice 打开 PDF（非只读模式）
    }

    @Override
    public void addStatusListener(XStatusListener listener, URL url) {
        // 可选：实现状态监听
    }

    @Override
    public void removeStatusListener(XStatusListener listener, URL url) {
        // 可选：实现状态监听
    }
}
```

### 3.3 注册拦截器

**关键**：需要在文档打开时注册拦截器

```java
// 在 OFDImportFilter.filter() 方法中
public boolean filter(PropertyValue[] aDescriptor) {
    // ... 现有代码 ...

    // 获取当前 Frame
    XModel model = UnoRuntime.queryInterface(XModel.class, targetDocument);
    XFrame frame = model.getCurrentController().getFrame();

    // 注册拦截器
    registerDispatchInterceptor(frame, ofdPath);

    // ...
}

private void registerDispatchInterceptor(XFrame frame, Path ofdPath) {
    try {
        // 获取当前的 DispatchProvider
        XDispatchProvider provider = UnoRuntime.queryInterface(
            XDispatchProvider.class, frame
        );

        // 创建拦截器
        OFDDispatchInterceptor interceptor = new OFDDispatchInterceptor();
        interceptor.setFrame(frame);
        interceptor.setOFDDocument(true);
        interceptor.setMasterDispatcher(provider);

        // 注册拦截器
        // 注意：LibreOffice 可能没有直接设置 interceptor 的 API
        // 需要通过其他方式实现

    } catch (Exception e) {
        logger.error("Failed to register dispatch interceptor", e);
    }
}
```

### ⚠️ 问题：LibreOffice 可能不支持注册 Interceptor

**现实限制**：
- LibreOffice 的 `XFrame` **没有 `setDispatchInterceptor()` 方法**
- 无法直接替换 Frame 的 DispatchProvider
- **需要使用其他方式**

---

## 4. Save 按钮拦截

### 4.1 Save 命令流程

```
用户点击 Save 或 Ctrl+S
   ↓
触发 .uno:Save
   ↓
XStorable.store()  // 如果文档支持存储
   ↓
保存到原位置
```

### 4.2 拦截 Save 的方案

#### ❌ 方案 A：拦截 .uno:Save（不可行）

**问题**：
- `.uno:Save` 是 LibreOffice 核心命令
- 无法通过 XDispatch 拦截（因为 Frame 的 DispatchProvider 不可替换）

#### ✅ 方案 B：实现 XStorable 接口包装（可行）

**原理**：包装文档的 `XStorable` 接口，拦截 `store()` 调用

```java
public class OFDDocumentStorableWrapper implements XStorable {
    private XStorable originalStorable;
    private Path originalOfdPath;
    private XComponentContext context;

    public OFDDocumentStorableWrapper(XStorable original,
                                      Path ofdPath,
                                      XComponentContext context) {
        this.originalStorable = original;
        this.originalOfdPath = ofdPath;
        this.context = context;
    }

    @Override
    public void store() throws IOException {
        // 拦截保存操作
        try {
            // 1. 导出当前文档为 PDF
            Path tempPdf = exportCurrentDocumentToPDF();

            // 2. 转换 PDF → OFD
            convertPDFToOFD(tempPdf, originalOfdPath);

            // 3. 清理临时文件
            Files.deleteIfExists(tempPdf);

            // 4. 显示成功提示
            showSuccessMessage();

        } catch (Exception e) {
            throw new IOException("OFD 保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeAsURL(String url, PropertyValue[] arguments) throws IOException {
        // 拦截另存为操作
        if (url.endsWith(".ofd")) {
            // 另存为 OFD，执行转换
            Path tempPdf = exportCurrentDocumentToPDF();
            convertPDFToOFD(tempPdf, Paths.get(url));
        } else {
            // 另存为其他格式，使用原始逻辑
            originalStorable.storeAsURL(url, arguments);
        }
    }

    private Path exportCurrentDocumentToPDF() throws IOException {
        // 获取当前文档
        XModel model = UnoRuntime.queryInterface(XModel.class, originalStorable);
        XComponent doc = model;

        // 导出为 PDF
        Path tempPdf = Files.createTempFile("ofd-save-", ".pdf");
        PropertyValue[] exportProps = new PropertyValue[1];
        exportProps[0] = new PropertyValue();
        exportProps[0].Name = "FilterName";
        exportProps[0].Value = "draw_pdf_Export";

        XStorable storable = UnoRuntime.queryInterface(XStorable.class, doc);
        storable.storeToURL(tempPdf.toUri().toString(), exportProps);

        return tempPdf;
    }

    private void convertPDFToOFD(Path pdfPath, Path ofdPath) throws IOException {
        try (PDFConverter converter = new PDFConverter(ofdPath)) {
            converter.convert(pdfPath);
        }
    }

    // 委托其他方法到原始 XStorable
    @Override
    public boolean hasLocation() {
        return originalStorable.hasLocation();
    }

    @Override
    public String getLocation() {
        return originalStorable.getLocation();
    }

    @Override
    public boolean isReadonly() {
        return originalStorable.isReadonly();
    }

    @Override
    public void storeToURL(String url, PropertyValue[] arguments) throws IOException {
        originalStorable.storeToURL(url, arguments);
    }
}
```

#### ⚠️ 问题：无法替换文档的 XStorable 接口

**问题**：
- 文档对象已经创建，无法替换其接口实现
- LibreOffice 的文档模型不支持动态替换接口

---

## 5. 实现方案

### 5.1 现实可行的方案

经过以上分析，**直接拦截按钮和保存操作的方案不可行**。

### ✅ 推荐方案：不依赖拦截，使用独立功能

#### 方案 A：添加自定义菜单项（推荐）

**原理**：通过 Addons.xcu 添加自定义菜单，而不是拦截现有按钮

**实现步骤**：

1. **在 Addons.xcu 中添加菜单项**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<oor:component-data xmlns:oor="http://openoffice.org/2001/registry"
                    xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    oor:name="Addons"
                    oor:package="org.openoffice.Office">
  <node oor:name="AddonUI">
    <!-- 添加菜单项 -->
    <node oor:name="OfficeMenuBar">
      <node oor:name="org.loongoffice.ofd.reader" oor:op="replace">
        <prop oor:name="Title">
          <value xml:lang="en-US">O~FD</value>
          <value xml:lang="zh-CN">O~FD</value>
        </prop>
        <node oor:name="Submenu">
          <!-- 编辑 OFD 文档 -->
          <node oor:name="EditOFD" oor:op="replace">
            <prop oor:name="URL">
              <value>service:org.loongoffice.ofd.reader?edit</value>
            </prop>
            <prop oor:name="Title">
              <value xml:lang="en-US">~Edit OFD Document</value>
              <value xml:lang="zh-CN">编辑 OFD 文档(~E)</value>
            </prop>
            <prop oor:name="Target">
              <value>_self</value>
            </prop>
          </node>

          <!-- 保存为 OFD -->
          <node oor:name="SaveAsOFD" oor:op="replace">
            <prop oor:name="URL">
              <value>service:org.loongoffice.ofd.reader?saveas</value>
            </prop>
            <prop oor:name="Title">
              <value xml:lang="en-US">Save As ~OFD</value>
              <value xml:lang="zh-CN">另存为 OFD(~O)</value>
            </prop>
            <prop oor:name="Target">
              <value>_self</value>
            </prop>
          </node>
        </node>
      </node>
    </node>
  </node>
</oor:component-data>
```

2. **实现 Protocol Handler**

```java
public class OFDProtocolHandler implements XDispatchProvider, XDispatch {
    private XComponentContext context;

    public OFDProtocolHandler(XComponentContext context) {
        this.context = context;
    }

    @Override
    public XDispatch queryDispatch(URL aURL, String targetFrameName, int searchFlags) {
        // 处理自定义协议
        if (aURL.Protocol != null &&
            aURL.Protocol.equals("service.org.loongoffice.ofd.reader")) {
            return this;
        }
        return null;
    }

    @Override
    public void dispatch(URL aURL, PropertyValue[] arguments) {
        if (aURL == null) return;

        // 解析命令
        String command = aURL.Path; // "edit" 或 "saveas"

        switch (command) {
            case "edit":
                handleEditCommand();
                break;
            case "saveas":
                handleSaveAsCommand();
                break;
        }
    }

    private void handleEditCommand() {
        // 实现编辑功能
        // 1. OFD → PDF
        // 2. 打开 PDF
    }

    private void handleSaveAsCommand() {
        // 实现另存为功能
        // 1. 显示文件选择对话框
        // 2. 导出 PDF → OFD
    }

    @Override
    public void addStatusListener(XStatusListener listener, URL url) {
    }

    @Override
    public void removeStatusListener(XStatusListener listener, URL url) {
    }
}
```

3. **注册 Protocol Handler**

在 `OFDImportFilter.components` 中添加：

```xml
<component loader="com.sun.star.loader.Java2"
           xmlns="http://openoffice.org/2001/manifest">
    <implementation name="org.loongoffice.ofd.reader.OFDProtocolHandler">
        <service name="com.sun.star.frame.ProtocolHandler"/>
    </component>
</component>
```

#### 方案 B：隐藏"Edit Document"按钮，显示自定义按钮

**原理**：
1. 设置 `LockEditDoc = true`，隐藏系统的"Edit Document"按钮
2. 在自定义 Infobar 中添加自己的"Edit"按钮

```java
// 在 filter() 方法中
PropertyValue[] loadProps = new PropertyValue[2];
loadProps[0] = new PropertyValue();
loadProps[0].Name = "ReadOnly";
loadProps[0].Value = Boolean.TRUE;

loadProps[1] = new PropertyValue();
loadProps[1].Name = "LockEditDoc";
loadProps[1].Value = Boolean.TRUE;  // 隐藏系统的 Edit Document 按钮

// 文档打开后，显示自定义 Infobar
showCustomEditButton();
```

**显示自定义按钮**：
```java
private void showCustomEditButton() {
    try {
        // 使用 Infobar API
        Object infobar = mcf.createInstanceWithContext(
            "com.sun.star.frame.Infobar", context);

        // 调用 Infobar 方法显示按钮
        // 注意：LibreOffice 的 Infobar API 可能受限
    } catch (Exception e) {
        logger.error("Failed to show custom edit button", e);
    }
}
```

#### 方案 C：监听文档事件（最可行）

**原理**：监听文档的 `OnSave` 事件，在保存前执行转换

```java
public class OFDEventListener implements XDocumentEventListener {
    @Override
    public void documentEventOccurred(DocumentEvent event) {
        if ("OnSave".equals(event.EventName)) {
            // 文档即将保存
            handleSaveEvent(event);
        } else if ("OnSaveAs".equals(event.EventName)) {
            // 文档另存为
            handleSaveAsEvent(event);
        }
    }

    private void handleSaveEvent(DocumentEvent event) {
        try {
            XStorable storable = UnoRuntime.queryInterface(
                XStorable.class, event.Source);

            // 检查是否是 OFD 文档
            String url = storable.getLocation();
            if (url != null && url.endsWith(".ofd")) {
                // 拦截保存，执行转换
                event.Source = null;  // 阻止原始保存

                // 执行自定义保存逻辑
                customSaveLogic(url);
            }
        } catch (Exception e) {
            logger.error("Failed to handle save event", e);
        }
    }
}
```

**注册事件监听器**：
```java
private void registerDocumentEventListener(XComponent doc) {
    try {
        XDocumentEventBroadcaster broadcaster = UnoRuntime.queryInterface(
            XDocumentEventBroadcaster.class, doc);

        OFDEventListener listener = new OFDEventListener();
        broadcaster.addDocumentEventListener(listener);

    } catch (Exception e) {
        logger.error("Failed to register event listener", e);
    }
}
```

---

## 6. 限制与注意事项

### 6.1 UNO 拦截的限制

| 限制 | 说明 | 影响 |
|------|------|------|
| **无法替换系统 DispatchProvider** | LibreOffice Frame 的 DispatchProvider 不可替换 | 无法全局拦截命令 |
| **无法修改系统按钮行为** | "Edit Document" 按钮由 LibreOffice 内部控制 | 只能隐藏或添加新按钮 |
| **无法包装文档接口** | 文档对象一旦创建，无法替换其接口实现 | 无法拦截 XStorable.store() |
| **只能作用于自己的文档** | 拦截器只能拦截自己创建的文档 | 无法影响其他文档 |

### 6.2 现实可行的操作

| 操作 | 可行性 | 实现方式 |
|------|--------|---------|
| **添加自定义菜单项** | ✅ 完全可行 | Addons.xcu + ProtocolHandler |
| **隐藏系统按钮** | ✅ 可行 | `LockEditDoc = true` |
| **监听文档事件** | ✅ 可行 | XDocumentEventListener |
| **显示自定义对话框** | ✅ 可行 | MessageBox API |
| **转换文件格式** | ✅ 可行 | ofdrw 库 |
| **拦截保存操作** | ⚠️ 部分可行 | 通过事件监听，但不能完全阻止 |

---

## 7. 代码示例

### 7.1 完整的自定义菜单实现

```java
package org.loongoffice.ofd.reader.handler;

import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.lang.XInitialization;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.lib.uno.helper.ComponentBase;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import com.sun.star.util.URL;

public class OFDProtocolHandler extends ComponentBase
        implements XDispatchProvider, XDispatch, XServiceInfo, XInitialization {

    private XComponentContext context;
    private static final String[] SERVICE_NAMES = {
        "com.sun.star.frame.ProtocolHandler"
    };
    private static final String PROTOCOL = "org.loongoffice.ofd.reader:*";

    @Override
    public String[] getSupportedServiceNames() {
        return SERVICE_NAMES;
    }

    @Override
    public boolean supportsService(String serviceName) {
        for (String name : SERVICE_NAMES) {
            if (name.equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getImplementationName() {
        return OFDProtocolHandler.class.getName();
    }

    @Override
    public void initialize(Object[] arguments) {
        for (Object arg : arguments) {
            if (arg instanceof XComponentContext) {
                this.context = (XComponentContext) arg;
            }
        }
    }

    @Override
    public XDispatch queryDispatch(URL aURL, String targetFrameName, int searchFlags) {
        if (aURL != null && aURL.Protocol != null &&
            aURL.Protocol.startsWith("org.loongoffice.ofd.reader")) {
            return this;
        }
        return null;
    }

    @Override
    public void dispatch(URL aURL, PropertyValue[] arguments) {
        if (aURL == null) return;

        String command = aURL.Path;

        try {
            switch (command) {
                case "edit":
                    handleEditOFD();
                    break;
                case "saveas":
                    handleSaveAsOFD();
                    break;
                default:
                    logger.warn("Unknown command: " + command);
            }
        } catch (Exception e) {
            logger.error("Failed to handle command: " + command, e);
        }
    }

    private void handleEditOFD() {
        // 1. 获取当前文档
        XModel model = getCurrentDocument();
        if (model == null) {
            showError("无法获取当前文档");
            return;
        }

        // 2. 显示确认对话框
        if (!confirmEdit()) {
            return;
        }

        // 3. 获取原 OFD 路径
        String ofdPath = getOriginalOFDPath(model);
        if (ofdPath == null) {
            showError("无法找到原 OFD 文件");
            return;
        }

        // 4. 转换 OFD → PDF
        Path pdfPath = convertOFDToPDF(Paths.get(ofdPath));

        // 5. 关闭当前文档
        closeDocument(model);

        // 6. 打开 PDF（可编辑）
        openPDF(pdfPath);
    }

    private void handleSaveAsOFD() {
        // 1. 显示文件选择对话框
        Path ofdPath = showOFDSaveDialog();
        if (ofdPath == null) {
            return;  // 用户取消
        }

        // 2. 导出当前文档为 PDF
        Path pdfPath = exportToPDF();

        // 3. 转换 PDF → OFD
        try {
            convertPDFToOFD(pdfPath, ofdPath);
            showSuccess("OFD 文件保存成功");
        } catch (Exception e) {
            showError("保存失败: " + e.getMessage());
        }
    }

    @Override
    public void addStatusListener(XStatusListener listener, URL url) {
    }

    @Override
    public void removeStatusListener(XStatusListener listener, URL url) {
    }
}
```

### 7.2 注册 Protocol Handler

在 `src/main/oxt/OFDImportFilter.components` 中添加：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<components xmlns="http://openoffice.org/2001/manifest">
  <!-- 现有的 ImportFilter -->
  <component loader="com.sun.star.loader.Java2"
             uri="@extension.jar.name@">
    <implementation name="org.loongoffice.ofd.reader.OFDImportFilter">
      <service name="com.sun.star.document.ImportFilter"/>
      <service name="com.sun.star.document.ExtendedTypeDetection"/>
    </implementation>
  </component>

  <!-- 新增：Protocol Handler -->
  <component loader="com.sun.star.loader.Java2"
             uri="@extension.jar.name@">
    <implementation name="org.loongoffice.ofd.reader.OFDProtocolHandler">
      <service name="com.sun.star.frame.ProtocolHandler"/>
    </implementation>
  </component>
</components>
```

在 `src/main/resources/ProtocolHandler.xcu` 中注册：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<oor:component-data xmlns:oor="http://openoffice.org/2001/registry"
                    xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    oor:name="ProtocolHandler"
                    oor:package="org.openoffice.Office">
  <node oor:name="HandlerSet">
    <node oor:name="org.loongoffice.ofd.reader.OFDProtocolHandler" oor:op="replace">
      <prop oor:name="Protocols">
        <value>org.loongoffice.ofd.reader:*</value>
      </prop>
    </node>
  </node>
</oor:component-data>
```

在 `manifest.xml` 中添加：

```xml
<manifest:file-entry manifest:media-type="application/vnd.sun.star.configuration-data"
                     manifest:full-path="ProtocolHandler.xcu"/>
```

---

## 8. 替代方案

### 8.1 方案对比

| 方案 | 可行性 | 用户体验 | 开发难度 | 推荐度 |
|------|--------|---------|---------|--------|
| **拦截系统按钮** | ❌ 不可行 | ⭐⭐⭐⭐⭐ | 🔴 极难 | ❌ |
| **自定义菜单项** | ✅ 完全可行 | ⭐⭐⭐⭐ | 🟡 中等 | ✅✅✅ |
| **隐藏系统按钮+自定义按钮** | ✅ 可行 | ⭐⭐⭐⭐ | 🟢 简单 | ✅✅ |
| **监听文档事件** | ⚠️ 部分可行 | ⭐⭐⭐ | 🟡 中等 | ✅ |
| **独立工具栏** | ✅ 完全可行 | ⭐⭐⭐ | 🟡 中等 | ✅ |

### 8.2 推荐实现方案

#### ✅ 最佳方案：自定义菜单项 + 协议处理器

**优点**：
- ✅ 完全可行，无需依赖系统按钮
- ✅ 用户体验好（菜单清晰）
- ✅ 开发难度适中
- ✅ 易于维护

**实现步骤**：
1. 在 Addons.xcu 添加菜单项
2. 实现 OFDProtocolHandler
3. 注册 Protocol Handler
4. 实现转换逻辑

---

## 9. 总结

### 9.1 核心结论

| 问题 | 答案 |
|------|------|
| **能否拦截 Edit Document 按钮？** | ⚠️ **不能直接拦截**，但可以隐藏它并添加自定义按钮 |
| **能否拦截 Save 按钮？** | ⚠️ **不能直接拦截**，但可以通过事件监听部分拦截 |
| **最佳实现方式是什么？** | ✅ **使用自定义菜单项 + Protocol Handler** |

### 9.2 关键要点

1. **UNO 拦截能力有限**
   - 无法替换系统的 DispatchProvider
   - 无法拦截其他组件的命令

2. **替代方案可行**
   - 自定义菜单项是标准做法
   - Protocol Handler 是官方推荐方式

3. **用户体验优先**
   - 虽然不能修改系统按钮
   - 但可以提供更好的自定义界面

---

## 10. 参考资源

### 10.1 LibreOffice UNO 文档

- [Protocol Handler](https://wiki.libreoffice.org/Documentation/DevGuide/Universal_Network_Objects/Protocol_Handler)
- [Dispatch Provider](https://wiki.libreoffice.org/Documentation/DevGuide/Universal_Network_Objects/Using_the_Dispatch_Propvider)
- [Addons](https://wiki.libreoffice.org/Documentation/DevGuide/Extensions/Add-ons)

### 10.2 代码示例

- LibreOffice 官方示例：`sdk/examples/java/`
- ofdrw 库示例：[GitHub](https://github.com/Trisia/ofdrw)

---

**文档版本**: 1.0
**最后更新**: 2026-03-05
**作者**: Claude Code
**审核状态**: 待审核
