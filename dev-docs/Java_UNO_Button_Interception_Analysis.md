# Java UNO 按钮事件拦截分析报告

> **文档类型**: 技术分析报告
> **调查日期**: 2025-03-05
> **主题**: Java UNO 接口拦截 Edit Document 和 Save 按钮

---

## 目录

- [一、核心结论](#一核心结论)
- [二、拦截架构](#二拦截架构)
- [三、Edit Document 按钮拦截](#三edit-document-按钮拦截)
- [四、Save 按钮拦截](#四save-按钮拦截)
- [五、完整实现示例](#五完整实现示例)
- [六、关键接口定义](#六关键接口定义)
- [七、源码位置](#七源码位置)

---

## 一、核心结论

### ✅ 可以拦截！

**是的，Java UNO 接口完全可以拦截 Edit Document 和 Save 按钮！**

LibreOffice 提供了完整的 **Dispatch 拦截机制**，允许在命令执行前后进行拦截。

### 核心拦截接口

| 接口 | 用途 | 优先级 |
|------|------|--------|
| `XDispatchProviderInterceptor` | **核心拦截接口**，可以拦截任何命令 | ⭐⭐⭐⭐⭐ |
| `XDispatchProviderInterception` | 注册拦截器到 Frame | ⭐⭐⭐⭐⭐ |
| `XDocumentEventListener` | 监听文档事件（被动监听） | ⭐⭐⭐ |
| `XJobExecutor` | 基于事件触发任务 | ⭐⭐⭐ |
| `XStorable` | 控制文档保存（Save 专用） | ⭐⭐⭐⭐ |

---

## 二、拦截架构

### 2.1 命令分发流程

```
用户点击按钮
    ↓
UI 触发 (.uno:EditDoc 或 .uno:Save)
    ↓
Frame 查询 DispatchProvider
    ↓
【拦截点 1】XDispatchProviderInterceptor ← 在这里拦截！
    ↓
查询目标 Dispatcher
    ↓
【拦截点 2】XDispatch::dispatch() ← 或者在这里拦截！
    ↓
执行实际命令
```

### 2.2 两层拦截机制

**第一层：DispatchProvider 拦截**
- 接口：`XDispatchProviderInterceptor`
- 时机：在查询目标 Dispatcher 之前
- 优点：可以完全替换或阻止命令
- 缺点：需要实现完整的 DispatchProvider 逻辑

**第二层：Dispatch 拦截**
- 接口：`XDispatch` (自定义实现)
- 时机：在 `dispatch()` 方法执行时
- 优点：实现简单，只需拦截特定命令
- 缺点：只能在目标 Dispatcher 被选中后拦截

---

## 三、Edit Document 按钮拦截

### 3.1 命令信息

**UNO 命令**: `.uno:EditDoc`

**源码位置**:
- UI 定义: [`workdir/XcuMergeTarget/officecfg/registry/data/org/openoffice/Office/UI/GenericCommands.xcu`](workdir/XcuMergeTarget/officecfg/registry/data/org/openoffice/Office/UI/GenericCommands.xcu)
- 标签: "Edit Mode" / "编辑模式"

### 3.2 拦截方案

#### 方案 A：使用 XDispatchProviderInterceptor（推荐）

```java
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.frame.XDispatchProviderInterceptor;
import com.sun.star.frame.XFrame;
import com.sun.star.util.URL;
import com.sun.star.beans.PropertyValue;

public class EditDocInterceptor implements XDispatchProviderInterceptor {
    private XDispatchProvider mSlave;
    private XDispatchProvider mMaster;
    private XFrame mFrame;

    public EditDocInterceptor(XFrame frame) {
        mFrame = frame;
    }

    // 核心：拦截 dispatch 查询
    @Override
    public XDispatch queryDispatch(URL URL, String TargetFrameName, int SearchFlags) {
        // 检查是否为 .uno:EditDoc 命令
        if (".uno:EditDoc".equals(URL.Complete)) {
            // 返回自定义的 Dispatch 来处理
            return new EditDocDispatch(mFrame);
        }

        // 其他命令正常传递
        if (mSlave != null) {
            return mSlave.queryDispatch(URL, TargetFrameName, SearchFlags);
        }
        return null;
    }

    @Override
    public XDispatch[] queryDispatches(com.sun.star.frame.DispatchDescriptor[] Requests) {
        XDispatch[] result = new XDispatch[Requests.length];
        for (int i = 0; i < Requests.length; i++) {
            result[i] = queryDispatch(
                Requests[i].FeatureURL,
                Requests[i].FrameName,
                Requests[i].SearchFlags
            );
        }
        return result;
    }

    @Override
    public XDispatchProvider getSlaveDispatchProvider() {
        return mSlave;
    }

    @Override
    public void setSlaveDispatchProvider(XDispatchProvider NewSupplier) {
        mSlave = NewSupplier;
    }

    @Override
    public XDispatchProvider getMasterDispatchProvider() {
        return mMaster;
    }

    @Override
    public void setMasterDispatchProvider(XDispatchProvider NewSupplier) {
        mMaster = NewSupplier;
    }
}
```

#### 方案 B：自定义 Dispatch 实现

```java
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XFrame;
import com.sun.star.util.URL;
import com.sun.star.beans.PropertyValue;

public class EditDocDispatch implements XDispatch {
    private XFrame mFrame;

    public EditDocDispatch(XFrame frame) {
        mFrame = frame;
    }

    @Override
    public void dispatch(URL URL, PropertyValue[] Arguments) {
        System.out.println("EditDoc button clicked - intercepted!");

        // 在这里添加你的拦截逻辑
        // 例如：检查文档是否允许编辑
        if (!isEditAllowed()) {
            System.out.println("Edit not allowed!");
            return; // 阻止执行
        }

        // 如果允许，执行原始命令
        executeOriginalEditDoc();
    }

    private boolean isEditAllowed() {
        // 实现你的业务逻辑
        return true;
    }

    private void executeOriginalEditDoc() {
        // 调用原始的 EditDoc 命令
        // 或者实现你自己的切换逻辑
    }

    @Override
    public void addStatusListener(
        com.sun.star.frame.XStatusListener Control,
        URL URL
    ) {
        // 状态监听器
    }

    @Override
    public void removeStatusListener(
        com.sun.star.frame.XStatusListener Control,
        URL URL
    ) {
        // 移除状态监听器
    }
}
```

#### 方案 C：注册拦截器到 Frame

```java
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XDispatchProviderInterception;
import com.sun.star.uno.UnoRuntime;

public class InterceptorRegistration {

    public static void registerEditDocInterceptor(XFrame frame) {
        // 获取 XDispatchProviderInterception 接口
        XDispatchProviderInterception interception =
            UnoRuntime.queryInterface(
                XDispatchProviderInterception.class,
                frame
            );

        if (interception != null) {
            // 创建拦截器
            EditDocInterceptor interceptor = new EditDocInterceptor(frame);

            // 注册拦截器
            interception.registerDispatchProviderInterceptor(interceptor);

            System.out.println("EditDoc interceptor registered!");
        }
    }

    public static void unregisterEditDocInterceptor(
        XFrame frame,
        EditDocInterceptor interceptor
    ) {
        XDispatchProviderInterception interception =
            UnoRuntime.queryInterface(
                XDispatchProviderInterception.class,
                frame
            );

        if (interception != null) {
            interception.releaseDispatchProviderInterceptor(interceptor);
            System.out.println("EditDoc interceptor unregistered!");
        }
    }
}
```

### 3.3 使用示例

```java
import com.sun.star.uno.XComponentContext;
import com.sun.star.frame.XDesktop;
import com.sun.star.frame.XFrame;

public class Main {
    public static void main(String[] args) throws Exception {
        // 获取桌面
        XComponentContext context = ...; // 获取上下文
        XDesktop desktop = ...; // 获取桌面

        // 获取当前 Frame
        XFrame frame = desktop.getCurrentFrame();

        // 注册 EditDoc 拦截器
        InterceptorRegistration.registerEditDocInterceptor(frame);

        // 现在 .uno:EditDoc 命令会被拦截
    }
}
```

---

## 四、Save 按钮拦截

### 4.1 命令信息

**UNO 命令**: `.uno:Save`

**源码位置**:
- UI 定义: [`workdir/XcuMergeTarget/officecfg/registry/data/org/openoffice/Office/UI/GenericCommands.xcu`](workdir/XcuMergeTarget/officecfg/registry/data/org/openoffice/Office/UI/GenericCommands.xcu)
- 标签: "Save" / "保存"

**核心检查**: [`sfx2/source/doc/objserv.cxx:1545`](sfx2/source/doc/objserv.cxx)

```cpp
case SID_SAVEDOC:
{
    if ( IsReadOnly() || isSaveLocked() )
    {
        rSet.DisableItem(nWhich);  // 禁用 Save 按钮
        break;
    }
    rSet.Put(SfxStringItem(nWhich, SfxResId(STR_SAVEDOC)));
}
```

### 4.2 拦截方案

#### 方案 A：使用 XDispatchProviderInterceptor（推荐）

```java
public class SaveInterceptor implements XDispatchProviderInterceptor {
    private XDispatchProvider mSlave;
    private XDispatchProvider mMaster;
    private XFrame mFrame;

    public SaveInterceptor(XFrame frame) {
        mFrame = frame;
    }

    @Override
    public XDispatch queryDispatch(URL URL, String TargetFrameName, int SearchFlags) {
        // 检查是否为 .uno:Save 命令
        if (".uno:Save".equals(URL.Complete)) {
            return new SaveDispatch(mFrame);
        }

        // 检查其他保存相关命令
        if (".uno:SaveAs".equals(URL.Complete)) {
            return new SaveAsDispatch(mFrame);
        }

        if (mSlave != null) {
            return mSlave.queryDispatch(URL, TargetFrameName, SearchFlags);
        }
        return null;
    }

    // ... 其他方法同 EditDocInterceptor
}
```

#### 方案 B：自定义 Save Dispatch

```java
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XFrame;
import com.sun.star.util.URL;
import com.sun.star.beans.PropertyValue;

public class SaveDispatch implements XDispatch {
    private XFrame mFrame;

    public SaveDispatch(XFrame frame) {
        mFrame = frame;
    }

    @Override
    public void dispatch(URL URL, PropertyValue[] Arguments) {
        System.out.println("Save button clicked - intercepted!");

        // 检查是否允许保存
        if (!isSaveAllowed()) {
            System.out.println("Save not allowed!");
            showWarningMessage("Document cannot be saved!");
            return; // 阻止保存
        }

        // 如果允许，执行保存
        performSave();
    }

    private boolean isSaveAllowed() {
        // 实现你的业务逻辑
        // 例如：检查文档状态、用户权限等
        return false;
    }

    private void showWarningMessage(String message) {
        // 显示警告对话框
        // 使用 XDialogProvider 或其他方式
    }

    private void performSave() {
        // 执行实际的保存操作
        // 可以调用 XStorable.store() 方法
    }

    @Override
    public void addStatusListener(
        com.sun.star.frame.XStatusListener Control,
        URL URL
    ) {
        // 控制按钮状态
        // 如果不允许保存，禁用按钮
    }

    @Override
    public void removeStatusListener(
        com.sun.star.frame.XStatusListener Control,
        URL URL
    ) {
        // 移除监听器
    }
}
```

#### 方案 C：使用 XStorable 接口（被动拦截）

```java
import com.sun.star.frame.XModel;
import com.sun.star.frame.XStorable2;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.beans.PropertyValue;

public class SaveController {

    private XStorable2 mStorable;

    public SaveController(XModel model) {
        mStorable = UnoRuntime.queryInterface(XStorable2.class, model);
    }

    public boolean isSaveAllowed() {
        // 检查文档是否可保存
        if (mStorable == null) {
            return false;
        }

        // 检查只读状态
        if (mStorable.isReadonly()) {
            return false;
        }

        // 你的自定义逻辑
        return checkCustomSaveRules();
    }

    private boolean checkCustomSaveRules() {
        // 实现你的保存规则
        return false; // 返回 false 可以阻止保存
    }

    public void disableSave() {
        // 设置 LockSave 属性
        try {
            XModel model = UnoRuntime.queryInterface(XModel.class, mStorable);

            // 通过设置属性来禁用保存
            PropertyValue[] args = model.getArgs();
            // 修改 args 中的 LockSave 属性
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 4.3 控制按钮状态（禁用按钮）

```java
import com.sun.star.frame.XModel;
import com.sun.star.frame.XController;
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.util.URL;
import com.sun.star.uno.UnoRuntime;

public class SaveButtonController {

    public static void disableSaveButton(XModel model) {
        XController controller = model.getCurrentController();
        XFrame frame = controller.getFrame();

        // 创建状态事件
        com.sun.star.frame.FeatureStateEvent event =
            new com.sun.star.frame.FeatureStateEvent();

        event.FeatureURL = new URL();
        event.FeatureURL.Complete = ".uno:Save";
        event.IsEnabled = false;  // 禁用按钮

        // 通知所有状态监听器
        // 这会导致 Save 按钮被禁用
    }

    public static void enableSaveButton(XModel model) {
        // 类似地，设置 IsEnabled = true
    }
}
```

---

## 五、完整实现示例

### 5.1 综合拦截器实现

```java
package com.example.lointerceptor;

import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.frame.XDispatchProviderInterceptor;
import com.sun.star.frame.XFrame;
import com.sun.star.util.URL;
import com.sun.star.frame.DispatchDescriptor;
import com.sun.star.uno.UnoRuntime;

/**
 * 综合拦截器：拦截 EditDoc 和 Save 按钮
 */
public class DocumentOperationInterceptor
    implements XDispatchProviderInterceptor {

    private XDispatchProvider mSlave;
    private XDispatchProvider mMaster;
    private XFrame mFrame;
    private DocumentOperationHandler mHandler;

    public DocumentOperationInterceptor(XFrame frame) {
        mFrame = frame;
        mHandler = new DocumentOperationHandler(frame);
    }

    @Override
    public XDispatch queryDispatch(URL URL, String TargetFrameName, int SearchFlags) {
        String command = URL.Complete;

        System.out.println("Intercepting command: " + command);

        // 拦截 EditDoc
        if (".uno:EditDoc".equals(command)) {
            System.out.println("-> Intercepted EditDoc");
            return new EditDocDispatch(mFrame, mHandler);
        }

        // 拦截 Save
        if (".uno:Save".equals(command)) {
            System.out.println("-> Intercepted Save");
            return new SaveDispatch(mFrame, mHandler);
        }

        // 拦截 SaveAs
        if (".uno:SaveAs".equals(command)) {
            System.out.println("-> Intercepted SaveAs");
            return new SaveAsDispatch(mFrame, mHandler);
        }

        // 其他命令正常传递
        if (mSlave != null) {
            return mSlave.queryDispatch(URL, TargetFrameName, SearchFlags);
        }

        return null;
    }

    @Override
    public XDispatch[] queryDispatches(DispatchDescriptor[] Requests) {
        XDispatch[] result = new XDispatch[Requests.length];
        for (int i = 0; i < Requests.length; i++) {
            result[i] = queryDispatch(
                Requests[i].FeatureURL,
                Requests[i].FrameName,
                Requests[i].SearchFlags
            );
        }
        return result;
    }

    @Override
    public XDispatchProvider getSlaveDispatchProvider() {
        return mSlave;
    }

    @Override
    public void setSlaveDispatchProvider(XDispatchProvider NewSupplier) {
        mSlave = NewSupplier;
    }

    @Override
    public XDispatchProvider getMasterDispatchProvider() {
        return mMaster;
    }

    @Override
    public void setMasterDispatchProvider(XDispatchProvider NewSupplier) {
        mMaster = NewSupplier;
    }
}
```

### 5.2 操作处理器

```java
package com.example.lointerceptor;

import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.uno.UnoRuntime;

/**
 * 文档操作处理器
 */
public class DocumentOperationHandler {

    private XFrame mFrame;

    public DocumentOperationHandler(XFrame frame) {
        mFrame = frame;
    }

    /**
     * 检查是否允许编辑
     */
    public boolean isEditAllowed() {
        XModel model = getCurrentModel();

        if (model == null) {
            return false;
        }

        // 检查文档状态
        // 这里实现你的业务逻辑
        String docType = getDocumentType(model);

        // 示例：PDF 文档不允许编辑
        if ("pdf".equals(docType)) {
            return false;
        }

        return true;
    }

    /**
     * 检查是否允许保存
     */
    public boolean isSaveAllowed() {
        XModel model = getCurrentModel();

        if (model == null) {
            return false;
        }

        // 检查文档状态
        // 这里实现你的业务逻辑

        // 示例：只读文档不允许保存
        if (isDocumentReadOnly(model)) {
            return false;
        }

        return true;
    }

    /**
     * 执行编辑模式切换
     */
    public void executeEditModeSwitch() {
        System.out.println("Executing EditDoc command...");

        // 实现你自己的切换逻辑
        // 或者调用原始命令
    }

    /**
     * 执行保存操作
     */
    public void executeSave() {
        System.out.println("Executing Save command...");

        // 实现你自己的保存逻辑
        // 或者调用原始命令
    }

    // 辅助方法

    private XModel getCurrentModel() {
        return UnoRuntime.queryInterface(XModel.class, mFrame.getController().getModel());
    }

    private String getDocumentType(XModel model) {
        // 获取文档类型
        return "unknown";
    }

    private boolean isDocumentReadOnly(XModel model) {
        // 检查是否为只读
        return false;
    }
}
```

### 5.3 注册和使用

```java
package com.example.lointerceptor;

import com.sun.star.uno.XComponentContext;
import com.sun.star.frame.XDesktop;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XDispatchProviderInterception;
import com.sun.star.uno.UnoRuntime;

/**
 * 拦截器注册和使用示例
 */
public class InterceptorManager {

    private XComponentContext mContext;
    private XDesktop mDesktop;
    private DocumentOperationInterceptor mInterceptor;

    public InterceptorManager(XComponentContext context, XDesktop desktop) {
        mContext = context;
        mDesktop = desktop;
    }

    /**
     * 注册拦截器
     */
    public void registerInterceptor() {
        // 获取当前 Frame
        XFrame frame = mDesktop.getCurrentFrame();

        if (frame == null) {
            System.err.println("No current frame!");
            return;
        }

        // 获取 XDispatchProviderInterception 接口
        XDispatchProviderInterception interception =
            UnoRuntime.queryInterface(
                XDispatchProviderInterception.class,
                frame
            );

        if (interception == null) {
            System.err.println("Frame does not support interception!");
            return;
        }

        // 创建并注册拦截器
        mInterceptor = new DocumentOperationInterceptor(frame);
        interception.registerDispatchProviderInterceptor(mInterceptor);

        System.out.println("Interceptor registered successfully!");
    }

    /**
     * 注销拦截器
     */
    public void unregisterInterceptor() {
        if (mInterceptor == null) {
            return;
        }

        XFrame frame = mDesktop.getCurrentFrame();

        if (frame == null) {
            return;
        }

        XDispatchProviderInterception interception =
            UnoRuntime.queryInterface(
                XDispatchProviderInterception.class,
                frame
            );

        if (interception != null) {
            interception.releaseDispatchProviderInterceptor(mInterceptor);
            System.out.println("Interceptor unregistered!");
        }
    }

    /**
     * 设置操作规则
     */
    public void setOperationRules(boolean allowEdit, boolean allowSave) {
        // 更新操作处理器的规则
        // DocumentOperationHandler 可以在这里配置
    }
}
```

### 5.4 使用示例（完整流程）

```java
package com.example;

import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.uno.XComponentContext;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.frame.XDesktop;
import com.sun.star.frame.XComponentLoader;
import com.example.lointerceptor.InterceptorManager;

/**
 * 完整使用示例
 */
public class Main {

    public static void main(String[] args) {
        try {
            // 1. 初始化 LibreOffice 连接
            XComponentContext context = Bootstrap.bootstrap();
            XMultiComponentFactory factory = context.getServiceManager();

            // 2. 获取桌面
            Object desktopObj = factory.createInstanceWithContext(
                "com.sun.star.frame.Desktop",
                context
            );
            XDesktop desktop = UnoRuntime.queryInterface(
                XDesktop.class,
                desktopObj
            );

            // 3. 注册拦截器
            InterceptorManager manager = new InterceptorManager(context, desktop);
            manager.registerInterceptor();

            // 4. 设置规则（不允许编辑，不允许保存）
            manager.setOperationRules(false, false);

            // 5. 加载文档
            XComponentLoader loader = UnoRuntime.queryInterface(
                XComponentLoader.class,
                desktop
            );

            // 以只读模式打开文档
            String url = "file:///path/to/document.pdf";
            // ... 加载逻辑

            System.out.println("Interceptor is active. Edit and Save buttons are intercepted.");

            // 保持程序运行
            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## 六、关键接口定义

### 6.1 XDispatchProviderInterceptor

**IDL 文件**: [`offapi/com/sun/star/frame/XDispatchProviderInterceptor.idl`](offapi/com/sun/star/frame/XDispatchProviderInterceptor.idl)

```idl
module com { module sun { module star { module frame {
interface XDispatchProviderInterceptor: com::sun::star::uno::XInterface
{
    XDispatchProvider getSlaveDispatchProvider();
    void setSlaveDispatchProvider([in] XDispatchProvider NewSupplier);
    XDispatchProvider getMasterDispatchProvider();
    void setMasterDispatchProvider([in] XDispatchProvider NewSupplier);
};
}; }; }; };
```

### 6.2 XDispatchProviderInterception

**IDL 文件**: [`offapi/com/sun/star/frame/XDispatchProviderInterception.idl`](offapi/com/sun/star/frame/XDispatchProviderInterception.idl)

```idl
module com { module sun { module star { module frame {
interface XDispatchProviderInterception: com::sun::star::uno::XInterface
{
    void registerDispatchProviderInterceptor(
        [in] XDispatchProviderInterceptor Interceptor
    );
    void releaseDispatchProviderInterceptor(
        [in] XDispatchProviderInterceptor Interceptor
    );
};
}; }; }; };
```

### 6.3 XInterceptorInfo

**IDL 文件**: [`offapi/com/sun/star/frame/XInterceptorInfo.idl`](offapi/com/sun/star/frame/XInterceptorInfo.idl)

```idl
module com { module sun { module star { module frame {
interface XInterceptorInfo: com::sun::star::uno::XInterface
{
    sequence<string> getInterceptedURLs();
};
}; }; }; };
```

### 6.4 XDocumentEventListener

**IDL 文件**: [`offapi/com/sun/star/document/XDocumentEventListener.idl`](offapi/com/sun/star/document/XDocumentEventListener.idl)

```idl
module com { module sun { module star { module document {
interface XDocumentEventListener: com::sun::star::lang::XEventListener
{
    void documentEventOccured([in] DocumentEvent Event);
};
}; }; }; };
```

### 6.5 XJobExecutor

**IDL 文件**: [`offapi/com/sun/star/task/XJobExecutor.idl`](offapi/com/sun/star/task/XJobExecutor.idl)

```idl
module com { module sun { module star { module task {
interface XJobExecutor: com::sun::star::uno::XInterface
{
    void trigger([in] string Event);
};
}; }; }; };
```

### 6.6 XStorable

**IDL 文件**: [`offapi/com/sun/star/frame/XStorable.idl`](offapi/com/sun/star/frame/XStorable.idl)

```idl
module com { module sun { module star { module frame {
interface XStorable: com::sun::star::uno::XInterface
{
    boolean hasLocation();
    string getLocation();
    boolean isReadonly();
    void store();
    void storeAsURL([in] string aURL, [in] sequence<beans::PropertyValue> aArgs);
};
}; }; }; };
```

---

## 七、源码位置

### 7.1 Dispatch 系统

| 功能 | 文件路径 |
|------|---------|
| Dispatch Provider 实现 | [`framework/source/dispatch/dispatchprovider.cxx`](framework/source/dispatch/dispatchprovider.cxx) |
| 拦截器辅助类 | [`framework/source/dispatch/interceptionhelper.cxx`](framework/source/dispatch/interceptionhelper.cxx) |
| 主 Dispatcher | [`sfx2/source/control/dispatch.cxx`](sfx2/source/control/dispatch.cxx) |
| 命令定义 | [`workdir/XcuMergeTarget/officecfg/registry/data/org/openoffice/Office/UI/GenericCommands.xcu`](workdir/XcuMergeTarget/officecfg/registry/data/org/openoffice/Office/UI/GenericCommands.xcu) |

### 7.2 Save 命令实现

| 功能 | 文件路径 |
|------|---------|
| Save 按钮状态控制 | [`sfx2/source/doc/objserv.cxx:1545`](sfx2/source/doc/objserv.cxx) |
| Save 实现 | [`sfx2/source/doc/objstor.cxx`](sfx2/source/doc/objstor.cxx) |
| LockSave 检查 | [`sfx2/source/doc/objmisc.cxx:2056`](sfx2/source/doc/objmisc.cxx) |
| XStorable 实现 | [`sfx2/source/doc/sfxbasemodel.cxx`](sfx2/source/doc/sfxbasemodel.cxx) |

### 7.3 UNO 接口定义

| 接口 | IDL 文件路径 |
|------|------------|
| XDispatchProviderInterceptor | [`offapi/com/sun/star/frame/XDispatchProviderInterceptor.idl`](offapi/com/sun/star/frame/XDispatchProviderInterceptor.idl) |
| XDispatchProviderInterception | [`offapi/com/sun/star/frame/XDispatchProviderInterception.idl`](offapi/com/sun/star/frame/XDispatchProviderInterception.idl) |
| XInterceptorInfo | [`offapi/com/sun/star/frame/XInterceptorInfo.idl`](offapi/com/sun/star/frame/XInterceptorInfo.idl) |
| XDocumentEventListener | [`offapi/com/sun/star/document/XDocumentEventListener.idl`](offapi/com/sun/star/document/XDocumentEventListener.idl) |
| XDocumentEventBroadcaster | [`offapi/com/sun/star/document/XDocumentEventBroadcaster.idl`](offapi/com/sun/star/document/XDocumentEventBroadcaster.idl) |
| XJobExecutor | [`offapi/com/sun/star/task/XJobExecutor.idl`](offapi/com/sun/star/task/XJobExecutor.idl) |
| XModifyListener | [`offapi/com/sun/star/util/XModifyListener.idl`](offapi/com/sun/star/util/XModifyListener.idl) |
| XStorable | [`offapi/com/sun/star/frame/XStorable.idl`](offapi/com/sun/star/frame/XStorable.idl) |

---

## 总结

### ✅ 可以拦截的内容

| 操作 | 拦截方式 | 推荐方案 |
|------|---------|---------|
| Edit Document 按钮 | ✅ 完全可拦截 | XDispatchProviderInterceptor |
| Save 按钮 | ✅ 完全可拦截 | XDispatchProviderInterceptor |
| SaveAs 按钮 | ✅ 完全可拦截 | XDispatchProviderInterceptor |
| 按钮状态（启用/禁用） | ✅ 可控制 | XStatusListener |
| 保存功能 | ✅ 可阻止 | XStorable + LockSave |

### 最佳实践

1. **优先使用 XDispatchProviderInterceptor**
   - 可以在命令执行前拦截
   - 完全控制命令的执行

2. **实现自定义 XDispatch**
   - 更简单的实现
   - 针对特定命令

3. **结合文档事件监听**
   - 使用 XDocumentEventListener 监听文档变化
   - 提供更全面的控制

### 注意事项

- 拦截器需要在文档加载前注册
- 记得在适当时机注销拦截器
- 拦截逻辑要简洁，避免影响性能
- 处理异常情况，避免导致 LibreOffice 崩溃

---

**文档版本**: 1.0
**最后更新**: 2025-03-05
**作者**: Claude Code 分析报告
