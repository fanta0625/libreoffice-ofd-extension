# LibreOffice OFD 扩展

LibreOffice 扩展，可将 OFD（开放版式文档）文件转换为 SVG 以便在 LibreOffice Draw 中查看。

## 项目简介

本扩展通过 OFD → SVG 转换实现 OFD 阅读功能。主要特性：
- 字体缺失警告
- 只读模式打开
- 支持中文

## 项目框架

```
src/main/java/org/loongoffice/ofd/
├── reader/          # 核心过滤器（OFDImportFilter）
├── converter/       # OFD → SVG 转换器
├── document/        # 文档处理
├── font/            # 字体检查工具
├── util/            # 工具类（MessageBox、TempFileManager 等）
└── config/          # 字体映射配置
```

## 使用方法

### 安装

**方法 1：图形界面安装（推荐）**

1. 构建 OXT 文件：
   ```bash
   mvn clean package -DskipTests
   ```

2. 在 LibreOffice 中安装：
   - 打开 LibreOffice
   - 菜单：**工具** → **扩展管理**
   - 点击 **添加** 按钮
   - 选择 `target/libreoffice-ofd-extension.oxt` 文件
   - 重启 LibreOffice

**方法 2：命令行安装**

```bash
unopkg add target/libreoffice-ofd-extension.oxt
```

### 打开 OFD 文件

安装后，直接在 LibreOffice 中打开 `.ofd` 文件即可。

### 集成到 LibreOffice 构建系统

将扩展打包到 LibreOffice 中（内置扩展，用户无法删除）：

```bash
cd /path/to/libreoffice/source

# 创建扩展目录
mkdir -p external/ofdreader

# 复制 OXT 文件
cp /path/to/libreoffice-ofd-extension.oxt external/ofdreader/

# 创建 Module_ofdreader.mk
cat > external/ofdreader/Module_ofdreader.mk << 'EOF'
$(eval $(call gb_Module_Module,ofdreader))
$(eval $(call gb_Module_add_targets,ofdreader,\
    ExtensionPackage_ofdreader \
))
EOF

# 创建 ExtensionPackage_ofdreader.mk
cat > external/ofdreader/ExtensionPackage_ofdreader.mk << 'EOF'
$(eval $(call gb_ExtensionPackage_ExtensionPackage,ofdreader,$(SRCDIR)/external/ofdreader/libreoffice-ofd-extension.oxt))
EOF

# 在 configure.ac 中添加（与其他 libo_CHECK_EXTENSION 并列）
# libo_CHECK_EXTENSION([OFDReader],[OFDREADER],[ofdreader],[ofdreader],[libreoffice-ofd-extension.oxt])

# 在 external/Module_external.mk 中添加（在 gb_Module_add_moduledirs 列表中）
# $(call gb_Helper_optional,OFDREADER,ofdreader) \
```

重新构建 LibreOffice 即可。扩展将安装到 `share/extensions/` 目录，用户无法删除。

### 字体映射配置

字体映射文件位置（按优先级）：
1. `~/.libreffice/font-mappings.properties`
2. `/etc/libreffice/font-mappings.properties`
3. JAR 包内默认配置

## 文档

- [CLAUDE.md](CLAUDE.md) - 项目开发记忆
- [docs/](docs/) - 技术文档
- [CHANGELOG.md](CHANGELOG.md) - 详细更新日志

## 更新日志

### [1.0.0] - 2026-03-16

首次发布。让 LibreOffice Draw 能够打开 OFD 文件。

## 技术栈

- Java 17
- ofdrw 2.3.8（OFD 处理库）
- LibreOffice UNO 24.8.4
- Maven

## 许可证

MPL-2.0
