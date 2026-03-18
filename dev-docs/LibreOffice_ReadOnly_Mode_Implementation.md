# LibreOffice 只读模式提示和 Edit Document 按钮实现分析

> **文档类型**: 技术调查报告
> **调查日期**: 2025-03-05
> **项目**: LibreOffice (loongoffice)

---

## 目录

- [一、功能概述](#一功能概述)
- [二、只读模式提示消息实现](#二只读模式提示消息实现)
- [三、Edit Document 按钮实现](#三edit-document-按钮实现)
- [四、锁文件(.~lock)处理机制](#四锁文件lock处理机制)
- [五、Java UNO 接口](#五java-uno-接口)
- [六、工作流程](#六工作流程)
- [七、常见问题](#七常见问题)
- [八、关键文件路径](#八关键文件路径)

---

## 一、功能概述

### 1.1 功能描述

当用户在 LibreOffice 中打开一个已被其他实例打开的文档时：

1. **锁文件创建**: 系统自动创建 `.~lock.*` 文件
2. **只读模式打开**: 再次打开该文档时，会以只读模式打开
3. **提示信息显示**: 顶部显示黄色信息栏，提示文档处于只读模式
4. **Edit Document 按钮**: 提供"Edit Document"按钮，允许切换到编辑模式

### 1.2 PDF 签名场景的特殊处理

对于 PDF 文档，提示信息略有不同：

> "This PDF is open in read-only mode to allow signing the existing file."

额外显示 "Sign Document" 按钮，用于签名操作。

---

## 二、只读模式提示消息实现

### 2.1 提示文本定义

**文件位置**: [`include/sfx2/strings.hrc`](include/sfx2/strings.hrc)

```cpp
// PDF 文档专用提示
#define STR_READONLY_PDF NC_("STR_READONLY_PDF", \
    "This PDF is open in read-only mode to allow signing the existing file.")

// 通用文档提示
#define STR_READONLY_DOCUMENT NC_("STR_READONLY_DOCUMENT", \
    "This document is open in read-only mode.")

// Edit Document 按钮文本
#define STR_READONLY_EDIT NC_("STR_READONLY_EDIT", "Edit Document")

// Sign Document 按钮文本
#define STR_READONLY_SIGN NC_("STR_READONLY_SIGN", "Sign Document")
```

### 2.2 UI 显示组件

**文件位置**: [`sfx2/source/view/viewfrm.cxx`](sfx2/source/view/viewfrm.cxx)

**核心函数**: `SfxViewFrame::AppendReadOnlyInfobar()`

```cpp
void SfxViewFrame::AppendReadOnlyInfobar()
{
    // 检测是否为 PDF 签名场景
    bool bSignPDF = m_xObjSh.is() && m_xObjSh->IsSignPDF();

    // 创建 Infobar（只读提示栏）
    auto pInfoBar = AppendInfoBar(
        u"readonly"_ustr,                                    // Infobar ID
        u""_ustr,                                             // 主按钮（无）
        SfxResId(bSignPDF ? STR_READONLY_PDF
                          : STR_READONLY_DOCUMENT),         // 消息文本
        InfobarType::INFO                                   // 类型：信息提示
    );

    // 添加 "Edit Document" 按钮
    pInfoBar->addButton(
        SfxResId(STR_READONLY_EDIT),                        // 按钮文本："Edit Document"
        SWITCH_READONLY_HANDLER                             // 点击处理函数
    );

    // 对于 PDF，添加 "Sign Document" 按钮
    if (bSignPDF) {
        pInfoBar->addButton(
            SfxResId(STR_READONLY_SIGN),                    // 按钮文本："Sign Document"
            SIGN_DOCUMENT_HANDLER                           // 点击处理函数
        );
    }
}
```

### 2.3 触发条件

提示栏在以下条件满足时显示：

```cpp
// 在 SfxViewFrame::Notify() 函数中
if (m_xObjShell->IsReadOnly() &&           // 文档处于只读模式
    !m_xObjShell->IsSecurityOptOpenReadOnly())  // 不是安全策略导致的只读
{
    AppendReadOnlyInfobar();
}
```

---

## 三、Edit Document 按钮实现

### 3.1 按钮定义

**文件位置**: [`include/sfx2/strings.hrc`](include/sfx2/strings.hrc)

```cpp
#define STR_READONLY_EDIT NC_("STR_READONLY_EDIT", "Edit Document")
```

### 3.2 点击处理函数

**文件位置**: [`sfx2/source/view/viewfrm.cxx`](sfx2/source/view/viewfrm.cxx)

**函数**: `SfxViewFrame::SwitchReadOnlyHandler`

```cpp
IMPL_LINK(SfxViewFrame, SwitchReadOnlyHandler, weld::Button&, rButton, void)
{
    // 对于签名 PDF，显示确认对话框
    if (m_xObjSh.is() && m_xObjSh->IsSignPDF())
    {
        SfxEditDocumentDialog aDialog(&rButton);
        if (aDialog.run() != RET_OK)
            return;  // 用户取消，不执行切换
    }

    // 执行切换到编辑模式
    GetDispatcher()->Execute(SID_EDITDOC);
}
```

### 3.3 编辑确认对话框

**UI 文件**: [`sfx2/uiconfig/ui/editdocumentdialog.ui`](sfx2/uiconfig/ui/editdocumentdialog.ui)

**对话框内容**:

```
主标题: "Are you sure you want to edit the document?"

次要文本: "The original file can be signed without editing the document.
          Existing signatures on the document will be lost in case of
          saving an edited version."

按钮:
  - "Edit Document" - 确认编辑
  - "Cancel" - 取消操作
```

**实现类**: `SfxEditDocumentDialog` (继承自 `weld::MessageDialogController`)

### 3.4 SID_EDITDOC 命令

**UNO 命令**: `.uno:EditDoc`

**功能**: 在只读模式和编辑模式之间切换

**执行位置**: `SfxDispatcher::Execute(SID_EDITDOC)`

---

## 四、锁文件(.~lock)处理机制

### 4.1 锁文件创建

**核心文件**: [`svl/source/misc/documentlockfile.cxx`](svl/source/misc/documentlockfile.cxx)

**调用位置**: [`sfx2/source/doc/docfile.cxx`](sfx2/source/doc/docfile.cxx)

**创建函数**: `SfxMedium::LockOrigFileOnDemand()`

```cpp
bool SfxMedium::LockOrigFileOnDemand()
{
    // 检查是否适合创建锁文件
    if (!isSuitableProtocolForLocking(pImpl->m_aLogicName))
        return false;

    // 创建锁文件
    ::svt::DocumentLockFile aLockFile(pImpl->m_aLogicName);
    bool bResult = aLockFile.CreateOwnLockFile();

    return bResult;
}
```

### 4.2 锁文件格式

**命名规则**: `filename.~lock.hostnamePID`

**示例**: `report.~lock.myhost-12345`

**存储内容**:

| 字段 | 说明 |
|------|------|
| `OOOUSERNAME` | LibreOffice 用户名 |
| `SYSUSERNAME` | 系统用户名 |
| `LOCALHOST` | 主机名 |
| `EDITTIME` | 编辑时间戳 |
| `USERURL` | 用户 URL |

### 4.3 支持的协议

```cpp
bool isSuitableProtocolForLocking(const OUString& rLogicName)
{
    INetURLObject aUrl(rLogicName);
    INetProtocol eProt = aUrl.GetProtocol();

    return eProt == INetProtocol::File ||   // 本地文件
           eProt == INetProtocol::Smb ||    // Samba 共享
           eProt == INetProtocol::Sftp;     // SFTP 协议
}
```

**WebDAV**: 有特殊的处理方式

### 4.4 只读模式触发

**函数**: `SfxMedium::ShowLockedDocumentDialog()`

**触发逻辑**:

```
1. 检测到锁文件存在
   ↓
2. 读取锁文件信息（用户名、编辑时间等）
   ↓
3. 显示对话框，提供选项：
   - Abort         - 取消打开
   - Open Read-Only - 以只读模式打开
   - Edit Copy     - 编辑副本
   - Ignore        - 忽略锁（用于过期锁）
   ↓
4. 根据用户选择设置文档状态
```

**设置只读标志**:

```cpp
if (bOpenReadOnly)
{
    GetItemSet().Put(SfxBoolItem(SID_DOC_READONLY, true));
}
```

### 4.5 PDF 签名检测

**函数**: `SfxObjectShell::IsSignPDF()`

```cpp
bool SfxObjectShell::IsSignPDF() const
{
    if (pMedium && !pMedium->IsOriginallyReadOnly())
    {
        const std::shared_ptr<const SfxFilter>& pFilter = pMedium->GetFilter();
        if (pFilter && pFilter->GetName() == "draw_pdf_import")
            return true;
    }
    return false;
}
```

---

## 五、Java UNO 接口

### 5.1 核心文档接口

#### XModel

**IDL 文件**: [`offapi/com/sun/star/frame/XModel.idl`](offapi/com/sun/star/frame/XModel.idl)

**用途**: 文档模型的核心接口

**关键方法**:

```java
// 获取文档参数（包含只读状态）
PropertyValue[] getArgs();

// 绑定文档到 URL
boolean attachResource(String URL, PropertyValue[] Arguments);

// 控制器锁定控制
void lockControllers();
void unlockControllers();

// 获取当前控制器
XController getCurrentController();
```

#### XController

**IDL 文件**: [`offapi/com/sun/star/frame/XController.idl`](offapi/com/sun/star/frame/XController.idl)

**用途**: 视图控制器接口

**关键方法**:

```java
// 获取文档模型
XModel getModel();

// 获取框架
XFrame getFrame();

// 附加到框架
void attachFrame(XFrame Frame);
```

#### XFrame

**IDL 文件**: [`offapi/com/sun/star/frame/XFrame.idl`](offapi/com/sun/star/frame/XFrame.idl)

**用途**: 框架容器接口

**关键方法**:

```java
// 设置组件和控制器
void setComponent(XWindow ComponentWindow, XController Controller);

// 查找框架
XFrame findFrame(String TargetFrameName, int SearchFlags);
```

### 5.2 MediaDescriptor 接口

**IDL 文件**: [`offapi/com/sun/star/document/MediaDescriptor.idl`](offapi/com/sun/star/document/MediaDescriptor.idl)

**用途**: 文档加载属性描述符

#### 关键只读属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `ReadOnly` | boolean | 以只读模式打开 |
| `LockEditDoc` | boolean | **禁止从只读切换到编辑模式** |
| `LockContentExtraction` | boolean | 禁止复制/拖拽内容 |
| `LockExport` | boolean | 禁止导出 |
| `LockPrint` | boolean | 禁止打印 |
| `LockSave` | boolean | 禁止保存 |
| `Preview` | boolean | 以预览模式打开 |
| `Hidden` | boolean | 隐藏加载 |

#### 使用示例

```java
// 以只读模式打开文档
PropertyValue[] loadProps = new PropertyValue[1];
loadProps[0] = new PropertyValue();
loadProps[0].Name = "ReadOnly";
loadProps[0].Value = Boolean.TRUE;

XComponentLoader loader = ...;
XComponent doc = loader.loadComponentFromURL(
    "file:///path/to/document.odt",
    "_blank",
    0,
    loadProps
);
```

### 5.3 切换编辑模式

**UNO 命令**: `.uno:EditDoc`

**用途**: 在只读模式和编辑模式之间切换

**执行方式**: 通过 `XDispatch` 接口

```java
// 获取 Frame
XFrame frame = ...;

// 获取 DispatchProvider
XDispatchProvider dispatchProvider = UnoRuntime.queryInterface(
    XDispatchProvider.class, frame
);

// 创建 URL
com.sun.star.util.URL url = new com.sun.star.util.URL();
url.Complete = ".uno:EditDoc";

// 查询 Dispatch
XDispatch dispatch = dispatchProvider.queryDispatch(url, "_self", 0);

// 执行命令
dispatch.dispatch(url, new PropertyValue[0]);
```

### 5.4 检测只读状态

```java
// 获取文档模型
XModel model = UnoRuntime.queryInterface(XModel.class, doc);

// 获取文档参数
PropertyValue[] args = model.getArgs();

// 遍历查找 ReadOnly 属性
for (PropertyValue prop : args) {
    if ("ReadOnly".equals(prop.Name)) {
        boolean isReadOnly = ((Boolean)prop.Value).booleanValue();
        System.out.println("Document is read-only: " + isReadOnly);
        break;
    }
}
```

### 5.5 完整示例：只读模式配置

```java
import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.URL;

public class ReadOnlyDocumentExample {

    public static XComponent openReadOnly(XComponentLoader loader, String url)
            throws Exception {

        // 配置只读模式
        PropertyValue[] loadProps = new PropertyValue[2];

        // 设置为只读模式
        loadProps[0] = new PropertyValue();
        loadProps[0].Name = "ReadOnly";
        loadProps[0].Value = Boolean.TRUE;

        // 允许切换到编辑模式（显示"Edit Document"按钮）
        loadProps[1] = new PropertyValue();
        loadProps[1].Name = "LockEditDoc";
        loadProps[1].Value = Boolean.FALSE;

        // 加载文档
        return loader.loadComponentFromURL(url, "_blank", 0, loadProps);
    }

    public static void switchToEditMode(XComponent doc) {
        // 获取模型
        XModel model = UnoRuntime.queryInterface(XModel.class, doc);
        XFrame frame = model.getCurrentController().getFrame();

        // 获取 DispatchProvider
        XDispatchProvider dispatchProvider = UnoRuntime.queryInterface(
            XDispatchProvider.class, frame
        );

        // 创建 EditDoc 命令
        URL url = new URL();
        url.Complete = ".uno:EditDoc";

        // 执行命令
        XDispatch dispatch = dispatchProvider.queryDispatch(url, "_self", 0);
        if (dispatch != null) {
            dispatch.dispatch(url, new PropertyValue[0]);
        }
    }

    public static boolean isReadOnly(XComponent doc) {
        XModel model = UnoRuntime.queryInterface(XModel.class, doc);
        PropertyValue[] args = model.getArgs();

        for (PropertyValue prop : args) {
            if ("ReadOnly".equals(prop.Name)) {
                return ((Boolean)prop.Value).booleanValue();
            }
        }
        return false;
    }
}
```

---

## 六、工作流程

### 6.1 完整流程图

```
┌─────────────────────┐
│   文档打开请求       │
└──────────┬──────────┘
           ↓
┌─────────────────────────────────┐
│ SfxMedium::LockOrigFileOnDemand() │
└──────────┬──────────────────────┘
           ↓
┌─────────────────────┐
│  检测锁文件是否存在  │
│  (.~lock.*)         │
└──────────┬──────────┘
           ↓
      ┌────┴────┐
      │         │
   无锁文件   有锁文件
      │         │
      │         ↓
      │   ┌──────────────────────────────┐
      │   │ SfxMedium::                   │
      │   │ ShowLockedDocumentDialog()   │
      │   └──────────┬───────────────────┘
      │              ↓
      │      ┌─────────┴─────────┐
      │      │                   │
      │   Abort              Open Read-Only
      │      │                   │
      │      │    ┌──────────────┴───────────┐
      │      │    │                         │
      │      │    ↓                         ↓
      │      │ Edit Copy               Ignore
      │      │    │                      (过期锁)
      │      └────┴──────────────────────┘
      │              ↓
      │      设置 SID_DOC_READONLY = true
      │              ↓
      └──────────────┘
                     ↓
          ┌──────────────────────┐
          │    文档加载完成       │
          └──────────┬───────────┘
                     ↓
          ┌──────────────────────────────┐
          │ SfxViewFrame::Notify()       │
          │ 检测到只读状态                │
          └──────────┬───────────────────┘
                     ↓
          ┌──────────────────────────────┐
          │ SfxViewFrame::               │
          │ AppendReadOnlyInfobar()     │
          └──────────┬───────────────────┘
                     ↓
          ┌──────────────────────┐
          │  显示只读提示栏       │
          │  ├─ 提示消息          │
          │  └─ [Edit Document]  │
          └──────────┬───────────┘
                     ↓
          ┌──────────────────────┐
          │ 用户点击              │
          │ [Edit Document]      │
          └──────────┬───────────┘
                     ↓
          ┌──────────────────────────────┐
          │ SfxViewFrame::               │
          │ SwitchReadOnlyHandler()     │
          └──────────┬───────────────────┘
                     ↓
              ┌──────┴──────┐
              │             │
         非 PDF 文档    PDF 签名
              │             │
              │             ↓
              │      ┌─────────────────┐
              │      │ 显示确认对话框   │
              │      │ "签名将丢失"    │
              │      └────────┬────────┘
              │               │
              │      ┌────────┴────────┐
              │      │                 │
              │   取消              确认编辑
              │      │                 │
              └──────┴─────────────────┘
                     ↓
          ┌──────────────────────┐
          │ 执行 .uno:EditDoc    │
          │ 切换到编辑模式       │
          └──────────────────────┘
```

### 6.2 关键调用链

**锁文件检测**:
```
SfxMedium::LockOrigFileOnDemand()
  → DocumentLockFile::CreateOwnLockFile()
  → DocumentLockFile::GetLockData()
```

**只读模式显示**:
```
SfxViewFrame::Notify()
  → SfxViewFrame::AppendReadOnlyInfobar()
    → AppendInfoBar()
    → addButton()  [Edit Document]
```

**切换到编辑模式**:
```
SwitchReadOnlyHandler()
  → SfxViewFrame::SwitchReadOnlyHandler()
    → SfxDispatcher::Execute(SID_EDITDOC)
      → .uno:EditDoc UNO 命令
```

---

## 七、常见问题

### 7.1 为什么没有显示"Edit Document"按钮？

**问题**: 设置了只读模式，但是没有显示"Edit Document"按钮

**原因**: 设置了 `LockEditDoc = true`

**解决方案**:

```java
// 错误配置
DEFAULT_VALUES.put("ReadOnly", true);
DEFAULT_VALUES.put("LockEditDoc", true);  // ❌ 禁止编辑，不显示按钮

// 正确配置
DEFAULT_VALUES.put("ReadOnly", true);
DEFAULT_VALUES.put("LockEditDoc", false); // ✅ 允许切换到编辑模式
```

**属性说明**:

| 属性 | 值 | 效果 |
|------|-----|------|
| `ReadOnly` | true | 文档以只读模式打开 |
| `LockEditDoc` | **false** | **显示"Edit Document"按钮，允许切换到编辑模式** |
| `LockEditDoc` | true | **不显示"Edit Document"按钮，禁止编辑** |

### 7.2 如何检测文档是否为 PDF 签名模式？

```java
boolean isSignPDF = false;

XModel model = UnoRuntime.queryInterface(XModel.class, doc);
PropertyValue[] args = model.getArgs();

for (PropertyValue prop : args) {
    // 检查过滤器名称
    if ("FilterName".equals(prop.Name)) {
        String filterName = (String)prop.Value;
        isSignPDF = "draw_pdf_import".equals(filterName);
        break;
    }
}

// PDF 签名模式会显示不同的提示文本和额外的"Sign Document"按钮
```

### 7.3 如何自定义只读模式的行为？

```java
PropertyValue[] props = new PropertyValue[6];

// 基础只读设置
props[0] = createProp("ReadOnly", Boolean.TRUE);

// 允许编辑（显示 Edit Document 按钮）
props[1] = createProp("LockEditDoc", Boolean.FALSE);

// 禁止保存
props[2] = createProp("LockSave", Boolean.TRUE);

// 允许打印
props[3] = createProp("LockPrint", Boolean.FALSE);

// 禁止复制内容
props[4] = createProp("LockContentExtraction", Boolean.TRUE);

// 禁止导出
props[5] = createProp("LockExport", Boolean.TRUE);
```

### 7.4 锁文件在什么情况下会被忽略？

**过期锁文件** (超过 5 分钟):

```cpp
// 在 DocumentLockFile 中
if (aEditTime.Seconds() > 300)  // 5 分钟 = 300 秒
{
    // 显示 "Ignore" 选项
}
```

**损坏的锁文件**:

显示 `LockFileCorruptRequest` 对话框，允许用户忽略并继续。

---

## 八、关键文件路径

### 8.1 C++ 实现文件

| 功能 | 文件路径 |
|------|---------|
| 提示文本定义 | [`include/sfx2/strings.hrc`](include/sfx2/strings.hrc) |
| Infobar 显示 | [`sfx2/source/view/viewfrm.cxx`](sfx2/source/view/viewfrm.cxx) |
| 锁文件创建 | [`sfx2/source/doc/docfile.cxx`](sfx2/source/doc/docfile.cxx) |
| 锁文件操作 | [`svl/source/misc/documentlockfile.cxx`](svl/source/misc/documentlockfile.cxx) |
| 编辑确认 UI | [`sfx2/uiconfig/ui/editdocumentdialog.ui`](sfx2/uiconfig/ui/editdocumentdialog.ui) |
| 锁文件接口 | [`svl/inc/svl/documentlockfile.hxx`](svl/inc/svl/documentlockfile.hxx) |
| MSO 锁文件支持 | [`svl/source/misc/msodocumentlockfile.cxx`](svl/source/misc/msodocumentlockfile.cxx) |

### 8.2 Java UNO 接口定义

| 接口 | IDL 文件路径 |
|------|------------|
| XModel | [`offapi/com/sun/star/frame/XModel.idl`](offapi/com/sun/star/frame/XModel.idl) |
| XModel2 | [`offapi/com/sun/star/frame/XModel2.idl`](offapi/com/sun/star/frame/XModel2.idl) |
| XController | [`offapi/com/sun/star/frame/XController.idl`](offapi/com/sun/star/frame/XController.idl) |
| XController2 | [`offapi/com/sun/star/frame/XController2.idl`](offapi/com/sun/star/frame/XController2.idl) |
| XFrame | [`offapi/com/sun/star/frame/XFrame.idl`](offapi/com/sun/star/frame/XFrame.idl) |
| XComponentLoader | [`offapi/com/sun/star/frame/XComponentLoader.idl`](offapi/com/sun/star/frame/XComponentLoader.idl) |
| XDesktop | [`offapi/com/sun/star/frame/XDesktop.idl`](offapi/com/sun/star/frame/XDesktop.idl) |
| XDesktop2 | [`offapi/com/sun/star/frame/XDesktop2.idl`](offapi/com/sun/star/frame/XDesktop2.idl) |
| MediaDescriptor | [`offapi/com/sun/star/document/MediaDescriptor.idl`](offapi/com/sun/star/document/MediaDescriptor.idl) |

### 8.3 相关常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| `STR_READONLY_PDF` | - | PDF 只读提示文本 |
| `STR_READONLY_DOCUMENT` | - | 通用只读提示文本 |
| `STR_READONLY_EDIT` | - | "Edit Document" 按钮文本 |
| `STR_READONLY_SIGN` | - | "Sign Document" 按钮文本 |
| `SID_EDITDOC` | - | 编辑文档命令 ID |
| `SID_DOC_READONLY` | - | 只读文档属性 ID |

---

## 总结

### 核心组件

1. **提示消息**: C++ 字符串资源 + Infobar UI 组件
2. **Edit Document 按钮**: weld::Button + 事件处理器
3. **锁文件机制**: DocumentLockFile 类 + SfxMedium 集成

### Java UNO 接口

- **控制只读模式**: `MediaDescriptor.ReadOnly` 属性
- **控制编辑按钮**: `MediaDescriptor.LockEditDoc` 属性
- **检测只读状态**: `XModel.getArgs()` 获取文档参数
- **切换编辑模式**: 执行 `.uno:EditDoc` UNO 命令
- **核心接口**: `XModel`, `XController`, `XFrame`, `XDispatch`

### 特殊处理

- **PDF 签名场景**: 使用不同的提示文本，额外显示签名按钮
- **确认对话框**: PDF 编辑时显示警告提示签名会丢失
- **过期锁处理**: 超过 5 分钟的锁文件可以忽略

---

**文档版本**: 1.0
**最后更新**: 2025-03-05
**作者**: Claude Code 分析报告
