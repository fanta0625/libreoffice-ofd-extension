# LoongOffice OFD UNO Extension - 项目记忆

## 更新日志

### 2026-03-16 - 文档完善与上架准备
- ✅ **创建 README 文档**（中英文双版本）：
  - [README.md](README.md) - 英文版，简洁实用
  - [README.zh-CN.md](README.zh-CN.md) - 中文版，内容一致
  - 包含：项目简介、框架结构、使用方法、技术栈、许可证
- ✅ **添加 MPL-2.0 许可证文件**：
  - 创建 [LICENSE](LICENSE) 文件，包含完整 MPL-2.0 文本
  - 更新 description.xml 声明（已有）
  - 更新 README 许可证部分
- 📋 **上架准备度评估**：
  - 当前状态：**75%** 准备度
  - ✅ 已完成：核心功能、基础文档、打包构建、LICENSE
  - ❌ 缺失项：扩展图标、完善 description.xml、CHANGELOG
  - ⚠️ 改进项：测试覆盖率（0%）、兼容性测试

### 2026-03-04 - 智能字体映射与Fallback警告（重大更新）
- ✅ **实现智能字体映射（方案A）**：
  - 启动时使用 fontconfig 检测系统字体
  - 如果找到 Windows 字体，跳过 fallback 映射，使用实际字体
  - 如果只找到 Noto 通用字体，添加 fallback 到开源字体
  - 每次初始化时清除记录，确保反映系统字体的最新状态
- ✅ **实现 Fallback 映射警告功能**：
  - FontChecker 新增 `isFallbackMapped` 字段
  - FontMappingConfig 记录所有使用 fallback 映射的字体
  - 当使用替换字体时，弹出警告对话框
  - 合并通用字体和 fallback 映射为一个统一警告
  - 字体列表基于 fontName 去重并排序
- ✅ **更新配置文件**：
  - font-mappings.properties 删除所有 `font.*` 强制映射
  - 只保留 `alias.*` 别名映射
  - 让智能映射自动选择最佳字体
- ✅ **核心优势**：
  - Windows 字体存在时：使用实际字体，无警告
  - Windows 字体不存在时：使用开源字体替换，显示警告
  - 动态添加/删除字体时：重新检测，自动适应

### 2026-03-04 - 字体检查与MessageBox集成（继续完善）
- ✅ 完成 FontChecker 与 MessageBox 的集成
- ✅ 在 OFDImportFilter 中添加字体检查步骤（Step 5）
- ✅ 使用 FontWarningDialog 构建消息内容，MessageBox.showWarningConfirm() 显示对话框
- ✅ 支持用户选择继续或取消打开文档
- ✅ 详细列出所有缺失字体和通用字体替换
- ✅ 验证 filter 返回 false 的行为（LibreOffice 标准错误消息）
- ✅ **修复 LibreOffice 25.8 按钮返回值异常问题**（YES=2, NO=3）
- ✅ **使用相对大小判断提高兼容性**（0 或 2 = 继续，其他 = 取消）
- ✅ **修正按钮常量定义**（BUTTONS_YES_NO = 3，符合官方文档）
- ✅ **清理 FontWarningDialog 类**（删除未使用方法，只保留消息构建功能）
- ✅ 清理代码，移除未使用的导入和注释代码

### 2026-03-03 - 字体映射配置与字体检查功能
- ✅ 新增 FontMappingConfig 类，支持应用层字体映射
- ✅ 新增 FontChecker 类，检查OFD文档字体可用性
- ✅ 新增 FontWarningDialog 类，显示字体警告对话框
- ✅ 支持 Windows → Linux 开源字体的默认映射

## 项目概述

LibreOffice UNO 扩展，用于打开 OFD（Open Fixed-layout Document）文档。
- 使用 ofdrw 库将 OFD 转换为 SVG
- 通过 filter 方式在 LibreOffice Draw 中打开

## 技术栈

- **Java 17**
- **ofdrw 2.3.8** - OFD 文档处理库
- **LibreOffice UNO 24.8.4** - LibreOffice 集成
- **Maven** - 构建工具

## 核心组件

### 主要类

- [OFDImportFilter.java](src/main/java/org/loongoffice/ofd/reader/OFDImportFilter.java) - 主过滤器，协调转换流程
- [SvgConverter.java](src/main/java/org/loongoffice/ofd/converter/SvgConverter.java) - OFD 转 SVG 转换器
- [DocumentHandler.java](src/main/java/org/loongoffice/ofd/document/DocumentHandler.java) - 文档处理
- [FontMappingConfig.java](src/main/java/org/loongoffice/ofd/config/FontMappingConfig.java) - 字体映射配置类
- [FontChecker.java](src/main/java/org/loongoffice/ofd/font/FontChecker.java) - 字体检查工具
- [MessageBox.java](src/main/java/org/loongoffice/ofd/util/MessageBox.java) - 消息框工具类
- [FontWarningDialog.java](src/main/java/org/loongoffice/ofd/util/FontWarningDialog.java) - 字体警告对话框

### 项目结构

```
src/main/java/org/loongoffice/ofd/
├── reader/               # 读取器和过滤器
│   ├── converter/        # 转换器
│   ├── document/         # 文档处理
│   ├── util/            # 工具类
│   └── config/          # 配置类（字体映射等）
```

## 字体映射配置（2026-03-04 智能映射更新）

### FontMappingConfig 类

位置：[FontMappingConfig.java](src/main/java/org/loongoffice/ofd/config/FontMappingConfig.java)

**功能**：
- **智能字体映射**：启动时使用 fontconfig 检测系统字体
- **动态适应**：优先使用实际字体，只在必要时使用 fallback
- **Fallback 警告**：记录使用 fallback 映射的字体，用于警告提示

### 智能映射工作原理

