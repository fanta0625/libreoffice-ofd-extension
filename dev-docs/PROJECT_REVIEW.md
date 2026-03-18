# LibreOffice OFD UNO 扩展项目审查报告

## LibreOffice 生命周期

┌─────────────────────────────────────────────────────────────┐
│  Desktop (service)                                          │
│  ├── XDesktop                                               │
│  ├── XComponentLoader                │
│  └── XEventBroadcaster                                      │
└─────────────────┬───────────────────────────────────────────┘
                │ loadComponentFromURL(…, PropertyValue[] aArgs)
                ▼
┌─────────────────────────────────────────────────────────────┐
│  Frame (service)                                            │
│  ├── XFrame                                                 │
│  ├── XDispatchProvider  ← 可在此拦截编辑命令                │
│  └── XFramesSupplier                                        │
└─────────────────┬───────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│  Controller (service)                                       │
│  ├── XController                                            │
│  └── XDispatchProvider                                      │
└─────────────────┬───────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│  Model (service) → XModel                                   │
└─────────────────┬───────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│  OfficeDocument (service)                                   │
│  ├── XStorable       ← 只能读取 isReadonly()，无法设置      │
│  ├── XModifiable     ← 只能设置 Modified 标志，无法真正只读  │
│  └── XPropertySet                                           │
└─────────────────────────────────────────────────────────────┘

## LibreOffice 调用 UNO 插件的流程

```
┌───────────────────────────────────────┐
│                        LibreOffice 启动扩展流程                              │
├───────────────────────────────────────┤
│  1. LibreOffice 启动                                                         │
│       ↓                                                                     │
│  2. 扫描扩展目录，加载 *.oxt 文件                                            │
│       ↓                                                                     │
│  3. 读取 META-INF/manifest.xml → 知道要加载哪些配置文件                     │
│       ↓                                                                     │
│  4. 解析 Types.xcu → 注册 "ofd_OpenFormDocument" 文件类型                   │
│       ↓                                                                     │
│  5. 解析 Filter.xcu → 注册 "OFD_Import_Filter" 过滤器                       │
│       ↓                                                                     │
│  6. 解析 OFDImportFilter.components → 注册 Java 组件                        │
│       ↓                                                                     │
│  7. 用户打开 *.ofd 文件                                                      │
│       ↓                                                                     │
│  8. 类型检测：调用 XExtendedFilterDetection.detect()                         │
│       ↓                                                                     │
│  9. 调用 XImporter.setTargetDocument() 设置目标文档                          │
│       ↓                                                                     │
│  10. 调用 XFilter.filter() 执行实际导入                                      │
└───────────────────────────────────────┘
```

---

## 各文件作用详解

| 文件 | 作用 |
|------|------|
| **OFDImportFilter.java** | 核心 Java 类，实现 `XImporter`、`XFilter`、`XExtendedFilterDetection` 等接口，负责 OFD→SVG 转换并导入到 Draw 文档 |
| **OfdToSvgLauncher.java** | 简单的启动器类，仅用于打包 JAR 的入口点 |
| **OFDImportFilter.components** | 现代 UNO 组件注册文件（LibreOffice 4.0+），声明 Java 类、服务名和加载器 |
| **Components.xcu** | 旧版组件注册方式，用于向后兼容 |
| **Types.xcu** | 定义文件类型 "ofd_OpenFormDocument"，包括扩展名 `.ofd`、MIME 类型 `application/ofd` |
| **Filter.xcu** | 定义导入过滤器，关联类型和实现类，指定目标文档类型为 Draw |
| **Addons.xcu** | 添加菜单项/工具栏按钮（目前只添加了帮助链接） |
| **description.xml** | 扩展的元数据（ID、版本、名称、发布者、许可证），在扩展管理器中显示 |
| **manifest.xml** | OXT 包的清单文件，列出扩展内所有需要注册的文件 |
| **Messages*.properties** | 国际化资源文件，支持中英文消息 |
| **simplelogger.properties** | SLF4J 日志配置 |

---

## 核心接口说明

```java
// OFDImportFilter 实现的关键接口
XImporter                → setTargetDocument()  设置目标文档
XFilter                  → filter()             执行导入操作
XExtendedFilterDetection → detect()             检测文件类型
XInitialization          → initialize()         初始化
XNamed                   → getName()            获取过滤器名称
XServiceInfo             → getImplementationName() 等服务信息
```

---

## 官方文档链接

| 文档类型 | 链接 |
|----------|------|
| **description.xml** | https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions#description.xml |
| **Types.xcu / Filter.xcu** | https://wiki.documentfoundation.org/Documentation/DevGuide/Office_Development#Type_Detection_and_File_Format_Definition |
| **manifest.xml** | https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions#manifest.xml |
| **UNO 组件开发指南** | https://wiki.documentfoundation.org/Documentation/DevGuide/Uno |
| **Java UNO 组件** | https://wiki.documentfoundation.org/Documentation/DevGuide/WritingUNO/Java |
| **.components 文件格式** | https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions#Component_Registration |
| **XFilter 接口** | https://api.libreoffice.org/docs/idl/ref/interfacecom_1_1sun_1_1star_1_1document_1_1XFilter.html |
| **XExtendedFilterDetection** | https://api.libreoffice.org/docs/idl/ref/interfacecom_1_1sun_1_1star_1_1document_1_1XExtendedFilterDetection.html |
| **OXT 扩展结构** | https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions |
| **LibreOffice 类型检测**|https://wiki.documentfoundation.org/Documentation/DevGuide/Office_Development#Type_Detection|
| **FreeDesktop.org .desktop 规范** | https://specifications.freedesktop.org/desktop-entry-spec/latest/ |
| **Linux MIME 类型关联** | https://specifications.freedesktop.org/shared-mime-info-spec/latest/ |
| **Arch Wiki: Desktop entries** | https://wiki.archlinux.org/title/Desktop_entries |

---

## 项目架构总结

```
用户打开 .ofd 文件
        ↓
LibreOffice 类型检测系统（Types.xcu 定义的 DetectService）
        ↓
调用 OFDImportFilter.detect() 检测文件类型
        ↓
返回 "ofd_OpenFormDocument" 类型名
        ↓
LibreOffice 查找对应的 Filter（Filter.xcu 定义的 FilterService）
        ↓
调用 OFDImportFilter.filter()
        ↓
OFD → SVG 转换（使用 ofdrw 库）
        ↓
创建 GraphicObjectShape 插入 Draw 文档
        ↓
导入完成
```

---

## 项目文件结构

```
src/main/
├── java/org/ofdrw/uno/
│   ├── OFDImportFilter.java      # 核心导入过滤器实现
│   └── OfdToSvgLauncher.java     # JAR 启动入口
├── oxt/
│   └── OFDImportFilter.components # 现代 UNO 组件注册
└── resources/
    ├── Addons.xcu                 # 菜单/工具栏扩展
    ├── Components.xcu             # 旧版组件注册（兼容）
    ├── Filter.xcu                 # 导入过滤器定义
    ├── Types.xcu                  # 文件类型定义
    ├── META-INF/manifest.xml      # OXT 清单
    ├── description/description.xml # 扩展元数据
    ├── i18n/
    │   ├── Messages.properties        # 英文资源
    │   └── Messages_zh_CN.properties  # 中文资源
    └── simplelogger.properties    # 日志配置
```
