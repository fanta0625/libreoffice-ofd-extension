# OFD 文档可编辑功能实现方案 - 可行性分析报告

**文档类型**: 技术方案与可行性分析
**分析日期**: 2026-03-05
**项目**: LibreOffice OFD UNO Extension - 可编辑功能
**版本**: v1.0

---

## 📋 目录

- [1. 方案概述](#1-方案概述)
- [2. 技术栈分析](#2-技术栈分析)
- [3. 核心流程设计](#3-核心流程设计)
- [4. 可行性分析](#4-可行性分析)
- [5. 技术挑战与风险](#5-技术挑战与风险)
- [6. 实现方案](#6-实现方案)
- [7. LibreOffice 导出功能需求](#7-libreoffice-导出功能需求)
- [8. 架构设计](#8-架构设计)
- [9. 性能与用户体验](#9-性能与用户体验)
- [10. 推荐方案](#10-推荐方案)

---

## 1. 方案概述

### 1.1 当前状态

**OFD Reader (只读模式)**：
```
OFD 文件 → ofdrw (SVGExporter) → SVG → LibreOffice Draw → 只读显示
```

**特点**：
- ✅ 简单高效
- ✅ 完美保留 OFD 格式
- ❌ 无法编辑内容
- ❌ "Edit Document" 按钮被禁用

### 1.2 目标方案 (可编辑模式)

**用户提出的方案**：
```
打开流程:
OFD → ofdrw (PDFExporter) → PDF → LibreOffice Draw → 可编辑

保存流程:
LibreOffice Draw → 导出 PDF → ofdrw (PDFConverter) → OFD
```

**关键变化**：
- 中间格式从 SVG 改为 PDF
- 利用 LibreOffice 强大的 PDF 编辑能力
- 保存时需要反向转换 PDF → OFD

---

## 2. 技术栈分析

### 2.1 OFDRW 库能力评估

#### ✅ 已支持的功能

| 功能 | 类名 | 状态 | 说明 |
|------|------|------|------|
| **OFD → PDF** | `PDFExporterPDFBox` | ✅ 可用 | 基于 Apache PDFBox |
| **OFD → PDF** | `PDFExporterIText` | ⚠️ LTS | 基于 iText，不再更新 |
| **PDF → OFD** | `PDFConverter` | ✅ 可用 | 支持 PDF 转换为 OFD |
| **OFD → SVG** | `SVGExporter` | ✅ 已使用 | 当前方案使用 |

#### 代码示例

**OFD → PDF 导出**：
```java
// ofdrw 支持将 OFD 导出为 PDF
Path ofdPath = Paths.get("/path/to/document.ofd");
Path pdfPath = Paths.get("/tmp/document.pdf");

try (OFDExporter exporter = new PDFExporterPDFBox(ofdPath, pdfPath)) {
    exporter.export();  // 导出所有页面
}
```

**PDF → OFD 导入**：
```java
// ofdrw 支持将 PDF 转换为 OFD
Path pdfPath = Paths.get("/tmp/edited.pdf");
Path ofdPath = Paths.get("/path/to/save.ofd");

try (PDFConverter converter = new PDFConverter(ofdPath)) {
    converter.convert(pdfPath);  // 转换所有页面
}
```

### 2.2 LibreOffice PDF 能力评估

#### ✅ PDF 导入 (draw_pdf_import)

**过滤器名称**: `draw_pdf_import`

**能力**：
- ✅ 导入 PDF 文件到 LibreOffice Draw
- ✅ 保留基本布局
- ✅ 支持编辑文本、图像
- ✅ 支持删除/添加内容
- ⚠️ 复杂布局可能损坏
- ⚠️ 字体可能缺失

**代码示例**：
```java
// 使用 LibreOffice 打开 PDF（可编辑）
PropertyValue[] loadProps = new PropertyValue[3];

// 不设置为只读模式（可编辑）
loadProps[0] = new PropertyValue();
loadProps[0].Name = "ReadOnly";
loadProps[0].Value = Boolean.FALSE;

// 指定 PDF 导入过滤器
loadProps[1] = new PropertyValue();
loadProps[1].Name = "FilterName";
loadProps[1].Value = "draw_pdf_import";

loadProps[2] = new PropertyValue();
loadProps[2].Name = "LockEditDoc";
loadProps[2].Value = Boolean.FALSE;  // 允许编辑

XComponent doc = loader.loadComponentFromURL(
    "file:///tmp/document.pdf",
    "_blank",
    0,
    loadProps
);
```

#### ✅ PDF 导出

**过滤器名称**: `draw_pdf_Export`

**能力**：
- ✅ 将 LibreOffice Draw 导出为 PDF
- ✅ 保留基本布局
- ✅ 保留字体嵌入
- ⚠️ OFD 特性可能丢失

**代码示例**：
```java
// 导出 LibreOffice 文档为 PDF
PropertyValue[] exportProps = new PropertyValue[1];

exportProps[0] = new PropertyValue();
exportProps[0].Name = "FilterName";
exportProps[0].Value = "draw_pdf_Export";

XStorable storable = UnoRuntime.queryInterface(XStorable.class, doc);
storable.storeToURL(
    "file:///tmp/edited.pdf",
    exportProps
);
```

---

## 3. 核心流程设计

### 3.1 完整工作流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户打开 OFD 文件                          │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  第一步：OFD → PDF 转换 (后端)                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  | Path tempPdf = Files.createTempFile("ofd-", ".pdf");    |   │
│  | try (PDFExporter exporter =                              |   │
│  |         new PDFExporterPDFBox(ofdPath, tempPdf)) {       |   │
│  |     exporter.export();                                   |   │
│  | }                                                        |   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  第二步：LibreOffice 打开 PDF (可编辑)                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  | PropertyValue[] props = {                               |   │
│  |   {"FilterName", "draw_pdf_import"},                    |   │
│  |   {"ReadOnly", FALSE},                                  |   │
│  |   {"LockEditDoc", FALSE}                                |   │
│  | };                                                      |   │
│  | doc = loader.loadComponentFromURL(pdfUrl, props);       |   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  第三步：用户编辑 (LibreOffice Draw 环境)                         │
│  - 编辑文本内容                                                   │
│  - 插入/删除图像                                                  │
│  - 调整布局                                                       │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    用户点击"保存"或"另存为"                        │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  第四步：LibreOffice → PDF 导出                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  | PropertyValue[] props = {                               |   │
│  |   {"FilterName", "draw_pdf_Export"}                     |   │
│  | };                                                      |   │
│  | storable.storeToURL(pdfUrl, props);                     |   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  第五步：PDF → OFD 转换 (后端)                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  | try (PDFConverter converter = new PDFConverter(ofdPath)) {|   │
│  |     converter.convert(editedPdfPath);                    |   │
│  | }                                                        |   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    完成！OFD 文件已更新                            │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 "Edit Document" 按钮流程

```
┌──────────────────────┐
│  只读模式打开 OFD      │
│  (OFD → SVG)         │
└──────────┬───────────┘
           ↓
┌──────────────────────┐
│  用户点击             │
│  "Edit Document"     │
└──────────┬───────────┘
           ↓
┌──────────────────────────────────────────────────────────┐
│  显示确认对话框：                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  | "编辑需要将文档转换为 PDF 格式"                      |  │
│  | "• 编辑功能可能受限于 PDF 兼容性"                   |  │
│  | "• 保存时将转换回 OFD 格式"                          |  │
│  | "• 复杂布局可能发生变化"                            |  │
│  |                                                      |  │
│  | [继续编辑]  [取消]                                   |  │
│  └────────────────────────────────────────────────────┘  │
└────────────────────────┬─────────────────────────────────┘
                         ↓ 用户确认
┌──────────────────────────────────────────────────────────┐
│  后台执行转换：                                            │
│  1. OFD → PDF (ofdrw)                                    │
│  2. 关闭当前只读文档                                       │
│  3. 打开 PDF (LibreOffice Draw)                          │
└────────────────────────┬─────────────────────────────────┘
                         ↓
┌──────────────────────┐
│  可编辑模式           │
│  (PDF 在 Draw 中)    │
└──────────────────────┘
```

---

## 4. 可行性分析

### 4.1 技术可行性 ✅

| 环节 | 可行性 | 依据 | 风险等级 |
|------|--------|------|---------|
| OFD → PDF 转换 | ✅ 完全可行 | ofdrw 原生支持 | 🟢 低 |
| PDF 导入 LibreOffice | ✅ 完全可行 | LibreOffice 内置功能 | 🟢 低 |
| PDF 编辑 | ✅ 完全可行 | LibreOffice Draw 支持 | 🟡 中 |
| LibreOffice → PDF 导出 | ✅ 完全可行 | 标准导出功能 | 🟢 低 |
| PDF → OFD 转换 | ✅ 完全可行 | ofdrw 原生支持 | 🟡 中 |

**结论**：✅ **技术上完全可行**

### 4.2 功能可行性分析

#### ✅ 优势

1. **利用 LibreOffice 成熟功能**
   - PDF 编辑能力强大
   - 用户熟悉 LibreOffice 界面
   - 无需重新开发编辑器

2. **转换双向可行**
   - OFD ↔ PDF 双向转换都有库支持
   - 不需要额外开发转换器

3. **开发工作量可控**
   - 主要工作是流程集成
   - 不需要底层格式处理

#### ⚠️ 限制

1. **格式保真度**
   - OFD 特性可能丢失（签名、加密、元数据）
   - 复杂布局可能变化
   - 字体可能缺失

2. **编辑体验**
   - PDF 编辑不如原生 OFD 精细
   - 某些元素可能变为不可编辑的图像

3. **性能开销**
   - 需要两次格式转换（打开 + 保存）
   - 大文件转换时间较长

### 4.3 法律与标准合规性

#### ✅ OFD 标准

- GB/T 33190-2016 是国家标准
- PDF → OFD 转换符合标准要求
- 生成的 OFD 文件合规

#### ⚠️ 电子签名

- ⚠️ PDF 导出会丢失 OFD 电子签名
- ⚠️ 需要明确提示用户
- 建议在保存时重新签名

---

## 5. 技术挑战与风险

### 5.1 格式转换质量 🟡

**问题**：OFD → PDF → OFD 往返转换可能导致质量损失

**具体风险**：
- 字体替换导致显示不一致
- 复杂表格结构可能损坏
- 自定义图形可能转为位图
- OFD 特有属性（如签章域）丢失

**缓解方案**：
```java
// 转换前警告用户
String warning =
    "⚠️ 转换编辑提示\n\n" +
    "• 编辑功能基于 PDF 格式，可能存在精度损失\n" +
    "• 复杂布局、字体、特殊效果可能发生变化\n" +
    "• 电子签名将在保存时失效\n" +
    "• 建议编辑前备份原文件\n\n" +
    "是否继续？";

if (!confirm(warning)) {
    return;  // 用户取消
}
```

### 5.2 临时文件管理 🟢

**问题**：需要创建和管理临时 PDF 文件

**解决方案**：
```java
public class OFDEditingSession {
    private Path ofdPath;          // 原 OFD 文件
    private Path tempPdfPath;      // 临时 PDF
    private Path editedPdfPath;    // 编辑后的 PDF
    private TempFileManager tempManager;

    public void cleanup() {
        // 确保临时文件被清理
        if (tempPdfPath != null) {
            Files.deleteIfExists(tempPdfPath);
        }
        if (editedPdfPath != null) {
            Files.deleteIfExists(editedPdfPath);
        }
    }
}
```

### 5.3 用户体验 🟡

**问题**：用户可能不理解为什么 OFD 要转成 PDF 编辑

**解决方案**：
1. 清晰的用户提示
2. 首次使用时显示教程
3. 提供格式对比说明

```java
// 首次使用提示
if (isFirstTime()) {
    showTutorial(
        "OFD 可编辑功能说明",
        "• OFD 文档将转换为 PDF 格式进行编辑\n" +
        "• 利用 LibreOffice 强大的编辑功能\n" +
        "• 保存时自动转换回 OFD 格式\n" +
        "• 部分复杂元素可能变为图像"
    );
}
```

### 5.4 性能问题 🟢

**问题**：大文件转换可能较慢

**解决方案**：
```java
// 显示进度
ProgressTracker progress = new ProgressTracker();
progress.start("正在转换为 PDF...", 100);

// 后台转换
CompletableFuture<Void> conversionTask = CompletableFuture.runAsync(() -> {
    try (PDFExporter exporter = new PDFExporterPDFBox(ofdPath, pdfPath)) {
        exporter.export();
        progress.setValue(100);
    }
});

// 转换完成后打开文档
conversionTask.thenRun(() -> {
    openPdfInLibreOffice(pdfPath);
});
```

---

## 6. 实现方案

### 6.1 是否需要为 LibreOffice 添加"导出为 OFD"功能？

#### ❌ 方案 A：添加 LibreOffice 原生 OFD 导出过滤器

**实现方式**：
- 编写 C++ LibreOffice 扩展
- 注册新的导出过滤器 `ofd_export`
- 集成到 LibreOffice 导出菜单

**优点**：
- ✅ 用户体验最佳（原生集成）
- ✅ 可以直接调用 LibreOffice 内部 API

**缺点**：
- ❌ 需要编写 C++ 代码
- ❌ 需要深入了解 LibreOffice 内部架构
- ❌ 开发周期长（数周至数月）
- ❌ 维护成本高（LibreOffice 版本更新）
- ❌ 需要重新编译 LibreOffice

**工作量估算**：**3-6 个月**

---

#### ✅ 方案 B：Java UNO 扩展拦截保存操作（推荐）

**实现方式**：
- 在 Java UNO 层面拦截 `.uno:Save` 命令
- 自动触发 PDF → OFD 转换
- 用户无感知

**优点**：
- ✅ 开发快速（1-2 周）
- ✅ 纯 Java 实现
- ✅ 不需要修改 LibreOffice 核心
- ✅ 易于维护

**缺点**：
- ⚠️ 需要 Java 运行时环境
- ⚠️ 保存操作略有延迟

**工作量估算**：**1-2 周**

**实现示例**：
```java
public class OFDEditExportFilter implements XDispatch {

    @Override
    public void dispatch(URL url, PropertyValue[] args) {
        if (".uno:Save".equals(url.Complete)) {
            handleSave();
        }
    }

    private void handleSave() {
        // 1. 导出为 PDF
        Path tempPdf = exportToPDF();

        // 2. 转换 PDF → OFD
        Path ofdPath = getOriginalOFDPath();
        try (PDFConverter converter = new PDFConverter(ofdPath)) {
            converter.convert(tempPdf);
        }

        // 3. 显示成功提示
        showSuccessMessage("OFD 文件已保存");

        // 4. 清理临时文件
        Files.deleteIfExists(tempPdf);
    }
}
```

---

### 6.2 推荐实现方案：混合方案

**打开阶段**：
```
OFD → PDF (ofdrw) → LibreOffice 打开
```

**编辑阶段**：
```
LibreOffice Draw 正常编辑 PDF
```

**保存阶段**（两个选项）：

**选项 1 - 自动保存**（推荐）：
```
用户点击 Ctrl+S → Java 拦截 → 导出 PDF → 转换 OFD → 覆盖原文件
```

**选项 2 - 另存为对话框**：
```
用户点击"文件 → 另存为" → 选择格式（OFD/PDF） → 执行转换
```

---

## 7. LibreOffice 导出功能需求

### 7.1 不需要添加原生 OFD 导出器 ❌

**理由**：

1. **LibreOffice 已有 PDF 导出**
   - `draw_pdf_Export` 过滤器成熟稳定
   - 无需重新开发

2. **Java 层可以实现 OFD 转换**
   - 通过 UNO 拦截保存操作
   - 后台调用 ofdrw 转换
   - 用户无感知

3. **开发成本对比**
   | 方案 | 开发时间 | 维护成本 | 用户体验 |
   |------|---------|---------|---------|
   | C++ 原生导出 | 3-6月 | 高 | ⭐⭐⭐⭐⭐ |
   | Java 拦截保存 | 1-2周 | 低 | ⭐⭐⭐⭐ |

### 7.2 需要实现的功能清单

#### 7.2.1 必需功能（P0）

| 功能 | 实现方式 | 优先级 |
|------|---------|--------|
| OFD → PDF 转换 | ofdrw `PDFExporter` | P0 |
| PDF 导入 | LibreOffice 内置 | P0 |
| PDF → OFD 转换 | ofdrw `PDFConverter` | P0 |
| 保存拦截 | Java UNO `XDispatch` | P0 |

#### 7.2.2 重要功能（P1）

| 功能 | 实现方式 | 优先级 |
|------|---------|--------|
| "Edit Document" 按钮集成 | 修改按钮逻辑 | P1 |
| 转换进度提示 | `ProgressTracker` | P1 |
| 格式警告对话框 | `MessageBox` | P1 |
| 临时文件清理 | `TempFileManager` | P1 |

#### 7.2.3 可选功能（P2）

| 功能 | 实现方式 | 优先级 |
|------|---------|--------|
| 自动备份原文件 | 文件复制 | P2 |
| 编辑历史记录 | 日志系统 | P2 |
| 格式对比预览 | 并排显示 | P2 |

---

## 8. 架构设计

### 8.1 新增类设计

```java
// 核心编辑管理器
public class OFDEditingManager {
    private Path originalOfdPath;
    private Path tempPdfPath;
    private Path editedPdfPath;
    private OFDEditingSession session;

    /**
     * 启动可编辑模式
     */
    public XComponent startEditing(Path ofdPath, XComponentContext context);

    /**
     * 保存编辑（PDF → OFD）
     */
    public boolean saveEditing(XComponent pdfDocument);

    /**
     * 取消编辑
     */
    public void cancelEditing();
}

// 编辑会话管理
public class OFDEditingSession {
    private String sessionId;
    private long createdAt;
    private Path ofdPath;
    private Path pdfPath;
    private boolean isDirty;

    public void markDirty();
    public void cleanup();
}

// PDF 转换器
public class OFDToPDFConverter {
    public Path convert(Path ofdPath) throws IOException;
}

// OFD 转换器
public class PDFToOFDConverter {
    public Path convert(Path pdfPath, Path targetOfdPath) throws IOException;
}

// UNO 保存拦截器
public class OFDSaveInterceptor implements XDispatch {
    @Override
    public void dispatch(URL url, PropertyValue[] args);

    private boolean handleSaveAsOFD();
    private boolean handleSaveAsPDF();
}
```

### 8.2 修改现有类

```java
// ReadOnlyConfig.java - 添加编辑模式配置
public class ReadOnlyConfig {
    // 新增：允许通过"Edit Document"切换到编辑模式
    private static final boolean ALLOW_EDIT_MODE = true;

    // 新增：编辑模式使用 PDF 而非 SVG
    private static final String EDIT_MODE_FORMAT = "PDF";
}

// OFDImportFilter.java - 修改 filter() 方法
public boolean filter(PropertyValue[] aDescriptor) {
    // 检测是否请求编辑模式
    boolean requestEditMode = detectEditModeRequest(aDescriptor);

    if (requestEditMode) {
        return ofdEditingManager.startEditing(ofdPath, context);
    } else {
        // 原有的只读 SVG 导入逻辑
        return svgConverter.convertAndInsert(ofdPath, ...);
    }
}
```

### 8.3 配置文件

**新增配置文件**：`src/main/resources/ofd-editing.properties`

```properties
# OFD 编辑功能配置

# 是否启用编辑功能
editing.enabled=true

# 编辑模式使用的中间格式 (PDF | SVG)
editing.intermediate.format=PDF

# 是否自动备份原文件
editing.auto.backup=true

# 临时文件目录
editing.temp.dir=${java.io.tmpdir}/ofd-editing/

# 是否在首次使用时显示教程
editing.show.tutorial=true

# 转换超时时间（毫秒）
editing.conversion.timeout=30000
```

---

## 9. 性能与用户体验

### 9.1 性能分析

| 操作 | 预估时间 | 优化方案 |
|------|---------|---------|
| OFD → PDF 转换 | 2-5秒 | 后台转换 + 进度提示 |
| PDF 导入 | 1-3秒 | LibreOffice 内置优化 |
| 用户编辑 | - | 即时响应 |
| 导出 PDF | 1-2秒 | LibreOffice 内置优化 |
| PDF → OFD 转换 | 2-5秒 | 后台转换 + 进度提示 |
| **总计** | **6-15秒** | 异步处理 |

### 9.2 用户体验优化

#### 优化 1：首次转换提示

```java
String message =
    "⏳ 正在转换为可编辑格式...\n\n" +
    "• OFD 文档将转换为 PDF 格式\n" +
    "• 转换时间取决于文件大小和复杂度\n" +
    "• 请稍候片刻...";

showProgressDialog(message, () -> {
    convertOFDToPDF(ofdPath);
});
```

#### 优化 2：后台转换

```java
CompletableFuture<Path> conversionTask = CompletableFuture.supplyAsync(() -> {
    return convertOFDToPDFInBackground(ofdPath);
});

conversionTask.thenAccept(pdfPath -> {
    SwingUtilities.invokeLater(() -> {
        openPDFInLibreOffice(pdfPath);
    });
});
```

#### 优化 3：缓存转换结果

```java
// 如果同一个 OFD 文件被多次打开，缓存 PDF
private static final Map<Path, Path> pdfCache = new LRUCache<>(10);

Path getOrCreatePDF(Path ofdPath) {
    return pdfCache.computeIfAbsent(ofdPath, path -> {
        return convertOFDToPDF(path);
    });
}
```

---

## 10. 推荐方案

### 10.1 总体结论

✅ **方案完全可行**，推荐实施！

**关键优势**：
1. ✅ 技术成熟（所有依赖都存在）
2. ✅ 开发快速（1-2 周）
3. ✅ 用户体验良好（利用现有 LibreOffice 功能）
4. ✅ 维护成本低（纯 Java 实现）

### 10.2 实施路线图

#### 阶段 1：核心功能（1 周）

**目标**：实现基本的编辑功能

- [ ] OFD → PDF 转换
- [ ] PDF 在 LibreOffice 中打开
- [ ] "Edit Document" 按钮集成
- [ ] 保存时 PDF → OFD 转换

#### 阶段 2：用户体验优化（3-5 天）

**目标**：提升用户使用体验

- [ ] 转换进度提示
- [ ] 格式警告对话框
- [ ] 临时文件管理
- [ ] 错误处理

#### 阶段 3：高级功能（可选，3-5 天）

**目标**：完善功能细节

- [ ] 自动备份原文件
- [ ] 编辑历史记录
- [ ] 格式对比预览
- [ ] 性能优化

### 10.3 风险缓解措施

| 风险 | 缓解措施 |
|------|---------|
| 格式转换质量损失 | 明确提示用户 + 提供预览 |
| 性能问题 | 后台转换 + 进度提示 + 缓存 |
| 用户不理解 | 首次使用教程 + 帮助文档 |
| 电子签名丢失 | 保存前警告 + 建议重新签名 |
| 临时文件泄漏 | try-with-resources + finally 清理 |

### 10.4 不推荐：添加原生 LibreOffice OFD 导出器

**理由**：
1. ❌ 开发成本太高（3-6 个月 vs 1-2 周）
2. ❌ 维护成本高（需要跟随 LibreOffice 版本更新）
3. ❌ 功能重复（PDF 已足够好）
4. ❌ 收益不明显（用户体验提升有限）

**替代方案**：
- ✅ 使用 Java UNO 拦截保存操作
- ✅ 后台调用 ofdrw 转换
- ✅ 用户无感知

---

## 11. 下一步行动

### 11.1 技术验证（1 天）

在正式开发前，建议先进行技术验证：

```java
// 验证 OFD → PDF 转换
Path ofdPath = Paths.get("/path/to/test.ofd");
Path pdfPath = Paths.get("/tmp/test.pdf");

try (PDFExporter exporter = new PDFExporterPDFBox(ofdPath, pdfPath)) {
    exporter.export();
}
// 检查生成的 PDF 质量如何

// 验证 PDF → OFD 转换
try (PDFConverter converter = new PDFConverter(ofdPath2)) {
    converter.convert(pdfPath);
}
// 检查往返转换的质量损失
```

### 11.2 原型开发（2-3 天）

开发一个最小可行原型（MVP）：

1. 实现 OFD → PDF 转换
2. 在 LibreOffice 中打开转换后的 PDF
3. 实现保存时的 PDF → OFD 转换
4. 测试完整的编辑流程

### 11.3 正式开发（1-2 周）

基于原型，完善功能细节和用户体验。

---

## 附录

### A. 相关文档

- [LibreOffice 只读模式实现分析](LibreOffice_ReadOnly_Mode_Implementation.md)
- [OFDRW 转换文档](OFDRW 转换为OFD.md)
- [OFDRW 导出文档](ofdrw.md)
- [项目 CLAUDE 文档](../CLAUDE.md)

### B. 代码仓库

- LibreOffice: https://github.com/LibreOffice/core
- OFDRW: https://github.com/Trisia/ofdrw
- 本项目: [当前路径]

### C. 关键 API

- ofdrw PDF 导出: `org.ofdrw.converter.export.PDFExporterPDFBox`
- ofdrw PDF 导入: `org.ofdrw.converter.ofdconverter.PDFConverter`
- LibreOffice PDF 导入: `draw_pdf_import`
- LibreOffice PDF 导出: `draw_pdf_Export`

---

**文档版本**: 1.0
**最后更新**: 2026-03-05
**作者**: Claude Code
**审核状态**: 待审核
