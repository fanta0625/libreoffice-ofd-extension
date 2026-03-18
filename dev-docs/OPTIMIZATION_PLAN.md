# LibreOffice OFD UNO Extension - 优化方案

**文档版本**：v1.0
**创建日期**：2026-03-05
**项目状态**：开发中
**当前质量评分**：7.5/10（良好）
**目标质量评分**：8.5/10（优秀）

---

## 📋 目录

- [1. 概述](#1-概述)
- [2. 优先级分类](#2-优先级分类)
- [3. 详细优化方案](#3-详细优化方案)
- [4. 改进记录](#4-改进记录)
- [5. 验收标准](#5-验收标准)

---

## 1. 概述

### 1.1 项目现状

基于全面代码审查，项目在以下方面表现良好：
- ✅ 清晰的架构设计和职责分离
- ✅ 完善的资源管理
- ✅ 智能字体映射机制
- ✅ 良好的国际化支持

但在以下方面需要改进：
- 🔴 安全性：路径遍历漏洞、缺少文件大小限制
- 🟡 测试：0% 测试覆盖率
- 🟡 性能：缺少缓存机制、重复初始化
- 🟡 代码质量：过长方法、重复代码

### 1.2 改进目标

| 维度 | 当前评分 | 目标评分 | 提升幅度 |
|------|---------|---------|---------|
| 安全性 | 5/10 | 8/10 | +3 |
| 测试覆盖 | 2/10 | 7/10 | +5 |
| 性能 | 6/10 | 8/10 | +2 |
| 代码质量 | 8.1/10 | 9/10 | +0.9 |
| **总体** | **7.5/10** | **8.5/10** | **+1** |

---

## 2. 优先级分类

### 🔴 P0 - 严重（必须立即修复）

**风险**：安全漏洞、资源泄漏、系统崩溃
**工作量**：总计约 1-2 小时
**时间线**：第 1 周

| ID | 问题 | 影响 | 工作量 | 负责人 | 状态 |
|----|------|------|--------|--------|------|
| P0-1 | OFDImportFilter finally 块缺陷 | 临时文件泄漏 | 5分钟 | - | ⏳ 待开始 |
| P0-2 | 缺少文件大小限制 | DoS 攻击风险 | 10分钟 | - | ⏳ 待开始 |
| P0-3 | 路径遍历漏洞 | 任意文件访问 | 15分钟 | - | ⏳ 待开始 |
| P0-4 | 缺少 OFD 格式验证 | 接受恶意文件 | 10分钟 | - | ⏳ 待开始 |
| P0-5 | ProgressTracker.complete() 缺少异常处理 | 状态不一致 | 5分钟 | - | ⏳ 待开始 |

### 🟡 P1 - 重要（建议尽快修复）

**影响**：性能、可维护性、可测试性
**工作量**：总计约 3-4 小时
**时间线**：第 2-3 周

| ID | 问题 | 影响 | 工作量 | 负责人 | 状态 |
|----|------|------|--------|--------|------|
| P1-1 | 拆分 OFDImportFilter.filter() 方法 | 可维护性 | 30分钟 | - | ⏳ 待开始 |
| P1-2 | 拆分 FontChecker.checkFonts() 方法 | 可测试性 | 30分钟 | - | ⏳ 待开始 |
| P1-3 | 拆分 MessageBox.getDesktopWindowPeer() 方法 | 可读性 | 20分钟 | - | ⏳ 待开始 |
| P1-4 | 消除 XWindowPeer 获取重复代码 | DRY 原则 | 30分钟 | - | ⏳ 待开始 |
| P1-5 | 消除日志写入重复代码 | DRY 原则 | 20分钟 | - | ⏳ 待开始 |
| P1-6 | 添加字体检测缓存 | 性能优化 | 1小时 | - | ⏳ 待开始 |
| P1-7 | 添加配置文件缓存 | 性能优化 | 30分钟 | - | ⏳ 待开始 |

### 🟢 P2 - 一般（可选改进）

**影响**：长期维护性、开发体验
**工作量**：总计约 4-6 小时
**时间线**：第 4 周

| ID | 问题 | 影响 | 工作量 | 负责人 | 状态 |
|----|------|------|--------|--------|------|
| P2-1 | 添加核心功能单元测试 | 测试覆盖率 | 2小时 | - | ⏳ 待开始 |
| P2-2 | 实现多页文档并行处理 | 性能优化 | 2小时 | - | ⏳ 待开始 |
| P2-3 | 添加处理超时机制 | 安全性 | 1小时 | - | ⏳ 待开始 |
| P2-4 | 创建架构设计文档 | 文档完整性 | 1小时 | - | ⏳ 待开始 |
| P2-5 | 创建 API 使用文档 | 文档完整性 | 1小时 | - | ⏳ 待开始 |

---

## 3. 详细优化方案

### 🔴 P0-1: 修复 OFDImportFilter finally 块缺陷

**问题描述**：
当前 finally 块中，如果 `progressTracker.complete()` 抛出异常，`tempFileManager.cleanup()` 不会被执行，导致临时文件泄漏。

**位置**：`OFDImportFilter.java:216-227`

**当前代码**：
```java
} finally {
    if (progressTracker != null) {
        progressTracker.complete();
    }
    if (tempFileManager != null) {
        tempFileManager.cleanup();
    }
}
```

**修复方案**：
```java
} finally {
    // 进度跟踪器清理（独立 try-catch）
    try {
        if (progressTracker != null) {
            progressTracker.complete();
        }
    } catch (Throwable t) {
        logger.trace("Failed to complete progress tracker", t);
    }

    // 临时文件清理（独立 try-catch）
    try {
        if (tempFileManager != null) {
            tempFileManager.cleanup();
        }
    } catch (Throwable t) {
        logger.error("Failed to cleanup temp directory", t);
    }
}
```

**验收标准**：
- ✅ 即使 progressTracker.complete() 抛异常，tempFileManager.cleanup() 仍会执行
- ✅ 临时文件在所有情况下都被正确清理
- ✅ 异常被正确记录到日志

**测试方法**：
1. 模拟 progressTracker.complete() 抛异常
2. 验证临时文件仍被删除

---

### 🔴 P0-2: 添加文件大小限制

**问题描述**：
当前代码对 OFD 文件大小没有任何限制，攻击者可以上传超大文件导致内存耗尽或磁盘空间不足（DoS 攻击）。

**位置**：`OFDImportFilter.java:142-147`

**修复方案**：

**步骤 1**：在 OFDImportFilter 类中添加常量
```java
// OFDImportFilter.java
private static final long MAX_OFD_SIZE = 100 * 1024 * 1024; // 100MB
private static final int MAX_PAGES = 1000;
```

**步骤 2**：在 filter() 方法中添加验证
```java
// OFDImportFilter.java
Path ofdPath = UrlResolver.urlToPath(url);
if (!Files.exists(ofdPath)) {
    log("OFD file not found at: " + ofdPath);
    return false;
}

// 新增：文件大小验证
try {
    long fileSize = Files.size(ofdPath);
    if (fileSize > MAX_OFD_SIZE) {
        log("OFD file too large: " + fileSize + " bytes (max: " + MAX_OFD_SIZE + ")");
        MessageBox.showError(context, documentHandler.getTargetDocument(),
            I18nMessages.getMessage("error.file.too.large.title"),
            I18nMessages.getMessage("error.file.too.large.message",
                String.valueOf(MAX_OFD_SIZE / 1024 / 1024)));
        return false;
    }
    log("OFD file size: " + fileSize + " bytes");
} catch (IOException e) {
    log("Failed to get file size: " + e.getMessage());
    return false;
}
```

**步骤 3**：添加国际化消息
```properties
# src/main/resources/i18n/Messages_zh_CN.properties
error.file.too.large.title=文件过大
error.file.too.large.message=OFD 文件大小超过限制（最大 {0} MB），无法打开。

# src/main/resources/i18n/Messages.properties
error.file.too.large.title=File Too Large
error.file.too.large.message=The OFD file size exceeds the limit (maximum {0} MB) and cannot be opened.
```

**验收标准**：
- ✅ 超过 100MB 的文件被拒绝
- ✅ 用户看到友好的错误提示
- ✅ 文件大小记录到日志

**测试方法**：
1. 创建 >100MB 的测试文件
2. 尝试打开，验证被拒绝
3. 检查日志记录

---

### 🔴 P0-3: 修复路径遍历漏洞

**问题描述**：
`UrlResolver.urlToPath()` 方法缺少路径验证，攻击者可以使用 `../../` 访问任意文件。

**位置**：`UrlResolver.java:89-105`

**修复方案**：

**步骤 1**：添加路径验证方法
```java
// UrlResolver.java
private static void validatePathSafety(Path path, String originalUrl) {
    try {
        // 规范化路径（解析 ../ 和符号链接）
        Path realPath = path.toRealPath();

        // 检查是否包含父目录引用
        String pathStr = path.toString();
        if (pathStr.contains("../") || pathStr.contains("..\\")) {
            throw new SecurityException("Path traversal detected: " + originalUrl);
        }

        // 检查规范化后的路径是否在允许的范围内
        // 这里可以根据实际需求添加更多验证
        logger.debug("Path validated: {}", realPath);
    } catch (IOException e) {
        throw new SecurityException("Invalid path: " + originalUrl, e);
    }
}
```

**步骤 2**：在 urlToPath() 中调用验证
```java
// UrlResolver.java
public static Path urlToPath(String url) {
    String normalizedUrl = normalizeUrl(url);

    if (normalizedUrl == null || normalizedUrl.isEmpty()) {
        throw new IllegalArgumentException("Invalid URL: " + url);
    }

    Path path = Paths.get(normalizedUrl);

    // 新增：路径安全验证
    validatePathSafety(path, url);

    return path;
}
```

**验收标准**：
- ✅ 包含 `../../` 的 URL 被拒绝
- ✅ 抛出 SecurityException
- ✅ 攻击被记录到日志

**测试方法**：
```java
// 测试用例
@Test(expected = SecurityException.class)
public void testPathTraversalAttack1() {
    UrlResolver.urlToPath("file:///../../etc/passwd");
}

@Test(expected = SecurityException.class)
public void testPathTraversalAttack2() {
    UrlResolver.urlToPath("file://~/../sensitive.txt");
}
```

---

### 🔴 P0-4: 添加 OFD 格式验证

**问题描述**：
当前代码只检查文件扩展名，不验证实际文件格式。攻击者可以重命名任意文件为 `.ofd`。

**位置**：`OFDImportFilter.java:142-147`

**修复方案**：

**步骤 1**：添加格式验证方法
```java
// OFDImportFilter.java
private boolean validateOfdFormat(Path ofdPath) {
    try (InputStream is = Files.newInputStream(ofdPath)) {
        byte[] magic = new byte[4];
        int read = is.read(magic);

        if (read != 4) {
            log("Invalid OFD file: unable to read magic number");
            return false;
        }

        // OFD 文件是 ZIP 格式，魔数应该是 PK (0x50, 0x4B)
        if (magic[0] != 0x50 || magic[1] != 0x4B) {
            log("Invalid OFD file: wrong magic number " +
                String.format("0x%02X 0x%02X", magic[0], magic[1]));
            return false;
        }

        log("OFD format validated: ZIP magic number confirmed");
        return true;
    } catch (IOException e) {
        log("Failed to validate OFD format: " + e.getMessage());
        return false;
    }
}
```

**步骤 2**：在 filter() 方法中调用验证
```java
// OFDImportFilter.java
Path ofdPath = UrlResolver.urlToPath(url);
if (!Files.exists(ofdPath)) {
    log("OFD file not found at: " + ofdPath);
    return false;
}

// 新增：文件大小验证（P0-2）
// ...

// 新增：格式验证
if (!validateOfdFormat(ofdPath)) {
    MessageBox.showError(context, documentHandler.getTargetDocument(),
        I18nMessages.getMessage("error.invalid.format.title"),
        I18nMessages.getMessage("error.invalid.format.message"));
    return false;
}
```

**步骤 3**：添加国际化消息
```properties
# src/main/resources/i18n/Messages_zh_CN.properties
error.invalid.format.title=文件格式错误
error.invalid.format.message=这不是有效的 OFD 文件，文件格式无法识别。

# src/main/resources/i18n/Messages.properties
error.invalid.format.title=Invalid File Format
error.invalid.format.message=This is not a valid OFD file. The file format cannot be recognized.
```

**验收标准**：
- ✅ 非 ZIP 格式文件被拒绝
- ✅ 用户看到友好的错误提示
- ✅ 魔数验证正确

**测试方法**：
1. 创建文本文件，重命名为 `.ofd`
2. 尝试打开，验证被拒绝
3. 创建有效的 OFD 文件，验证通过

---

### 🔴 P0-5: 修复 ProgressTracker.complete() 异常处理

**问题描述**：
如果 `indicator.end()` 抛出异常，`started` 标志不会重置，导致状态不一致。

**位置**：`ProgressTracker.java:73-79`

**当前代码**：
```java
public void complete() {
    if (indicator != null && started) {
        indicator.end();
        started = false;
    }
}
```

**修复方案**：
```java
public void complete() {
    if (indicator != null && started) {
        try {
            indicator.end();
            logger.debug("Progress completed successfully");
        } catch (Throwable t) {
            logger.warn("Failed to end progress indicator: {}", t.getMessage());
            logger.debug("Progress indicator failure details", t);
        } finally {
            // 确保状态重置
            started = false;
        }
    }
}
```

**验收标准**：
- ✅ 即使 indicator.end() 抛异常，started 也会重置
- ✅ 异常被记录到日志
- ✅ 可以安全地多次调用 complete()

---

### 🟡 P1-1: 拆分 OFDImportFilter.filter() 方法

**问题描述**：
`filter()` 方法长达 118 行，圈复杂度约 15，职责过多，难以维护和测试。

**位置**：`OFDImportFilter.java:111-228`

**重构方案**：

**步骤 1**：提取准备逻辑
```java
/**
 * 准备转换上下文
 * @return 转换上下文，如果准备失败则返回 null
 */
private ConversionContext prepareConversion(PropertyValue[] aDescriptor) {
    ProgressTracker progressTracker = new ProgressTracker(aDescriptor, documentHandler.getTargetDocument());
    progressTracker.start(I18nMessages.getMessage("progress.converting"), 100);

    String url = UrlResolver.extractUrl(aDescriptor);
    if (url.isEmpty()) {
        log("findUrl: no URL property found");
        progressTracker.complete();
        return null;
    }

    if (!documentHandler.ensureTargetDocument()) {
        log("targetDocument is still null after creation attempt");
        progressTracker.complete();
        return null;
    }

    Path ofdPath = UrlResolver.urlToPath(url);
    if (ofdPath == null || !Files.exists(ofdPath)) {
        log("OFD file not found at: " + ofdPath);
        progressTracker.complete();
        return null;
    }

    TempFileManager tempFileManager = new TempFileManager();
    return new ConversionContext(url, ofdPath, tempFileManager, progressTracker);
}
```

**步骤 2**：提取字体检查逻辑
```java
/**
 * 检查字体可用性
 * @return true 如果字体检查通过或用户选择继续
 */
private boolean checkFontAvailability(ConversionContext context) {
    progressTracker.setValue(25);
    progressTracker.setText(I18nMessages.getMessage("progress.checking.fonts"));

    FontCheckResult fontCheckResult = FontChecker.checkFonts(context.ofdPath.toString());

    boolean hasGenericFont = fontCheckResult.getAvailableFonts().stream()
            .anyMatch(FontInfo::isGenericFont);
    boolean hasFallbackMapped = fontCheckResult.getAvailableFonts().stream()
            .anyMatch(FontInfo::isFallbackMapped);

    if (!fontCheckResult.hasMissingFonts() && !hasGenericFont && !hasFallbackMapped) {
        log(I18nMessages.getMessage("log.font.check.ok"));
        return true;
    }

    String title = FontWarningDialog.buildCaption(fontCheckResult);
    if (title == null) {
        return true;
    }

    String message = FontWarningDialog.buildWarningMessage(fontCheckResult);
    short userChoice = MessageBox.showWarningConfirm(context,
        documentHandler.getTargetDocument(), title, message);

    return (userChoice == 0 || userChoice == 2);
}
```

**步骤 3**：提取转换逻辑
```java
/**
 * 执行 OFD 到 SVG 的转换
 * @return true 如果转换成功
 */
private boolean performConversion(ConversionContext context) {
    progressTracker.setValue(30);
    progressTracker.setText(I18nMessages.getMessage("progress.converting"));

    SvgConverter converter = new SvgConverter(context, documentHandler);
    boolean success = converter.convertAndInsert(
        context.ofdPath,
        context.tempFileManager,
        progressTracker
    );

    if (success) {
        progressTracker.setValue(100);
        log(I18nMessages.getMessage("log.conversion.success"));
    } else {
        log(I18nMessages.getMessage("log.conversion.failed"));
    }

    return success;
}
```

**步骤 4**：简化 filter() 方法
```java
@Override
public boolean filter(PropertyValue[] aDescriptor) {
    ConversionContext context = prepareConversion(aDescriptor);
    if (context == null) return false;

    try {
        // 字体检查
        if (!checkFontAvailability(context)) {
            return false;
        }

        // 执行转换
        return performConversion(context);

    } catch (Exception e) {
        logException(e);
        return false;
    } finally {
        try {
            if (progressTracker != null) {
                progressTracker.complete();
            }
        } catch (Throwable t) {
            logger.trace("Failed to complete progress tracker", t);
        }

        try {
            if (tempFileManager != null) {
                tempFileManager.cleanup();
            }
        } catch (Throwable t) {
            logger.error("Failed to cleanup temp directory", t);
        }
    }
}
```

**验收标准**：
- ✅ filter() 方法减少到 <50 行
- ✅ 圈复杂度降低到 <10
- ✅ 每个子方法职责单一
- ✅ 逻辑更清晰易懂

---

### 🟡 P1-6: 添加字体检测缓存

**问题描述**：
`FontMappingConfig.initialize()` 在每次转换时被调用2次，每次开销 10-20ms，系统字体不会频繁变化。

**位置**：`FontChecker.java:209`, `SvgConverter.java:71`

**修复方案**：

**步骤 1**：添加缓存类
```java
// FontMappingConfig.java
private static final class FontCache {
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟
    private long lastUpdateTime = 0;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    synchronized boolean isExpired() {
        return System.currentTimeMillis() - lastUpdateTime > CACHE_TTL;
    }

    synchronized void update(Map<String, String> newMappings) {
        cache.clear();
        cache.putAll(newMappings);
        lastUpdateTime = System.currentTimeMillis();
        logger.debug("Font cache updated with {} entries", cache.size());
    }

    String get(String fontName) {
        return cache.get(fontName);
    }

    boolean contains(String fontName) {
        return cache.containsKey(fontName);
    }
}

private static final FontCache fontCache = new FontCache();
```

**步骤 2**：修改 initialize() 方法
```java
public static void initialize() {
    // 如果缓存未过期，跳过初始化
    if (fontCache.isExpired() == false) {
        logger.debug("Font cache is still valid, skipping initialization");
        return;
    }

    logger.debug("Initializing font mapping configuration...");

    // 清除旧记录
    fallbackMappedFonts.clear();

    // 加载默认映射
    loadDefaultChineseFontMappings();

    // 加载用户配置
    loadUserConfiguration();

    // 更新缓存
    Map<String, String> allMappings = new HashMap<>(systemFontMappings);
    allMappings.putAll(fallbackMappedFonts);
    fontCache.update(allMappings);

    logger.info("Font mapping initialized: {} system fonts, {} fallback mappings",
        systemFontMappings.size(), fallbackMappedFonts.size());
}
```

**验收标准**：
- ✅ 第二次调用 initialize() 无需重新检测字体
- ✅ 缓存在 5 分钟后自动过期
- ✅ 线程安全

**性能测试**：
```java
// 测试用例
@Test
public void testFontCachePerformance() {
    long start1 = System.currentTimeMillis();
    FontMappingConfig.initialize();
    long time1 = System.currentTimeMillis() - start1;

    long start2 = System.currentTimeMillis();
    FontMappingConfig.initialize();
    long time2 = System.currentTimeMillis() - start2;

    // 第二次调用应该快得多（从缓存读取）
    assertTrue(time2 < time1 / 10);
}
```

---

### 🟢 P2-1: 添加核心功能单元测试

**问题描述**：
当前测试覆盖率为 0%，核心功能未经过自动化测试验证。

**修复方案**：

**测试框架设置**：
```xml
<!-- pom.xml 添加 JUnit 5 依赖 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.5.0</version>
    <scope>test</scope>
</dependency>
```

**测试用例清单**：

| 测试类 | 测试内容 | 优先级 |
|--------|---------|--------|
| `UrlResolverTest` | URL 解析、路径验证 | P0 |
| `TempFileManagerTest` | 临时文件创建和清理 | P0 |
| `FontCheckerTest` | 字体检查逻辑 | P1 |
| `FontMappingConfigTest` | 配置加载 | P1 |
| `I18nMessagesTest` | 国际化消息 | P2 |

**示例测试**：
```java
// UrlResolverTest.java
@Test
public void testExtractUrlSuccess() {
    PropertyValue[] props = new PropertyValue[] {
        new PropertyValue("URL", "file:///test.ofd")
    };
    String url = UrlResolver.extractUrl(props);
    assertEquals("file:///test.ofd", url);
}

@Test
public void testExtractUrlNotFound() {
    PropertyValue[] props = new PropertyValue[] {
        new PropertyValue("OtherProp", "value")
    };
    String url = UrlResolver.extractUrl(props);
    assertTrue(url.isEmpty());
}

@Test(expected = SecurityException.class)
public void testPathTraversalAttack() {
    UrlResolver.urlToPath("file:///../../etc/passwd");
}

// TempFileManagerTest.java
@Test
public void testCleanupDeletesAllFiles() throws IOException {
    TempFileManager manager = new TempFileManager();
    Path testFile = manager.resolve("test.txt");
    Files.write(testFile, "test".getBytes());

    assertTrue(Files.exists(testFile));

    manager.cleanup();
    manager.setDeleted();

    assertFalse(Files.exists(testFile));
}

@Test
public void testCleanupIsIdempotent() throws IOException {
    TempFileManager manager = new TempFileManager();
    manager.cleanup();
    manager.setDeleted();

    // 第二次清理不应该报错
    manager.cleanup();
    manager.setDeleted();
}
```

**验收标准**：
- ✅ 测试覆盖率达到 60%+
- ✅ 所有 P0 问题都有测试
- ✅ CI/CD 集成测试

---

## 4. 改进记录

### 记录模板

每次完成改进后，请使用以下格式记录：

```markdown
#### [日期] - [改进ID] - [改进标题]

**执行人**：[姓名]
**状态**：✅ 完成 | ⚠️ 部分 | ❌ 失败

**改进内容**：
- [ ] 完成项 1
- [ ] 完成项 2

**代码变更**：
- 修改文件：`OFDImportFilter.java:216-227`
- 新增文件：`SecurityValidator.java`
- 删除行数：10
- 新增行数：25

**测试结果**：
- [x] 单元测试通过
- [x] 手动测试通过
- [x] 性能测试通过

**问题与解决**：
- 问题：[描述遇到的问题]
- 解决：[如何解决的]

**验收确认**：
- [x] 符合验收标准
- [x] 代码审查通过
```

### 改进历史

| 日期 | ID | 描述 | 状态 | 执行人 |
|------|-----|------|------|--------|
| - | - | 暂无改进记录 | - | - |

---

## 5. 验收标准

### 5.1 功能验收

- [ ] 所有 P0 问题修复完成
- [ ] 所有 P1 问题修复完成
- [ ] 核心功能单元测试覆盖率 ≥ 60%
- [ ] 所有安全漏洞修复
- [ ] 性能基准测试通过

### 5.2 质量验收

- [ ] 代码审查通过
- [ ] 静态分析无严重问题
- [ ] 依赖漏洞扫描通过
- [ ] 文档更新完整

### 5.3 性能验收

| 指标 | 优化前 | 目标 | 实际 |
|------|--------|------|------|
| 字体检测耗时 | 20ms | <2ms (缓存) | - |
| 10页文档转换 | 5s | <3s (并行) | - |
| 内存占用 | 150MB | <120MB | - |
| 测试覆盖率 | 0% | ≥60% | - |

---

## 6. 附录

### 6.1 参考资源

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [LibreOffice UNO 开发指南](https://wiki.libreoffice.org/Development/UNO)
- [Java 编码规范](https://google.github.io/styleguide/javaguide.html)

### 6.2 工具推荐

- **静态分析**：SpotBugs, PMD, SonarQube
- **安全扫描**：OWASP Dependency-Check
- **性能分析**：JProfiler, VisualVM
- **测试框架**：JUnit 5, Mockito

---

**文档维护**：每次改进后请及时更新本文档的"改进记录"部分。

**最后更新**：2026-03-05