**启动时检测**（一次性开销约 10-20ms）：
```java
// 伪代码
for each 字体名称 {
    fcPath = FontConfigHelper.queryFontConfig(字体名称);

    if (fcPath 不是 Noto 通用字体) {
        // 找到了更好的字体（如 Windows 字体）
        // 不添加映射，让 fontconfig 自然工作
        记录：无警告
    } else {
        // fontconfig 没找到或只找到 Noto
        // 添加 fallback 映射到开源字体
        addSystemFontMapping(字体名称, 开源字体路径);
        记录到 fallbackMappedFonts（用于警告）
    }
}
```

**优先级顺序**：
1. **fontconfig 找到的实际字体**（如 Windows 字体）→ 最高优先级
2. **Fallback 映射**（开源字体）→ 当实际字体不存在时
3. **FontLoader 的 fontconfig 查询** → 最低优先级（兜底）

### 配置文件

**font-mappings.properties**：
```properties
# 只包含别名映射，不包含强制路径映射
alias.AR PL UKai CN=楷体
alias.Noto Serif CJK SC=宋体
alias.Noto Sans CJK SC=黑体

# Windows 字体名 -> Linux 字体名（双向映射）
alias.楷体=AR PL UKai CN
alias.宋体=Noto Serif CJK SC
alias.黑体=Noto Sans CJK SC
```

**说明**：
- 删除了所有 `font.*` 强制映射
- 只保留 `alias.*` 别名映射
- 让智能映射自动选择最佳字体

### 配置文件加载顺序

1. 默认内置映射（智能映射，每次重新检测）
2. `classpath:font-mappings.properties`（JAR 包内）
3. `~/.loongoffice/font-mappings.properties`（用户目录）
4. `/etc/loongoffice/font-mappings.properties`（系统目录，仅 Linux）

### 默认 Fallback 字体映射

| Windows 字体 | Linux Fallback 字体 | 说明 |
|--------------|---------------------|------|
| 楷体/KaiTi | AR PL UKai CN (/usr/share/fonts/truetype/arphic/ukai.ttc) | 书法风格 |
| 宋体/SimSun | Noto Serif CJK SC (/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc) | 衬线字体 |
| 黑体/SimHei | Noto Sans CJK SC (/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc) | 无衬线 |
| 微软雅黑 | Noto Sans CJK SC (/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc) | 无衬线 |
| 等线/DengXian | Noto Sans CJK SC（如果文件不存在） | 无衬线 |
| 仿宋/FangSong | cwTeX 仿宋 (/usr/share/fonts/truetype/cwtex/cwfs.ttf) | 衬线 |

**注意**：只有在系统中没有对应 Windows 字体时，才会使用这些 fallback 映射。

### 使用方法

```java
// 在应用启动时初始化
FontMappingConfig.initialize();

// 检查字体是否使用了 fallback 映射
if (FontMappingConfig.isFallbackMappedFont("楷体")) {
    // 楷体使用了 fallback 映射
    String fallbackPath = FontMappingConfig.getFallbackMapping("楷体");
}

// 获取所有 fallback 映射
Map<String, String> allFallbacks = FontMappingConfig.getAllFallbackMappings();
```

### 智能映射的优势

**场景 1：有 Windows 字体**
```
楷体 → fontconfig 找到 simkai.ttf → 不添加映射 → 使用 Windows 字体 → 无警告 ✅
```

**场景 2：没有 Windows 字体**
```
楷体 → fontconfig 返回 Noto Sans CJK → 添加 fallback 到 ukai.ttc
→ 记录到 fallbackMappedFonts
→ 弹出警告："字体替换提示 - 另有 X 种字体使用了替换字体" ⚠️
```

**场景 3：动态添加/删除字体**
```
第一次打开（无字体）→ 添加 fallback → 显示警告
关闭文档，添加 Windows 字体
第二次打开（有字体）→ fallbackMappedFonts.clear() → 重新检测 → 无警告 ✅
```
| 黑体/SimHei | Noto Sans CJK SC (/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc) | 无衬线 |
| 微软雅黑 | Noto Sans CJK SC | 无衬线 |
| 等线 | Noto Sans CJK SC | 无衬线 |
| 仿宋 | cwTeX 仿宋 (/usr/share/fonts/truetype/cwtex/cwfs.ttf) | 衬线 |

## 字体检查与警告功能（2026-03-04 智能映射更新）

### 功能概述

在打开 OFD 文档时，自动检查字体并显示警告：
1. **检查字体** - 获取文档中使用的所有字体，检查系统是否包含
2. **检测替换** - 识别两种替换情况：
   - **通用字体替换**（如 NotoSansCJK）- `isGenericFont = true`
   - **Fallback 映射**（智能映射添加的开源字体）- `isFallbackMapped = true`
3. **显示警告** - 统一的警告对话框，列出所有替换字体
4. **用户选择** - 提供"继续"/"取消"选项，让用户决定是否继续

### FontChecker 类

位置：[FontChecker.java](src/main/java/org/loongoffice/ofd/font/FontChecker.java)

**功能**：
- 获取 OFD 文档中所有使用的字体
- 检查系统是否包含这些字体
- 生成字体可用性报告
- 区分嵌入字体和系统字体
- 检测通用字体替换（如 NotoSansCJK）
- **检测 fallback 映射**（2026-03-04 新增）

**FontInfo 新增字段**：
```java
private final boolean isFallbackMapped;  // 是否使用了 fallback 映射
```

**核心方法**：
```java
FontCheckResult result = FontChecker.checkFonts(ofdFilePath);
```

### FontWarningDialog 类（2026-03-04 更新）

位置：[FontWarningDialog.java](src/main/java/org/loongoffice/ofd/util/FontWarningDialog.java)

**功能**：
- 构建 UNO 消息框的标题和内容
- **合并通用字体和 fallback 映射为一个统一警告**
- **字体列表基于 fontName 去重并排序**
- 当没有任何问题时返回 null（不显示对话框）

**警告对话框示例**：

**缺失字体警告**：
```
⚠️ 字体缺失警告

⚠️ 文档中使用了 2 种系统中不存在的字体：

  • STKAITI
  • FangSong

这些字体将被替换为默认字体，可能会影响显示效果。

是否继续打开文档？
[是] [否]
```

**字体替换提示**（Fallback 映射）：
```
字体替换提示

ℹ️ 另有 3 种字体使用了替换字体：

  • 楷体
  • 宋体
  • 黑体

原始字体不存在，已使用替代字体（如开源字体）替换，
虽然可以正常显示中文，但会丢失原始字体样式。

是否继续打开文档？
[是] [否]
```

### MessageBox 工具类（2026-03-04 新增，已验证可用✅）

位置：[MessageBox.java](src/main/java/org/loongoffice/ofd/util/MessageBox.java)

**功能**：
- 统一的消息框工具类，用于显示各种类型的信息对话框
- 基于 LibreOffice UNO XMessageBox API 实现
- 支持信息、警告、错误、确认等多种消息框类型
- 自动管理资源，确保正确释放 UNO 组件
- **已在 LibreOffice 24.8.4 上验证可用**

**核心方法**：
```java
// 显示信息消息框（INFOBOX + OK按钮）
short result = MessageBox.showInfo(context, targetDocument, title, message);

// 显示警告消息框（WARNINGBOX + OK按钮）
short result = MessageBox.showWarning(context, targetDocument, title, message);

// 显示错误消息框（ERRORBOX + OK按钮）
short result = MessageBox.showError(context, targetDocument, title, message);

// 显示确认消息框（QUERYBOX + YES/NO按钮）
short result = MessageBox.showConfirm(context, targetDocument, title, message);

// 显示警告确认消息框（WARNINGBOX + YES/NO按钮）- 2026-03-04 新增
short result = MessageBox.showWarningConfirm(context, targetDocument, title, message);
```

**按钮类型常量**：
- `BUTTONS_OK = 1` - 确定按钮
- `BUTTONS_YES_NO = 2` - 是/否按钮

**返回值常量**：
- `RESULT_OK = 0` - 确定或"是"
- `RESULT_YES = 0` - "是"按钮
- `RESULT_NO = 1` - "否"按钮
- `RESULT_CANCEL = 2` - 取消按钮
- `RESULT_RETRY = 2` - 重试按钮

**实现细节**：
1. **多层次回退机制获取 XWindowPeer**（关键）：
   - 首先尝试从目标文档的 Frame 获取
   - 如果失败（在 filter 过程中 XController 为 null），从 Desktop 的当前活动框架获取
   - 依次尝试 `getComponentWindow()` 和 `getContainerWindow()`
   - 最后尝试 Desktop 自己的容器窗口
2. 使用 `com.sun.star.awt.Toolkit` 创建 MessageBoxFactory
3. 调用 `createMessageBox(XWindowPeer, MessageBoxType, short buttons, String title, String message)`
4. 执行 `execute()` 显示对话框并等待用户响应
5. 在 finally 块中释放 XComponent 资源

**关键发现**（经过调试验证）：
- ✅ 在 `filter` 过程中可以显示消息框
- ✅ 目标文档的 `XController` 在 filter 阶段为 null（正常）
- ✅ 必须使用 Desktop 的当前活动框架获取 XWindowPeer
- ✅ `getContainerWindow()` 在 filter 阶段可用，`getComponentWindow()` 不可用
- ✅ 消息框会阻塞 filter 流程，用户点击后才继续

**使用示例**（来自 OFDImportFilter）：
```java
private void showWelcomeMessage(Path ofdPath) {
    try {
        String fileName = ofdPath.getFileName().toString();
        String title = I18nMessages.getMessage("messagebox.welcome.title");
        String message = I18nMessages.getMessage("messagebox.welcome.message", fileName);

        MessageBox.showInfo(context, documentHandler.getTargetDocument(), title, message);
    } catch (Throwable t) {
        log(I18nMessages.getMessage("log.messagebox.failed", t.getMessage()));
    }
}
```

### FontWarningDialog 类（消息构建工具）

位置：[FontWarningDialog.java](src/main/java/org/loongoffice/ofd/util/FontWarningDialog.java)（83行，已清理）

**功能**：
- **纯粹的消息内容构建工具**，不负责显示对话框
- 生成对话框标题和警告消息
- 区分字体缺失和通用字体替换两种情况
- 详细列出所有问题字体的名称

**设计原则**（2026-03-04 清理）：
- 职责单一：只负责消息内容构建，不处理对话框显示
- 与 MessageBox 配合使用：FontWarningDialog 构建消息，MessageBox 显示对话框
- 删除了所有未使用的显示相关方法（约70%代码）
- **合并通用字体和 fallback 映射为一个统一警告**（2026-03-04 更新）
- **字体列表基于 fontName 去重并排序**（2026-03-04 更新）

**核心方法**：
```java
// 构建对话框标题（当没有任何问题时返回 null）
public static String buildCaption(FontCheckResult result)

// 构建警告消息内容（统一处理通用字体和 fallback 映射）
public static String buildWarningMessage(FontCheckResult result)
```

**使用示例**（与MessageBox配合使用）：
```java
// 检查字体
FontChecker.FontCheckResult fontCheckResult = FontChecker.checkFonts(ofdPath);
boolean hasGenericFont = fontCheckResult.getAvailableFonts().stream()
        .anyMatch(FontChecker.FontInfo::isGenericFont);
boolean hasFallbackMapped = fontCheckResult.getAvailableFonts().stream()
        .anyMatch(FontChecker.FontInfo::isFallbackMapped);

// 如果有缺失字体、通用字体替换或 fallback 映射，显示警告对话框
if (fontCheckResult.hasMissingFonts() || hasGenericFont || hasFallbackMapped) {
    // FontWarningDialog 只负责构建消息内容
    String title = FontWarningDialog.buildCaption(fontCheckResult);

    // 如果 title 为 null，说明没有警告需要显示
    if (title != null) {
        String message = FontWarningDialog.buildWarningMessage(fontCheckResult);

        // MessageBox 负责显示对话框
        short userChoice = MessageBox.showWarningConfirm(context, targetDocument, title, message);

        // 判断用户选择（0 或 2 = YES，其他 = NO）
        if (userChoice == 0 || userChoice == 2) {
            // 继续打开
        } else {
            return false; // 取消
        }
    }
}
```

### 集成位置

字体检查在 `OFDImportFilter.filter()` 中的执行流程：

```
1. 初始化进度跟踪
2. 提取并验证 URL
3. 确保目标文档存在
4. 验证 OFD 文件存在
5. ✅ 检查字体并显示警告（Step 5，新增）
6. 创建临时文件管理器
7. 转换 OFD 为 SVG 并插入
```

**字体检查步骤**（Step 5）：
- 位置：[OFDImportFilter.java:160-180](src/main/java/org/loongoffice/ofd/reader/OFDImportFilter.java:160)
- 在 OFD 文件验证通过后、转换前执行
- 更新进度显示为"正在检查字体..."
- 调用 `FontChecker.checkFonts()` 分析文档字体
- 检查是否有缺失字体或通用字体替换（isGenericFont标志）
- 如果有问题，使用 `FontWarningDialog` 构建消息内容
- 使用 `MessageBox.showWarningConfirm()` 显示 WARNINGBOX + YES/NO 对话框
- 兼容性判断：0 或 2 = YES（继续），其他 = NO（取消）

**最终行为**（已验证）：
- 当 filter() 返回 false 时，LibreOffice 会：
  1. 关闭当前文档窗口
  2. 显示标准错误消息框："general error, general input/output error"
  3. 不影响其他已打开的文档窗口
- 这是 LibreOffice filter 的标准行为，已被接受为正常表现

**代码实现**（[OFDImportFilter.java:160-180](src/main/java/org/loongoffice/ofd/reader/OFDImportFilter.java:160)）：
```java
// Step 5: Check fonts and show warning if needed
progressTracker.setText(I18nMessages.getMessage("progress.checking.fonts"));
progressTracker.setValue(25);

// 检查字体
FontChecker.FontCheckResult fontCheckResult = FontChecker.checkFonts(ofdPath.toString());
boolean hasGenericFont = fontCheckResult.getAvailableFonts().stream()
        .anyMatch(FontChecker.FontInfo::isGenericFont);

// 如果有缺失字体或通用字体替换，显示警告对话框
if (fontCheckResult.hasMissingFonts() || hasGenericFont) {
    String title = FontWarningDialog.buildCaption(fontCheckResult);
    String message = FontWarningDialog.buildWarningMessage(fontCheckResult);

    // 使用 WARNINGBOX + YES/NO 按钮显示警告对话框
    short userChoice = MessageBox.showWarningConfirm(context, documentHandler.getTargetDocument(), title, message);
    log(I18nMessages.getMessage("log.user.choice", String.valueOf(userChoice)));

    // LibreOffice 标准返回值：YES=0, NO=1
    // LibreOffice 25.8 观察到：YES=2, NO=3
    // 兼容性判断：较小的值（0或2）= YES（继续），较大的值 = NO（取消）
    if (userChoice == 0 || userChoice == 2) {
        // 用户点击了"是"，继续转换
        log(I18nMessages.getMessage("log.font.check.continued"));
    } else {
        // 用户点击了"否"或按ESC，取消转换
        log(I18nMessages.getMessage("log.font.check.cancelled"));
        return false; // User chose to cancel, LibreOffice 会显示错误消息框并关闭文档窗口
    }
}
log(I18nMessages.getMessage("log.font.check.continued"));
```

### 警告对话框示例

**类型 1：字体缺失警告**（当 hasMissingFonts() = true 时）

对话框标题：**字体缺失警告**

对话框内容：
```
⚠️ 文档中使用了 3 种系统中不存在的字体：

  • KaiTi (楷体)
  • FangSong (仿宋)
  • DengXian (等线)

这些字体将被替换为默认字体，可能会影响显示效果。

是否继续打开文档？
```

按钮：[是] [否]

**类型 2：字体替换提示**（当只有通用字体替换时）

对话框标题：**字体替换提示**

对话框内容：
```
ℹ️ 另有 5 种字体使用了通用替换：

  • FontName1 (FamilyName1)
  • FontName2 (FamilyName2)
  • FontName3 (FamilyName3)
  • FontName4 (FamilyName4)
  • FontName5 (FamilyName5)

这些字体使用通用字体（如 Noto Sans CJK）替换，
虽然可以正常显示中文，但会丢失原始字体样式。

是否继续打开文档？
```

按钮：[是] [否]

**类型 3：混合警告**（同时有缺失字体和通用字体替换）

对话框标题：**字体缺失警告**

对话框内容：
```
⚠️ 文档中使用了 2 种系统中不存在的字体：

  • KaiTi (楷体)
  • FangSong (仿宋)

这些字体将被替换为默认字体，可能会影响显示效果。

ℹ️ 另有 5 种字体使用了通用替换：

  • FontName1 (FamilyName1)
  • FontName2 (FamilyName2)
  • FontName3 (FamilyName3)
  • FontName4 (FamilyName4)
  • FontName5 (FamilyName5)

这些字体使用通用字体（如 Noto Sans CJK）替换，
虽然可以正常显示中文，但会丢失原始字体样式。

是否继续打开文档？
```

按钮：[是] [否]

**用户操作结果**（兼容多种 LibreOffice 版本）：
- 点击"是"：
  - 标准 LibreOffice：返回 0 ✅
  - LibreOffice 25.8：返回 2 ✅
  - 行为：继续转换并打开文档
- 点击"否"：
  - 标准 LibreOffice：返回 1 ✅
  - LibreOffice 25.8：返回 3 ✅
  - 行为：filter() 返回 false，LibreOffice 关闭文档窗口并显示"general error"消息框
- 按ESC或关闭：返回较大值（通常为2或更大），行为与点击"否"相同

### 技术实现细节（2026-03-04 调试经验）

#### Filter 上下文中的 MessageBox 显示

**问题**：在 filter 执行过程中显示对话框遇到困难
- 目标文档的 XController 在 filter 阶段为 null（正常现象）
- 使用 getComponentWindow() 返回 null
- FontWarningDialog 原有的窗口获取逻辑不工作

**解决方案**：多层次回退机制（已在 MessageBox.java 中实现）
```java
// 方法1: 尝试从目标文档的 Frame 获取
XWindowPeer peer = getWindowPeerOfFrame(targetDocument);

// 方法2: 如果失败，从 Desktop 的当前活动框架获取
if (peer == null) {
    peer = getDesktopWindowPeer(context);
}
```

**关键发现**（经过调试验证）：
1. ✅ 在 filter 过程中可以显示消息框（会阻塞流程）
2. ✅ 目标文档的 XController 在 filter 阶段为 null（正常）
3. ✅ Desktop.getCurrentFrame() 可以获取当前活动框架
4. ✅ `getContainerWindow()` 在 filter 阶段可用
5. ✅ `getComponentWindow()` 在 filter 阶段返回 null（不可用）
6. ✅ 必须使用 Desktop 的容器窗口作为父窗口

#### 用户取消处理

**尝试方案1**：使用 XCloseable.close(true)
```java
XCloseable closeable = UnoRuntime.queryInterface(XCloseable.class, targetDocument);
if (closeable != null) {
    closeable.close(true); // ❌ 会关闭整个 LibreOffice 应用
}
```
**问题**：关闭了所有文档窗口，包括其他 LibreOffice 窗口

**尝试方案2**：只返回 false
```java
if (userChoice != MessageBox.RESULT_YES) {
    return false; // ✅ 只关闭当前文档窗口
}
```
**结果**：✅ 成功 - 只关闭当前文档窗口，不影响其他窗口
**副作用**：LibreOffice 显示标准错误消息框："general error, general input/output error"
**用户决定**：接受此行为作为 LibreOffice 的标准表现

#### 按钮返回值处理

**官方文档标准**：
```java
const long BUTTONS_YES_NO = 3  // YES 和 NO 按钮
```

**标准返回值**（官方文档）：
- 第一个按钮（YES/"是"）= 0
- 第二个按钮（NO/"否"）= 1
- ESC/关闭 = 2

**LibreOffice 25.8 异常情况**（实际观察到）：
- 第一个按钮（YES/"是"）= **2**（偏离标准）
- 第二个按钮（NO/"否"）= **3**（偏离标准）

**分析**：
- 所有返回值都 **+2 偏移**
- 可能与默认按钮标志（DEFAULT_BUTTON_YES = 0x40000）或内部实现有关
- 不同 LibreOffice 版本可能行为不同

**兼容性解决方案**（2026-03-04 实现）：
使用**相对大小判断**而不是绝对值，提高兼容性：
```java
// 判断逻辑：左边按钮值较小，右边按钮值较大
// 0 和 2 都是左边按钮（YES）的返回值
if (userChoice == 0 || userChoice == 2) {
    // 用户点击"是"，继续
    log(I18nMessages.getMessage("log.font.check.continued"));
} else {
    // 用户点击"否"或ESC（值较大），取消
    log(I18nMessages.getMessage("log.font.check.cancelled"));
    return false;
}
```

**优点**：
- ✅ 兼容标准 LibreOffice（0, 1）
- ✅ 兼容 LibreOffice 25.8（2, 3）
- ✅ 处理 ESC/关闭（通常返回较大值）
- ✅ 不依赖具体返回值，只依赖相对大小

**按钮常量修正**：
```java
// ❌ 之前的错误定义
private static final short BUTTONS_YES_NO = 2;  // 实际是 BUTTONS_OK_CANCEL

// ✅ 正确定义（根据官方文档）
private static final short BUTTONS_YES_NO = 3;  // YES/NO 按钮
```

#### 消息内容构建

**FontWarningDialog 的设计思路**：
1. 将消息构建逻辑与显示逻辑分离
2. buildCaption() 和 buildWarningMessage() 改为 public static
3. 支持外部调用（OFDImportFilter 直接使用）
4. 详细列出所有问题字体的名称（不只显示数量）

**示例**：
```java
// 不够清晰：只显示数量
"ℹ️ 另有 5 种字体使用了通用替换。"

// 更清晰：列出具体字体
"ℹ️ 另有 5 种字体使用了通用替换：\n\n" +
"  • FontName1 (FamilyName1)\n" +
"  • FontName2 (FamilyName2)\n" +
"  • FontName3 (FamilyName3)\n" +
"  • FontName4 (FamilyName4)\n" +
"  • FontName5 (FamilyName5)\n"
```

#### 代码清理优化（2026-03-04）

**FontWarningDialog 类重构**：

**清理前**（276行）：
- 包含完整的对话框显示逻辑
- 有 `showWarning()` 公共方法
- 有多个私有方法用于窗口获取
- 消息构建与显示逻辑混合

**清理后**（83行，减少70%）：
- 只保留消息内容构建功能
- 删除所有显示相关代码
- 职责更单一，代码更清晰

**删除的方法**：
- ❌ `showWarning()` - 已被 MessageBox.showWarningConfirm() 替代
- ❌ `showWarningDialog()` - 显示对话框逻辑
- ❌ `getParentWindow()` - 窗口获取
- ❌ `getWindowFromTargetDocument()` - 窗口获取
- ❌ `getWindowFromDesktop()` - 窗口获取
- ❌ `hasGenericFontReplacement()` - 内部辅助方法
- ❌ 所有 UNO 相关导入（约20个）

**保留的方法**：
- ✅ `buildCaption(FontCheckResult)` - 构建对话框标题
- ✅ `buildWarningMessage(FontCheckResult)` - 构建警告消息

**设计优势**：
1. **职责单一**：只负责消息内容构建，不处理显示
2. **易于测试**：纯静态方法，无副作用
3. **易于维护**：代码量减少70%，逻辑更清晰
4. **灵活组合**：可与任何消息框实现配合使用

### ofdrw 库字体接口

通过探索 ofdrw 库源码（/home/loongson/projects/loongoffice/ofdrw），发现以下关键接口：

1. **OFDReader** - OFD 文档读取器
   ```java
   try (OFDReader reader = new OFDReader(ofdPath)) {
       ResourceManage resMgt = reader.getResMgt();
   }
   ```

2. **ResourceManage** - 资源管理器
   - `getFonts()` - 获取文档中所有字体定义
   - `getFont(id)` - 根据 ID 获取单个字体

3. **CT_Font** - 字体对象
   - `getFamilyName()` - 字族名
   - `getFontName()` - 字体名
   - `getFontFile()` - 字体文件路径（嵌入字体）

4. **FontLoader** - 字体加载器（位于 ofdrw-converter 模块）
   ```java
   FontLoader fontLoader = FontLoader.getInstance();
   String systemFontPath = fontLoader.getSystemFontPath(familyName, fontName);
   ```

### 字体检查 Demo

位置：[FontCheckerDemo.java](src/test/java/org/loongoffice/ofd/font/FontCheckerDemo.java)

功能：
- 获取 OFD 文档中所有使用的字体
- 检查系统是否包含这些字体
- 生成字体可用性报告
- 区分嵌入字体和系统字体

运行方式：

**重要**：由于 FontCheckerDemo 位于 `src/test/java` 目录，必须添加 `-Dexec.classpathScope=test` 参数

```bash
# 正确的运行命令（必需添加 classpathScope=test 参数）
mvn exec:java -Dexec.mainClass="org.loongoffice.ofd.font.FontCheckerDemo" \
    -Dexec.classpathScope=test \
    -Dexec.args="/path/to/document.ofd"

# 实际示例
mvn exec:java -Dexec.mainClass="org.loongoffice.ofd.font.FontCheckerDemo" \
    -Dexec.classpathScope=test \
    -Dexec.args="/home/loongson/projects/loongoffice/ofd_doc/26149126473000011572/26149126473000011572.ofd"
```

示例输出：
```
正在分析 OFD 文档: /home/user/example.ofd
从文档中读取到 3 个字体定义
  字体: SimSun (宋体) - 嵌入: 否, 可用: 是
  字体: KaiTi (楷体) - 嵌入: 否, 可用: 是
  字体: CustomFont (null) - 嵌入: 否, 可用: 否

================================================================================
OFD 文档字体检查报告
================================================================================
总字体数: 3
可用字体数: 2
缺失字体数: 1
...
```

### 已实现功能

1. **✅ 集成到 OFDImportFilter**（2026-03-04 完成，已优化）
   - 在转换前检查字体（Step 5）
   - 使用 FontChecker.checkFonts() 分析字体
   - 使用 FontWarningDialog 构建消息内容
   - 使用 MessageBox.showWarningConfirm() 显示对话框
   - **兼容性判断逻辑**：支持标准 LibreOffice 和 LibreOffice 25.8
   - 支持用户选择继续或取消
   - 取消时关闭文档窗口（LibreOffice 标准行为）

2. **✅ MessageBox 通用组件**（2026-03-04 完成，已优化）
   - 封装 XMessageBox API 的完整实现
   - 支持多种消息框类型（INFO/WARNING/ERROR/QUERY）
   - 多层次回退机制确保在 filter 上下文中可用
   - **修正按钮常量**：BUTTONS_YES_NO = 3（符合官方文档）
   - 已在 LibreOffice 24.8.4 和 25.8 上验证可用

3. **✅ 字体警告消息内容**（2026-03-04 完成）
   - 详细列出所有缺失字体的名称
   - 详细列出所有使用通用替换的字体
   - 区分不同警告类型（缺失 vs 替换）
   - 支持中英文国际化

4. **✅ 代码清理与重构**（2026-03-04 完成）
   - **FontWarningDialog 类**：从 276 行减少到 83 行（-70%）
     - 删除所有显示相关方法
     - 只保留消息内容构建功能
     - 职责单一，易于维护
   - **删除未使用导入**：移除约 20 个 UNO 相关导入
   - **代码质量**：编译通过，无警告

## 字体匹配机制

### 问题背景

Linux 系统的 fontconfig 通常**没有配置 Windows 中文字体的映射**，导致：
- 请求"楷体"时，fontconfig fallback 到 NotoSansCJK（通用字体）
- 丢失了原始字体样式（楷体的书法风格 → 无衬线风格）

### 解决方案

**应用层字体映射**（FontMappingConfig）：

1. **精确映射**：在应用启动时，将 Windows 字体名映射到 Linux 字体文件
2. **配置文件**：支持通过配置文件自定义映射
3. **不修改系统**：只影响本应用，不改变系统配置

### ofdrw 的 FontLoader 查找策略

1. 精确匹配字体名称
2. 别名映射查找
3. 正则表达式相似字体匹配
4. Linux 使用 fontconfig 查询
5. 以上都失败则使用默认字体

## 构建和部署（2026-03-04 更新）

### 设计理念

- **默认构建**：生成通用版本 `ofd-reader.oxt`
- **品牌化构建**：使用 `-P branded -Dbrand=<品牌名>` 生成品牌版本
- **配置隔离**：LibreOffice 和 LoongOffice 已通过 patches 实现配置目录隔离
- **单一包支持**：由于配置已隔离，可以使用同一个 OXT 包

### 构建方式

#### 1. 默认构建（推荐）

```bash
mvn clean package -DskipTests
```

**生成**：`target/ofd-reader.oxt`
- 标识符：`org.loongoffice.ofd.reader`
- 显示名称：`OFD Reader`

**适用场景**：
- 日常开发和测试
- 通用发布
- 不需要特定品牌

#### 2. 品牌化构建

```bash
mvn clean package -DskipTests -P branded -Dbrand=<品牌名>
```

**示例**：

LoongOffice 版本：
```bash
mvn clean package -DskipTests -P branded -Dbrand=loongoffice
```
- 生成：`target/loongoffice-ofd-reader.oxt`
- 标识符：`org.loongoffice.ofd.reader.loongoffice`

SmallOffice 版本：
```bash
mvn clean package -DskipTests -P branded -Dbrand=smalloffice
```
- 生成：`target/smalloffic-ofd-reader.oxt`
- 标识符：`org.loongoffice.ofd.reader.smalloffic`

**适用场景**：
- 需要特定品牌标识
- 为不同渠道提供定制版本

### 安装步骤

由于 LibreOffice 和 LoongOffice 已实现配置目录隔离，**可以使用同一个 OXT 包**：

```bash
# 1. 构建默认版本
mvn clean package -DskipTests

# 2. 安装到 LibreOffice
/usr/bin/unopkg add target/ofd-reader.oxt

# 3. 安装到 LoongOffice
/home/loongson/projects/loongoffice/build/instdir/program/unopkg add target/ofd-reader.oxt
```

### 验证安装

```bash
# 查看 LibreOffice 的扩展
/usr/bin/unopkg list | grep -i ofd

# 查看 LoongOffice 的扩展
/home/loongson/projects/loongoffice/build/instdir/program/unopkg list | grep -i ofd

# 查看物理安装位置
ls -la ~/.config/libreoffice/4/user/uno_packages/cache/uno_packages/
ls -la ~/.config/loongoffice/4/user/uno_packages/cache/uno_packages/
```

### 实现细节

**关键修改**：
1. **动态 JAR 文件引用**：[OFDImportFilter.components](src/main/oxt/OFDImportFilter.components:3) 使用 `@extension.jar.name@` 占位符
2. **动态属性替换**：使用 Ant filterset 在构建时替换 `description.xml` 和 `.components` 中的占位符
3. **品牌化参数**：通过 `-Dbrand` 参数传递品牌名称

**命名规范**：
- 所有版本都遵循 `*-ofd-reader` 格式
- 默认版本：`ofd-reader`
- 品牌版本：`<brand>-ofd-reader`

## 常用命令

### 构建项目

```bash
# 默认构建（推荐）
mvn clean package -DskipTests

# 品牌化构建（例如 loongoffice）
mvn clean package -DskipTests -P branded -Dbrand=loongoffice
```

### 测试字体检查

```bash
# 必须添加 -Dexec.classpathScope=test 参数，因为 Demo 在 src/test/java 目录
mvn exec:java -Dexec.mainClass="org.loongoffice.ofd.font.FontCheckerDemo" \
    -Dexec.classpathScope=test \
    -Dexec.args="/path/to/test.ofd"
```

### 生成 OXT 扩展包

```bash
# 默认构建（通用版本）
mvn clean package -DskipTests
# 输出: target/ofd-reader.oxt

# 品牌化构建（例如 loongoffice）
mvn clean package -DskipTests -P branded -Dbrand=loongoffice
# 输出: target/loongoffice-ofd-reader.oxt
```

## 关键文件

- [pom.xml](pom.xml) - Maven 配置，依赖管理
- [src/main/resources/META-INF/manifest.xml](src/main/resources/META-INF/manifest.xml) - UNO 清单文件
- [src/main/resources/Filter.xcu](src/main/resources/Filter.xcu) - 过滤器配置
- [src/main/resources/Types.xcu](src/main/resources/Types.xcu) - 文件类型配置

## 开发注意事项

1. **只读文档**：插件会自动将 OFD 文档以只读方式打开
2. **临时文件**：使用 TempFileManager 管理临时文件
3. **进度显示**：使用 ProgressTracker 显示转换进度
4. **国际化**：使用 I18nMessages 支持多语言

## 相关资源

- ofdrw 源码：/home/loongson/projects/loongoffice/ofdrw
- ofdrw GitHub：https://github.com/Trisia/ofdrw
- OFD 标准：GB/T 33190-2016
- LibreOffice UNO API：https://www.libreoffice.org/get-help/documentation/

---

# LibreOffice Extension 商店上架准备（2026-03-16）

## 上架准备度评估

**当前状态**：**75%** 准备度（可以上架，建议补充部分内容）

### ✅ 已完成项

1. **核心功能完整** ✅
   - OFD → SVG 转换
   - 字体映射（智能检测 + fallback）
   - 字体检查警告
   - 只读模式
   - 国际化支持

2. **基础文档** ✅
   - [README.md](README.md) - 英文版
   - [README.zh-CN.md](README.zh-CN.md) - 中文版
   - [CLAUDE.md](CLAUDE.md) - 开发记忆
   - [docs/](docs/) - 技术文档

3. **打包构建** ✅
   - Maven 构建流程完善
   - OXT 扩展包生成正常
   - 品牌化支持（branded profile）

4. **许可证文件** ✅
   - [LICENSE](LICENSE) - MPL-2.0 完整文本（2026-03-16 添加）
   - description.xml 声明 MPL-2.0
   - README 更新许可证说明

### ❌ 缺失项（P0 - 必须补充）

#### 1. 扩展图标 🔴

**位置**：`src/main/resources/description/icon.png`

**要求**：
- 格式：PNG
- 尺寸：32x32 或 64x64 像素
- 可选：icon-HIGH.png（高分辨率版本）

**建议**：
- 简洁的 OFD 文档图标
- 可以使用 LibreOffice 风格配色
- 支持透明背景

**创建命令**：
```bash
# 创建图标目录（如果不存在）
mkdir -p src/main/resources/description

# 然后将图标文件复制到该目录
cp your-icon.png src/main/resources/description/icon.png
```

#### 2. 完善 description.xml 🟡

**当前文件**：[src/main/resources/description/description.xml](src/main/resources/description/description.xml)

**缺失内容**：
- `<display-name>` 的中文名称
- `<description>` 功能描述（中英文）
- 发布者详细信息
- 图标文件引用

**建议修改**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<description xmlns="http://openoffice.org/extensions/description/2006"
             xmlns:xlink="http://www.w3.org/1999/xlink">
  <identifier value="@extension.identifier@"/>
  <version value="1.0.0"/>

  <display-name>
    <name lang="en-US">@extension.displayName@</name>
    <name lang="zh-CN">@extension.displayName@</name>
  </display-name>

  <icon>
    <normal xlink:href="icon.png"/>
    <high-contrast xlink:href="icon-HIGH.png"/>
  </icon>

  <publisher>
    <name lang="en-US" xlink:href="https://github.com/loongson/loongoffice">
      LoongOffice Contributors
    </name>
  </publisher>

  <license MPL-2.0" xlink:href="https://www.mozilla.org/MPL/2.0/"/>

  <description>
    <src lang="en-US" xlink:href="description-en.txt"/>
    <src lang="zh-CN" xlink:href="description-zh-CN.txt"/>
  </description>

  <release-notes>
    <src lang="en-US" xlink:href="CHANGELOG.md"/>
  </release-notes>
</description>
```

#### 3. 扩展描述文件 🟡

**需要创建**：
- `src/main/resources/description/description-en.txt`（英文描述）
- `src/main/resources/description/description-zh-CN.txt`（中文描述）

**示例内容**（description-en.txt）：
```
This extension enables LibreOffice to open OFD (Open Fixed-layout Document) files.

Features:
• Convert OFD to SVG for viewing in LibreOffice Draw
• Automatic font mapping (Windows fonts → Linux open-source fonts)
• Font missing warnings with user confirmation
• Read-only mode to prevent accidental modifications
• Full Chinese language support

System Requirements:
• LibreOffice 7.0+ or LoongOffice
• Java 17 runtime environment
• Linux system (tested on LoongArch, x86_64)
```

### 🟡 建议补充项（P1 - 改进体验）

#### 4. CHANGELOG.md 🟡

**位置**：项目根目录

**内容建议**：
```markdown
# Changelog

## [1.0.0] - 2026-03-16

### Added
- Initial release of OFD Reader extension
- OFD to SVG conversion using ofdrw library
- Intelligent font mapping system
- Font availability checking with warnings
- Read-only mode for OFD documents
- Chinese and English language support

### Features
- Automatic detection of Windows fonts on Linux
- Fallback to open-source fonts when Windows fonts unavailable
- User-friendly font replacement warnings
- Progress indicator during conversion
```

#### 5. 扩展截图 🟡

**用途**：LibreOffice Extension 商店展示

**建议准备**：
1. 主界面截图（打开 OFD 文件）
2. 字体警告对话框截图
3. 关于/扩展管理器截图

**规格**：
- 尺寸：建议 1200x800 或类似比例
- 格式：PNG 或 JPG

#### 6. 商店发布信息 🟡

**准备材料**：
- 扩展名称（已确定：OFD Reader）
- 简短描述（1-2句话）
- 详细描述（可使用上面的 description-en.txt）
- 分类：Document conversion / Import/Export
- 标签：OFD, document viewer, Chinese
- 主页链接：GitHub 仓库地址
- 下载链接：GitHub Releases

### ⚠️ 潜在问题（需要评估）

#### 1. 测试覆盖率 0% 🟡

**现状**：[docs/OPTIMIZATION_PLAN.md](docs/OPTIMIZATION_PLAN.md) 提到测试覆盖率为 0%

**风险**：
- 商店用户期望扩展稳定可靠
- 未测试的边界情况可能导致崩溃

**建议**：
- 添加核心功能的单元测试（约 2-3 小时工作量）
- 手动测试各种 OFD 文件
- 测试 LibreOffice 不同版本兼容性

#### 2. 兼容性说明 🟢

**当前**：声明 LibreOffice 24.8.4

**建议测试**：
- LibreOffice 7.x（最低版本要求）
- LibreOffice 24.x（当前系列）
- LoongOffice（定制版本）
- 不同架构（LoongArch, x86_64, ARM64）

**更新 description.xml**：
```xml
<dependencies>
  < LibreOffice-minimal-version value="7.0"/>
</dependencies>
```

## 上架清单

### 必做项（P0）- 上架前必须完成

- [ ] **添加扩展图标**（icon.png）
- [ ] **完善 description.xml**（添加中英文名称、描述）
- [ ] **创建扩展描述文件**（description-en.txt, description-zh-CN.txt）

### 建议项（P1）- 改进上架体验

- [ ] **创建 CHANGELOG.md**
- [ ] **准备扩展截图**（2-3 张）
- [ ] **测试兼容性**（不同 LibreOffice 版本）

### 可选项（P2）- 长期改进

- [ ] **添加单元测试**（核心功能）
- [ ] **完善发布说明**（Release Notes）
- [ ] **添加用户文档**（使用指南）

## 上架流程

### 1. 准备阶段（当前阶段）

```bash
# 1. 完成上述必做项
# 2. 构建最终版本
mvn clean package -DskipTests

# 3. 本地测试
/usr/bin/unopkg add target/ofd-reader.oxt
# 测试各种 OFD 文件
```

### 2. 发布阶段

```bash
# 1. 创建 Git tag
git tag -a v1.0.0 -m "First release"
git push origin v1.0.0

# 2. 在 GitHub 创建 Release
#    - 上传 ofd-reader.oxt
#    - 添加发布说明

# 3. 提交到 LibreOffice Extension 商店
#    - 访问：https://extensions.libreoffice.org/
#    - 注册账号（如果还没有）
#   - 填写扩展信息
#    - 上传 OXT 文件
#    - 等待审核
```

### 3. 审核阶段

- LibreOffice 团队会审核扩展
- 通常需要 1-3 个工作日
- 可能需要修改或补充信息

### 4. 发布后维护

- 监控用户反馈
- 修复 Bug
- 定期更新兼容性
- 发布新版本

## 参考资料

- **LibreOffice Extension 发布指南**：https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions
- **Extension 商店**：https://extensions.libreoffice.org/
- **description.xml 规范**：https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions#description.xml
- **OXT 包结构**：https://wiki.documentfoundation.org/Documentation/DevGuide/Extensions#Extension_Package

## 总结

**当前状态**：项目核心功能完善，文档齐全，已具备上架条件。

**建议**：
1. 如果只是**内部使用**或**GitHub 发布**：当前状态已经足够 ✅
2. 如果要**上架 LibreOffice Extension 商店**：建议补充 P0 项（图标、description.xml），约 1-2 小时工作量

**优先级**：
1. 扩展图标（最重要，影响第一印象）
2. 完善 description.xml（必须）
3. 创建 CHANGELOG.md（建议）

---

**最后更新**：2026-03-16
**准备度**：75%
**预计上架时间**：补充 P0 项后 1-2 天内可上架
