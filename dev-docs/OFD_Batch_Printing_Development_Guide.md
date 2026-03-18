# OFD 文档批量打印功能开发指南

> **文档类型**: 技术开发文档
> **调查日期**: 2025-03-05
> **版本**: 2.0
> **主题**: 基于 Java OXT 插件和 Python UNO 的 OFD 批量打印

---

## 📋 目录

- [一、核心概念：解耦架构](#一核心概念解耦架构)
- [二、角色分工](#二角色分工)
- [三、技术原理](#三技术原理)
- [四、OXT 插件开发指南](#四oxt-插件开发指南)
- [五、Python 批量打印指南](#五python-批量打印指南)
- [六、完整工作流程](#六完整工作流程)
- [七、验证测试](#七验证测试)
- [八、源码分析](#八源码分析)
- [九、常见问题](#九常见问题)
- [十、最佳实践](#十最佳实践)

---

## 一、核心概念：解耦架构

### 1.1 核心理念

**LibreOffice Filter 架构的设计目标：插件开发者和使用者完全解耦！**

```
┌─────────────────────────────────────────────────────────┐
│                  Python 批量打印代码                     │
│         • 不知道 OFD 的存在                              │
│         • 不调用 OFD 转换                                │
│         • 使用标准接口                                   │
│         • 无需修改代码                                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ loadComponentFromURL + print()
                     ↓
┌─────────────────────────────────────────────────────────┐
│              LibreOffice 核心（自动路由）                 │
│         • 检测文件类型                                   │
│         • 查找 Filter                                   │
│         • 调用插件                                       │
│         • 返回标准文档对象                               │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ 自动调用
                     ↓
┌─────────────────────────────────────────────────────────┐
│              Java OXT 插件（OFD Filter）                 │
│         • OFD → SVG 转换                                 │
│         • 对 Python 透明                                 │
│         • 返回标准文档对象                               │
└─────────────────────────────────────────────────────────┘
```

### 1.2 关键优势

✅ **Python 开发者无需知道 OFD 的存在**
✅ **OXT 插件开发者无需关心打印实现**
✅ **完全通过标准接口交互**
✅ **插件安装即用，无需代码修改**

### 1.3 工作流程示例

**场景：批量打印混合格式文档**

```python
# Python 代码（完全不需要修改）
files = [
    "report.odt",    # Writer 文档
    "slides.odp",    # Impress 演示
    "data.ods",      # Calc 表格
    "manual.pdf",    # PDF 文档
    "form.ofd"       # OFD 文档 ← OXT 插件处理
]

for file in files:
    doc = desktop.loadComponentFromURL(f"file:///{file}", "_blank", 0, ())
    doc.print()  # 所有文档同样的调用方式
    doc.dispose()

# 对 Python 来说，form.ofd 和 report.odt 完全一样！
```

---

## 二、角色分工

### 2.1 OXT 插件开发者（Java UNO）

**你的职责** ✅

1. **开发 OFD Filter OXT 插件**
   - 实现 `XFilter` 接口
   - 实现 `XImporter` 接口
   - 实现 `XExtendedFilterDetection` 接口

2. **OFD → SVG 转换**
   - 使用 ofdrw 库转换
   - 处理转换错误
   - 优化转换性能

3. **Filter 注册**
   - 注册 `.ofd` 扩展名
   - 定义 Filter Service
   - 配置文档类型

4. **打包和发布**
   - 打包为 `.oxt` 文件
   - 提供安装说明
   - 测试兼容性

**你不需要关心** ❌
- Python 批量打印的实现
- 打印机配置
- 批量处理逻辑
- 错误处理和日志（打印相关）

### 2.2 Python 开发者（你的同事）

**你同事的职责** ✅

1. **开发批量打印脚本**
   - 遍历文档列表
   - 调用标准接口
   - 处理打印结果
   - 日志和错误处理

2. **配置打印参数**
   - 选择打印机
   - 设置页码范围
   - 配置打印份数

3. **部署和运行**
   - 安装 OXT 插件
   - 配置 LibreOffice
   - 运行批量打印

**你同事不需要关心** ❌
- OFD 格式的存在
- OFD → SVG 转换
- Java OXT 插件的实现
- OFD 文档的特殊处理

### 2.3 交付物

**你交付的**：
```
YourOFDFilter.oxt          ← OXT 插件包
├── manifest.xml           ← 清单文件
├── OFDFilter.xcu          ← Filter 配置
└── OFDFilter.jar          ← Java 实现
    └── com/yourcompany/ofd/filter/
        └── OFDFilter.class
```

**你同事需要的**：
```bash
# 1. 安装插件（一次性）
unopkg add YourOFDFilter.oxt

# 2. 使用现有批量打印代码
python batch_print.py /path/to/ofd/files
```

---

## 三、技术原理

### 3.1 LibreOffice Filter 机制

**Filter 类型检测流程**：

```
1. 文件加载请求
   loadComponentFromURL("file:///doc.ofd", ...)
   ↓
2. 扩展名匹配
   .ofd → 查找 TypeRegistry
   ↓
3. Filter 选择
   ofd_OpenFixedDocument → OFDFilter
   ↓
4. Filter 执行
   OFDFilter.filter() → OFD → SVG
   ↓
5. 文档创建
   SVG → DrawingDocument
   ↓
6. 返回文档对象
   XComponent → Python
```

### 3.2 关键接口

**XFilter 接口**：
```java
public interface XFilter extends com.sun.star.uno.XInterface {
    boolean filter(PropertyValue[] aDescriptor);
    void cancel();
}
```

**XImporter 接口**：
```java
public interface XImporter extends com.sun.star.uno.XInterface {
    void setTargetDocument(XComponent Doc);
}
```

**XExtendedFilterDetection 接口**：
```java
public interface XExtendedFilterDetection extends com.sun.star.uno.XInterface {
    String detect(com.sun.star.util.URL[] aURL, String sTypeName);
}
```

### 3.3 转换透明性

**Python 看到的**：
```python
doc = desktop.loadComponentFromURL("file:///doc.ofd", "_blank", 0, ())
# ↓
# 对 Python 来说，这就是一个普通的 XComponent 对象
# 类型：DrawingDocument（Draw 文档）
# 可以打印、导出、编辑
```

**实际发生的**：
```java
// LibreOffice 内部自动执行
OFDFilter.filter() {
    String ofdPath = getPath(descriptor);
    byte[] svgData = OFDConverter.convert(ofdPath);  // OFD → SVG
    importSVG(svgData, targetDoc);  // SVG → Draw
    return true;
}
```

---

## 四、OXT 插件开发指南

### 4.1 项目结构

```
OFDFilter/
├── src/
│   └── com/
│       └── yourcompany/
│           └── ofd/
│               └── filter/
│                   ├── OFDFilter.java        ← 主 Filter 类
│                   ├── OFDConverter.java     ← 转换器
│                   └── SVGImporter.java      ← SVG 导入
├── META-INF/
│   └── manifest.xml                          ← 清单文件
├── OFDFilter.xcu                              ← Filter 配置
├── build.xml                                  ← Ant 构建脚本
└── README.md                                  ← 说明文档
```

### 4.2 Filter 实现

**OFDFilter.java**：

```java
package com.yourcompany.ofd.filter;

import com.sun.star.document.XFilter;
import com.sun.star.document.XImporter;
import com.sun.star.document.XExtendedFilterDetection;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.beans.PropertyValue;
import com.sun.star.lang.XComponent;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XInterface;

public class OFDFilter implements
    XFilter,
    XImporter,
    XExtendedFilterDetection,
    XServiceInfo {

    private XComponent targetDoc;
    private OFDConverter ofdConverter;

    // ========== XFilter 实现 ==========

    @Override
    public boolean filter(PropertyValue[] descriptor) {
        try {
            // 1. 获取输入 OFD 文件路径
            String inputPath = getInputPath(descriptor);
            System.out.println("[OFDFilter] Converting: " + inputPath);

            // 2. 使用 ofdrw 转换 OFD → SVG
            byte[] svgData = ofdConverter.convertToSvg(inputPath);

            if (svgData == null || svgData.length == 0) {
                System.err.println("[OFDFilter] Conversion failed: empty SVG");
                return false;
            }

            // 3. 将 SVG 导入到目标文档
            importSvgToDocument(svgData, targetDoc);

            System.out.println("[OFDFilter] Conversion completed successfully");
            return true;

        } catch (Exception e) {
            System.err.println("[OFDFilter] Conversion error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void cancel() {
        System.out.println("[OFDFilter] Operation cancelled");
    }

    // ========== XImporter 实现 ==========

    @Override
    public void setTargetDocument(XComponent doc) {
        this.targetDoc = doc;
        System.out.println("[OFDFilter] Target document set");
    }

    // ========== XExtendedFilterDetection 实现 ==========

    @Override
    public String detect(com.sun.star.util.URL[] URL, String typeName) {
        if (URL != null && URL.length > 0) {
            String url = URL[0].Complete;
            if (url != null && url.toLowerCase().endsWith(".ofd")) {
                System.out.println("[OFDFilter] Detected OFD file: " + url);
                return "ofd_OpenFixedDocument";
            }
        }
        return "";
    }

    // ========== XServiceInfo 实现 ==========

    @Override
    public String getImplementationName() {
        return "com.yourcompany.ofd.filter.OFDFilter";
    }

    @Override
    public String[] getSupportedServiceNames() {
        return new String[]{
            "com.sun.star.document.ImportFilter",
            "com.sun.star.document.ExtendedTypeDetection"
        };
    }

    @Override
    public boolean supportsService(String serviceName) {
        for (String supported : getSupportedServiceNames()) {
            if (supported.equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    // ========== 辅助方法 ==========

    private String getInputPath(PropertyValue[] descriptor) {
        for (PropertyValue prop : descriptor) {
            if ("InputURL".equals(prop.Name) || "URL".equals(prop.Name)) {
                String url = prop.Value.toString();
                // 移除 file:// 前缀
                if (url.startsWith("file://")) {
                    return url.substring(7);
                }
                return url;
            }
        }
        return null;
    }

    private void importSvgToDocument(byte[] svgData, XComponent doc) {
        try {
            // 这里需要实现 SVG 导入逻辑
            // 可以使用 LibreOffice 的 SVG 导入功能
            // 或者直接操作 Draw 文档

            // 简化版本：将 SVG 写入临时文件，然后导入
            String tempSvgFile = writeTempFile(svgData, ".svg");

            // 使用 LibreOffice 的 SVG 导入器
            // ... 实现细节

        } catch (Exception e) {
            throw new RuntimeException("Failed to import SVG", e);
        }
    }

    private String writeTempFile(byte[] data, String extension) {
        // 实现临时文件写入
        // ...
        return tempPath;
    }
}
```

### 4.3 Filter 配置

**OFDFilter.xcu**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<oor:component-data
    xmlns:oor="http://openoffice.org/2001/registry"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    oor:name="Filter"
    oor:package="org.openoffice.Office">

  <!-- ========== 类型定义 ========== -->
  <node oor:name="Types">
    <node oor:name="ofd_OpenFixedDocument" oor:op="replace">
      <prop oor:name="DetectService" oor:type="xs:string">
        <value>com.yourcompany.ofd.filter.OFDFilter</value>
      </prop>
      <prop oor:name="Extensions" oor:type="xs:string">
        <value>ofd</value>
      </prop>
      <prop oor:name="MediaType" oor:type="xs:string">
        <value>application/ofd</value>
      </prop>
      <prop oor:name="Preferred" oor:type="xs:boolean">
        <value>true</value>
      </prop>
      <prop oor:name="UIName" oor:type="xs:string">
        <value xml:lang="en-US">OFD Document</value>
        <value xml:lang="zh-CN">OFD 文档</value>
      </prop>
      <prop oor:name="ClipboardFormat" oor:type="xs:string">
        <value></value>
      </prop>
    </node>
  </node>

  <!-- ========== Filter 定义 ========== -->
  <node oor:name="Filters">
    <node oor:name="ofd_filter" oor:op="replace">
      <prop oor:name="Flags" oor:type="xs:string">
        <value>IMPORT ALIEN 3RDPARTYFILTER PREFERRED</value>
      </prop>
      <prop oor:name="FilterService" oor:type="xs:string">
        <value>com.yourcompany.ofd.filter.OFDFilter</value>
      </prop>
      <prop oor:name="TypeName" oor:type="xs:string">
        <value>ofd_OpenFixedDocument</value>
      </prop>
      <prop oor:name="DocumentService" oor:type="xs:string">
        <value>com.sun.star.drawing.DrawingDocument</value>
      </prop>
      <prop oor:name="UIName" oor:type="xs:string">
        <value xml:lang="en-US">OFD to SVG Converter</value>
        <value xml:lang="zh-CN">OFD 转 SVG 转换器</value>
      </prop>
      <prop oor:name="UserData" oor:type="xs:string">
        <value></value>
      </prop>
      <prop oor:name="FileFormatVersion" oor:type="xs:string">
        <value>0</value>
      </prop>
      <prop oor:name="FileFormatName" oor:type="xs:string">
        <value>Open Fixed-layout Document</value>
      </prop>
      <prop oor:name="TemplateName" oor:type="xs:string">
        <value></value>
      </prop>
    </node>
  </node>

</oor:component-data>
```

### 4.4 清单文件

**META-INF/manifest.xml**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest
    xmlns:manifest="http://openoffice.org/2001/manifest">

  <manifest:file-entry
      manifest:media-type="application/vnd.sun.star.configuration-data"
      manifest:full-path="OFDFilter.xcu"/>

  <manifest:file-entry
      manifest:media-type="application/vnd.sun.star.uno-components"
      manifest:full-path="OFDFilter.jar"/>

</manifest:manifest>
```

### 4.5 构建脚本

**build.xml**（Apache Ant）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project name="OFDFilter" default="oxt" basedir=".">

  <property name="src.dir" value="src"/>
  <property name="build.dir" value="build"/>
  <property name="dist.dir" value="dist"/>
  <property name="oxt.name" value="OFDFilter"/>

  <!-- LibreOffice 类路径 -->
  <path id="libreoffice.classpath">
    <fileset dir="/usr/lib/libreoffice/program/classes">
      <include name="*.jar"/>
    </fileset>
  </path>

  <!-- 编译 -->
  <target name="compile">
    <mkdir dir="${build.dir}"/>
    <javac srcdir="${src.dir}"
           destdir="${build.dir}"
           classpathref="libreoffice.classpath"
           includeantruntime="false"
           encoding="UTF-8">
      <include name="**/*.java"/>
    </javac>
  </target>

  <!-- 打包 JAR -->
  <target name="jar" depends="compile">
    <mkdir dir="${dist.dir}"/>
    <jar destfile="${dist.dir}/${oxt.name}.jar"
         basedir="${build.dir}">
      <manifest>
        <attribute name="RegistrationClassName"
                   value="com.yourcompany.ofd.filter.OFDFilter"/>
      </manifest>
    </jar>
  </target>

  <!-- 创建 OXT -->
  <target name="oxt" depends="jar">
    <zip destfile="${dist.dir}/${oxt.name}.oxt">
      <fileset file="OFDFilter.xcu"/>
      <fileset file="META-INF/manifest.xml"/>
      <fileset file="${dist.dir}/${oxt.name}.jar"/>
      <fileset file="README.md"/>
    </zip>
  </target>

  <!-- 清理 -->
  <target name="clean">
    <delete dir="${build.dir}"/>
    <delete dir="${dist.dir}"/>
  </target>

</project>
```

### 4.6 构建和打包

```bash
# 编译
ant clean oxt

# 输出
dist/OFDFilter.oxt  ← 这就是你交付的文件
```

---

## 五、Python 批量打印指南

### 5.1 核心原则

**Python 开发者需要记住的**：

1. **OFD 文档和普通文档完全一样**
2. **使用标准接口，无需特殊处理**
3. **只需确保 OXT 插件已安装**

### 5.2 环境准备

**安装 OXT 插件**（一次性）：

```bash
# 安装插件
unopkg add OFDFilter.oxt

# 验证安装
unopkg list --verbose | grep -i ofd

# 输出示例：
# Name: OFDFilter
# Registered in: /home/user/.libreoffice/4/user/uno_packages
```

### 5.3 基础打印代码

**单个文档打印**：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.view import XPrintable

def print_document(file_path, printer_name=None):
    """
    打印单个文档（支持任何格式，包括 OFD）

    Args:
        file_path: 文档路径
        printer_name: 打印机名称（可选）
    """
    # 连接到 LibreOffice
    local_context = uno.getComponentContext()
    resolver = local_context.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local_context)
    ctx = resolver.resolve("uno:socket,host=localhost,port=2083;urp;")
    desktop = ctx.ServiceManager.createInstanceWithContext(
        "com.sun.star.frame.Desktop", ctx)

    # 转换为文件 URL
    file_url = f"file://{os.path.abspath(file_path)}"

    # 加载文档（LibreOffice 自动选择合适的 Filter）
    doc = desktop.loadComponentFromURL(file_url, "_blank", 0, ())

    try:
        # 获取打印接口
        printable = XPrintable(doc)

        # 设置打印机（如果指定）
        if printer_name:
            printable.setPrinter((
                PropertyValue("Name", 0, printer_name, 0),
            ))

        # 打印
        printable.print(())
        print(f"✅ 打印成功: {file_path}")

    finally:
        # 释放文档
        doc.dispose()

# 使用示例
print_document("/path/to/document.ofd", printer_name="HP-Printer")
```

### 5.4 批量打印代码

**批量打印多个文档**：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import glob
import time
import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.view import XPrintable

def batch_print_documents(file_pattern, printer_name=None, delay=1.0):
    """
    批量打印文档（支持任何格式：ODT, ODP, ODS, PDF, OFD 等）

    Args:
        file_pattern: 文件模式（如 "/path/to/docs/*.ofd"）
        printer_name: 打印机名称
        delay: 文档间延迟（秒）
    """
    # 连接到 LibreOffice
    local_context = uno.getComponentContext()
    resolver = local_context.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local_context)
    ctx = resolver.resolve("uno:socket,host=localhost,port=2083;urp;")
    desktop = ctx.ServiceManager.createInstanceWithContext(
        "com.sun.star.frame.Desktop", ctx)

    # 获取文件列表
    files = glob.glob(file_pattern)
    files.sort()  # 按名称排序

    print(f"找到 {len(files)} 个文档")

    results = {"total": len(files), "success": 0, "failed": 0}

    # 批量打印
    for i, file_path in enumerate(files, 1):
        print(f"\n[{i}/{len(files)}] {os.path.basename(file_path)}")

        try:
            # 转换为文件 URL
            file_url = f"file://{os.path.abspath(file_path)}"

            # 加载文档（自动处理 OFD）
            doc = desktop.loadComponentFromURL(file_url, "_blank", 0, ())

            try:
                # 打印
                printable = XPrintable(doc)

                if printer_name:
                    printable.setPrinter((
                        PropertyValue("Name", 0, printer_name, 0),
                    ))

                printable.print(())

                print("  ✅ 成功")
                results["success"] += 1

            finally:
                doc.dispose()

            # 延迟
            if i < len(files) and delay > 0:
                time.sleep(delay)

        except Exception as e:
            print(f"  ❌ 失败: {e}")
            results["failed"] += 1

    # 打印统计
    print(f"\n{'='*60}")
    print(f"批量打印完成:")
    print(f"  总计: {results['total']} 个")
    print(f"  成功: {results['success']} 个")
    print(f"  失败: {results['failed']} 个")
    if results['total'] > 0:
        print(f"  成功率: {results['success']/results['total']*100:.1f}%")
    print(f"{'='*60}\n")

    return results

# 使用示例
if __name__ == "__main__":
    # 打印所有 OFD 文档
    batch_print_documents(
        "/path/to/ofd/files/*.ofd",
        printer_name="HP-Printer",
        delay=1.0
    )

    # 或者打印混合格式
    batch_print_documents(
        "/path/to/docs/*.*",  # 包括 ODT, ODP, PDF, OFD 等
        printer_name="HP-Printer"
    )
```

### 5.5 高级打印选项

**带打印参数的批量打印**：

```python
#!/usr/bin/env python3
import os
import glob
import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.view import XPrintable

def advanced_batch_print(files, printer_name=None, pages=None, copies=1):
    """
    高级批量打印（支持打印参数）

    Args:
        files: 文件列表
        printer_name: 打印机名称
        pages: 页码范围（如 "1-5;7;9-10"）
        copies: 打印份数
    """
    # 连接到 LibreOffice
    local_context = uno.getComponentContext()
    resolver = local_context.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local_context)
    ctx = resolver.resolve("uno:socket,host=localhost,port=2083;urp;")
    desktop = ctx.ServiceManager.createInstanceWithContext(
        "com.sun.star.frame.Desktop", ctx)

    for i, file_path in enumerate(files, 1):
        print(f"[{i}/{len(files)}] {os.path.basename(file_path)}")

        try:
            file_url = f"file://{os.path.abspath(file_path)}"
            doc = desktop.loadComponentFromURL(file_url, "_blank", 0, ())

            try:
                printable = XPrintable(doc)

                # 设置打印机
                if printer_name:
                    printable.setPrinter((
                        PropertyValue("Name", 0, printer_name, 0),
                    ))

                # 准备打印选项
                print_opts = []

                if pages:
                    print_opts.append(PropertyValue("Pages", 0, pages, 0))

                if copies > 1:
                    print_opts.append(PropertyValue("CopyCount", 0, copies, 0))

                # 打印
                printable.print(tuple(print_opts))
                print("  ✅ 成功")

            finally:
                doc.dispose()

        except Exception as e:
            print(f"  ❌ 失败: {e}")

# 使用示例
files = glob.glob("/path/to/ofd/files/*.ofd")
advanced_batch_print(
    files,
    printer_name="HP-Printer",
    pages="1-5",    # 只打印前 5 页
    copies=2        # 打印 2 份
)
```

### 5.6 完整命令行工具

**ofd_batch_print.py**：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
OFD 批量打印工具
支持 ODT, ODP, ODS, PDF, OFD 等所有格式
"""

import os
import sys
import time
import argparse
import getpass
from datetime import datetime

import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.view import XPrintable


class BatchPrinter:
    """批量打印器"""

    def __init__(self, host="localhost", port=2083):
        self.host = host
        self.port = port
        self.desktop = None

    def connect(self):
        """连接到 LibreOffice"""
        try:
            local_context = uno.getComponentContext()
            resolver = local_context.ServiceManager.createInstanceWithContext(
                "com.sun.star.bridge.UnoUrlResolver", local_context)
            ctx = resolver.resolve(
                f"uno:socket,host={self.host},port={self.port};urp;"
            )
            self.desktop = ctx.ServiceManager.createInstanceWithContext(
                "com.sun.star.frame.Desktop", ctx
            )
            print("✓ 已连接到 LibreOffice")
            return True
        except Exception as e:
            print(f"✗ 连接失败: {e}")
            return False

    def print_file(self, file_path, printer_name=None, pages=None, copies=1):
        """打印单个文件"""
        file_url = f"file://{os.path.abspath(file_path)}"

        doc = None
        try:
            # 加载文档（自动处理 OFD）
            doc = self.desktop.loadComponentFromURL(file_url, "_blank", 0, ())

            if not doc:
                raise Exception(f"无法加载文档: {file_path}")

            # 打印
            printable = XPrintable(doc)

            if printer_name:
                printable.setPrinter((
                    PropertyValue("Name", 0, printer_name, 0),
                ))

            print_opts = []
            if pages:
                print_opts.append(PropertyValue("Pages", 0, pages, 0))
            if copies > 1:
                print_opts.append(PropertyValue("CopyCount", 0, copies, 0))

            printable.print(tuple(print_opts))
            return True

        except Exception as e:
            print(f"  ✗ {e}")
            return False
        finally:
            if doc:
                doc.dispose()

    def batch_print(self, files, printer_name=None, pages=None, copies=1, delay=1.0):
        """批量打印"""
        print(f"\n开始批量打印 {len(files)} 个文档...\n")

        results = {"total": len(files), "success": 0, "failed": 0}

        for i, file_path in enumerate(files, 1):
            basename = os.path.basename(file_path)
            print(f"[{i}/{len(files)}] {basename}", end=" ")

            success = self.print_file(file_path, printer_name, pages, copies)

            if success:
                print("✓")
                results["success"] += 1
            else:
                results["failed"] += 1

            if i < len(files) and delay > 0:
                time.sleep(delay)

        # 打印统计
        print(f"\n{'='*60}")
        print(f"总计: {results['total']}")
        print(f"成功: {results['success']}")
        print(f"失败: {results['failed']}")
        if results['total'] > 0:
            print(f"成功率: {results['success']/results['total']*100:.1f}%")
        print(f"{'='*60}\n")

        return results


def main():
    parser = argparse.ArgumentParser(
        description="批量打印工具（支持 ODT, ODP, ODS, PDF, OFD 等所有格式）"
    )

    parser.add_argument("path", help="文件或目录路径")
    parser.add_argument("-p", "--printer", help="打印机名称")
    parser.add_argument("--pages", help="页码范围（如 '1-5;7;9-10'）")
    parser.add_argument("--copies", type=int, default=1, help="打印份数")
    parser.add_argument("--delay", type=float, default=1.0, help="文档间延迟（秒）")
    parser.add_argument("--host", default="localhost", help="LibreOffice 主机")
    parser.add_argument("--port", type=int, default=2083, help="LibreOffice 端口")

    args = parser.parse_args()

    # 收集文件
    if os.path.isfile(args.path):
        files = [args.path]
    else:
        files = []
        for root, dirs, filenames in os.walk(args.path):
            for filename in filenames:
                # 支持的扩展名
                if filename.lower().endswith(('.odt', '.odp', '.ods', '.pdf', '.ofd')):
                    files.append(os.path.join(root, filename))
        files.sort()

    if not files:
        print("未找到支持的文档")
        sys.exit(1)

    # 批量打印
    printer = BatchPrinter(host=args.host, port=args.port)
    if printer.connect():
        printer.batch_print(
            files,
            printer_name=args.printer,
            pages=args.pages,
            copies=args.copies,
            delay=args.delay
        )


if __name__ == "__main__":
    main()
```

**使用示例**：

```bash
# 打印单个 OFD 文档
python ofd_batch_print.py document.ofd

# 批量打印目录中的所有文档
python ofd_batch_print.py /path/to/docs -p "HP-Printer"

# 指定页码和份数
python ofd_batch_print.py document.ofd --pages "1-5" --copies 2
```

---

## 六、完整工作流程

### 6.1 开发流程

```
阶段 1：OXT 插件开发（你）
    ↓
├── 1.1 实现 OFDFilter 类
├── 1.2 编写 OFDFilter.xcu 配置
├── 1.3 创建 manifest.xml
├── 1.4 编译打包为 .oxt
└── 1.5 测试插件功能
    ↓
阶段 2：插件交付（你 → 同事）
    ↓
├── 2.1 提供OFDFilter.oxt文件
├── 2.2 提供安装说明
└── 2.3 提供测试文档
    ↓
阶段 3：插件安装（同事）
    ↓
├── 3.1 安装插件：unopkg add OFDFilter.oxt
├── 3.2 验证安装：unopkg list
└── 3.3 测试功能
    ↓
阶段 4：批量打印（同事）
    ↓
├── 4.1 编写批量打印脚本
├── 4.2 运行批量打印
└── 4.3 监控打印结果
```

### 6.2 数据流

```
用户操作
    ↓
Python 脚本：loadComponentFromURL("file:///doc.ofd")
    ↓
LibreOffice 核心
    ↓
[自动] 检测扩展名：.ofd
    ↓
[自动] 查找 Filter：OFDFilter
    ↓
[自动] 调用 OFDFilter.filter()
    ↓
OFDFilter 内部
    ↓
读取 OFD 文件
    ↓
调用 ofdrw 库
    ↓
转换 OFD → SVG
    ↓
导入 SVG 到 Draw 文档
    ↓
返回 XComponent 对象
    ↓
Python 收到文档对象
    ↓
Python：XPrintable.print()
    ↓
LibreOffice 打印系统
    ↓
[自动] 渲染 SVG 内容
    ↓
[自动] 发送到打印机
    ↓
打印完成
```

### 6.3 关键时间点

**T0：插件开发**
```
你：开发 OFDFilter.java
    配置 OFDFilter.xcu
    打包 OFDFilter.oxt
```

**T1：插件交付**
```
你：交付 OFDFilter.oxt 给同事
    提供 README.md
```

**T2：插件安装**
```
同事：unopkg add OFDFilter.oxt
    验证安装成功
```

**T3：批量打印**
```
同事：python batch_print.py *.ofd
    LibreOffice 自动调用 OFDFilter
    批量打印完成
```

**关键点**：T0-T2 只需要一次，T3 可以无限次使用！

---

## 七、验证测试

### 7.1 OXT 插件测试

**测试 1：Filter 注册验证**

```bash
# 检查插件是否安装
unopkg list --verbose | grep -i ofd

# 检查 Filter 配置
soffice --headless --accept="socket,host=localhost,port=2083;urp;" &
sleep 3

# Python 测试
python3 << EOF
import uno
local_context = uno.getComponentContext()
resolver = local_context.ServiceManager.createInstanceWithContext(
    "com.sun.star.bridge.UnoUrlResolver", local_context)
ctx = resolver.resolve("uno:socket,host=localhost,port=2083;urp;")
desktop = ctx.ServiceManager.createInstanceWithContext(
    "com.sun.star.frame.Desktop", ctx)

# 尝试加载 OFD
try:
    doc = desktop.loadComponentFromURL(
        "file:///path/to/test.ofd",
        "_blank",
        0,
        ()
    )
    print("✅ OFD Filter 工作正常！")
    doc.dispose()
except Exception as e:
    print(f"❌ 错误: {e}")
EOF
```

**测试 2：转换质量验证**

```bash
# 创建测试 OFD 文档
# 使用 LibreOffice GUI 打开
soffice /path/to/test.ofd

# 检查：
# 1. 是否能正确打开
# 2. 显示是否正确
# 3. 页面是否完整
```

### 7.2 Python 打印测试

**测试 3：基础打印测试**

```python
#!/usr/bin/env python3
import uno
from com.sun.star.view import XPrintable

# 连接
local_context = uno.getComponentContext()
resolver = local_context.ServiceManager.createInstanceWithContext(
    "com.sun.star.bridge.UnoUrlResolver", local_context)
ctx = resolver.resolve("uno:socket,host=localhost,port=2083;urp;")
desktop = ctx.ServiceManager.createInstanceWithContext(
    "com.sun.star.frame.Desktop", ctx)

# 测试单个文档
doc = desktop.loadComponentFromURL("file:///test.ofd", "_blank", 0, ())
printable = XPrintable(doc)
printable.print(())
doc.dispose()

print("✅ 打印测试通过")
```

**测试 4：批量打印测试**

```python
#!/usr/bin/env python3
import glob
from ofd_batch_print import BatchPrinter

# 测试批量打印
files = glob.glob("/path/to/test/ofds/*.ofd")

printer = BatchPrinter()
printer.connect()

results = printer.batch_print(files, delay=0.5)

assert results['success'] == len(files), "部分文档打印失败"
print("✅ 批量打印测试通过")
```

### 7.3 集成测试

**测试 5：混合格式测试**

```python
#!/usr/bin/env python3
import os
import glob

# 测试混合格式
test_files = [
    "test.odt",
    "test.odp",
    "test.pdf",
    "test.ofd"
]

for file in test_files:
    if os.path.exists(file):
        print(f"测试: {file}")
        doc = desktop.loadComponentFromURL(f"file:///{file}", "_blank", 0, ())
        printable = XPrintable(doc)
        printable.print(())
        doc.dispose()
        print(f"  ✅ 成功")
```

---

## 八、源码分析

### 8.1 关键源码文件

**Filter 接口定义**：

| 文件 | 路径 | 说明 |
|------|------|------|
| XFilter.idl | [`offapi/com/sun/star/document/XFilter.idl`](offapi/com/sun/star/document/XFilter.idl) | Filter 接口 |
| XImporter.idl | [`offapi/com/sun/star/document/XImporter.idl`](offapi/com/sun/star/document/XImporter.idl) | 导入器接口 |
| XExtendedFilterDetection.idl | [`offapi/com/sun/star/document/XExtendedFilterDetection.idl`](offapi/com/sun/star/document/XExtendedFilterDetection.idl) | 类型检测接口 |
| XPrintable.idl | [`offapi/com/sun/star/view/XPrintable.idl`](offapi/com/sun/star/view/XPrintable.idl) | 打印接口 |
| XComponentLoader.idl | [`offapi/com/sun/star/frame/XComponentLoader.idl`](offapi/com/sun/star/frame/XComponentLoader.idl) | 文档加载接口 |

**SVG Filter 实现**：

| 文件 | 路径 | 说明 |
|------|------|------|
| svgfilter.hxx | [`filter/source/svg/svgfilter.hxx`](filter/source/svg/svgfilter.hxx) | SVG Filter 头文件 |
| svgfilter.cxx | [`filter/source/svg/svgfilter.cxx`](filter/source/svg/svgfilter.cxx) | SVG Filter 实现 |
| svgexport.hxx | [`filter/source/svg/svgexport.hxx`](filter/source/svg/svgexport.hxx) | SVG 导出 |
| svgimport.hxx | [`filter/source/svg/svgimport.hxx`](filter/source/svg/svgimport.hxx) | SVG 导入 |

**类型检测**：

| 文件 | 路径 | 说明 |
|------|------|------|
| TypeDetection.cxx | [`filter/source/config/cache/typedetection.cxx`](filter/source/config/cache/typedetection.cxx) | 类型检测实现 |
| filtercache.hxx | [`filter/source/config/cache/filtercache.hxx`](filter/source/config/cache/filtercache.hxx) | Filter 缓存 |

**Python 示例**：

| 文件 | 路径 | 说明 |
|------|------|------|
| DocumentPrinter.py | [`instdir/sdk/examples/python/DocumentHandling/DocumentPrinter.py`](instdir/sdk/examples/python/DocumentHandling/DocumentPrinter.py) | 打印示例 |

### 8.2 Filter 调用链

```
Python: loadComponentFromURL("file:///doc.ofd")
    ↓
C++: SfxDesktop::loadComponentFromURL()
    ↓
C++: TypeDetection::queryTypeByDescriptor()
    ↓ 检查 .ofd 扩展名
    ↓
C++: FilterCache::getFilterByName("ofd_filter")
    ↓
C++: OFDFilter::filter()
    ↓
Java: OFDFilter.filter()
    ↓
Java: OFDConverter.convertToSvg()
    ↓ ofdrw 库
Java: SVGImporter.importSvg()
    ↓
C++: SVGReader::read()
    ↓
C++: SdXImpressDocument::initNew()
    ↓
返回 XComponent 给 Python
```

### 8.3 打印调用链

```
Python: XPrintable.print()
    ↓
C++: SfxBaseModel::print()
    ↓
C++: PrinterController::print()
    ↓
C++: PrinterController::printPage()
    ↓
C++: OutputDevice::Draw()
    ↓ 渲染 SVG 内容
C++: SalPrinter::StartJob()
    ↓
C++: Psps PSPrint (或系统打印机)
    ↓
打印输出
```

---

## 九、常见问题

### 9.1 OXT 插件问题

**Q1: 插件安装后无法加载 OFD 文档**

**A:** 检查清单：
```bash
# 1. 确认插件已安装
unopkg list | grep ofd

# 2. 检查配置
ls ~/.libreoffice/4/user/uno_packages/

# 3. 查看日志
soffice --headless --accept="socket,host=localhost,port=2083;urp;" &
tail -f ~/.libreoffice/4/user/Scripts/python/log.txt
```

**Q2: Filter 返回 false，加载失败**

**A:** 检查 Filter 实现：
```java
// 确保 filter() 方法返回 true
public boolean filter(PropertyValue[] descriptor) {
    try {
        // 转换逻辑
        return true;  // 必须返回 true
    } catch (Exception e) {
        e.printStackTrace();
        return false;  // 错误时返回 false
    }
}
```

**Q3: SVG 导入失败**

**A:** 确保 SVG 格式正确：
```java
// 验证 SVG 数据
if (svgData == null || svgData.length == 0) {
    return false;
}

// 使用 LibreOffice SVG 导入器
// 不要尝试手动解析
```

### 9.2 Python 打印问题

**Q4: 无法连接到 LibreOffice**

**A:** 确保 LibreOffice 监听器已启动：
```bash
# 启动监听器
soffice --headless --accept="socket,host=localhost,port=2083;urp;"

# 或使用系统服务
sudo systemctl start libreoffice-listener
```

**Q5: 打印质量不佳**

**A:** 调整转换质量：
```java
// 在 OFDConverter 中设置高质量
ofdConverter.setQuality(Quality.HIGH);
ofdConverter.setDPI(300);
```

**Q6: 批量打印时内存溢出**

**A:** 确保文档正确释放：
```python
try:
    doc = desktop.loadComponentFromURL(...)
    doc.print(...)
finally:
    doc.dispose()  # 必须释放！
```

### 9.3 性能问题

**Q7: 转换速度慢**

**A:** 实现缓存机制：
```java
// 在 OFDFilter 中
private static Map<String, byte[]> svgCache = new ConcurrentHashMap<>();

public boolean filter(PropertyValue[] descriptor) {
    String inputPath = getInputPath(descriptor);

    // 检查缓存
    byte[] svgData = svgCache.get(inputPath);
    if (svgData == null) {
        svgData = ofdConverter.convertToSvg(inputPath);
        svgCache.put(inputPath, svgData);
    }

    // 使用缓存的 SVG
    importSvg(svgData, targetDoc);
    return true;
}
```

**Q8: 批量打印太慢**

**A:** 使用多进程：
```python
from multiprocessing import Pool

def print_file(file_path):
    # 每个进程独立连接
    printer = BatchPrinter()
    printer.connect()
    printer.print_file(file_path)
    printer.disconnect()

# 并行打印
with Pool(processes=3) as pool:
    pool.map(print_file, files)
```

---

## 十、最佳实践

### 10.1 OXT 开发最佳实践

**✅ DO**:
1. 实现完整的 UNO 接口
   - XFilter, XImporter, XExtendedFilterDetection

2. 提供详细的日志
   ```java
   System.out.println("[OFDFilter] Converting: " + inputPath);
   ```

3. 处理所有异常
   ```java
   try {
       // 转换逻辑
   } catch (Exception e) {
       System.err.println("[OFDFilter] Error: " + e.getMessage());
       return false;
   }
   ```

4. 验证输入
   ```java
   if (inputPath == null || !new File(inputPath).exists()) {
       return false;
   }
   ```

5. 优化性能
   ```java
   // 使用缓存
   byte[] svgData = svgCache.computeIfAbsent(inputPath,
       k -> ofdConverter.convertToSvg(k)
   );
   ```

**❌ DON'T**:
1. 不要假设目标文档类型
2. 不要忽略返回值
3. 不要抛出未处理的异常
4. 不要使用硬编码路径
5. 不要忘记释放资源

### 10.2 Python 打印最佳实践

**✅ DO**:
1. 始终释放文档
   ```python
   try:
       doc = desktop.loadComponentFromURL(...)
       # 使用文档
   finally:
       doc.dispose()
   ```

2. 添加延迟避免过载
   ```python
   for file in files:
       print_file(file)
       time.sleep(1.0)  # 避免过载
   ```

3. 处理异常
   ```python
   try:
       doc.print()
   except Exception as e:
       print(f"打印失败: {e}")
   ```

4. 提供进度反馈
   ```python
   for i, file in enumerate(files, 1):
       print(f"[{i}/{len(files)}] {file}")
   ```

5. 验证连接
   ```python
   if not desktop:
       raise Exception("未连接到 LibreOffice")
   ```

**❌ DON'T**:
1. 不要忘记 dispose() 文档
2. 不要同时打开太多文档
3. 不要忽略打印错误
4. 不要假设文件存在
5. 不要硬编码打印机名称

### 10.3 部署最佳实践

**✅ DO**:
1. 提供完整的安装文档
2. 包含测试文档
3. 支持多平台
4. 提供版本信息
5. 记录变更日志

**❌ DON'T**:
1. 不要依赖特定路径
2. 不要假设用户权限
3. 不要忽略平台差异
4. 不要跳过测试

---

## 总结

### 核心要点

1. **完全解耦** 🎯
   - OXT 插件开发者（Java）和 Python 使用者完全独立
   - 通过标准接口（XFilter, XPrintable）交互
   - 插件安装即用，无需代码修改

2. **自动转换** 🔄
   - LibreOffice 自动检测文件类型
   - 自动调用合适的 Filter
   - OFD → SVG 转换对 Python 透明

3. **标准接口** 📐
   - 使用 LibreOffice 标准 UNO 接口
   - 无需特殊处理 OFD
   - 和普通文档完全一样

4. **一次安装，无限使用** ♻️
   - OXT 插件只需安装一次
   - 之后所有批量打印脚本自动支持 OFD
   - 无需任何额外配置

### 你的需求总结

**你需要做的**（OXT 插件开发）：
- ✅ 实现 `XFilter`、`XImporter`、`XExtendedFilterDetection` 接口
- ✅ 在 `filter()` 方法中调用 ofdrw 转换 OFD → SVG
- ✅ 配置 Filter 注册（`.ofd` 扩展名）
- ✅ 打包为 `.oxt` 文件
- ✅ 提供安装说明

**你同事需要做的**（Python 批量打印）：
- ✅ 安装你的 OXT 插件（一次性）
- ✅ 使用现有的批量打印代码
- ✅ **完全不需要知道 OFD 的存在！**
- ✅ **完全不需要修改任何代码！**

**工作原理**：
```python
# 你同事的代码（完全不需要修改）
doc = desktop.loadComponentFromURL("file:///doc.ofd", "_blank", 0, ())
# ↓ LibreOffice 自动调用你的 OFDFilter
# ↓ OFD → SVG 转换
# ↓ 返回 Draw 文档
doc.print()  # 直接打印！
```

### 关键文件

**OXT 插件**（你开发）：
```
OFDFilter.oxt
├── manifest.xml
├── OFDFilter.xcu
└── OFDFilter.jar
```

**Python 脚本**（你同事使用）：
```python
# 现有批量打印代码 - 无需修改！
def batch_print(files):
    for file in files:
        doc = load(file)
        doc.print()
        doc.dispose()
```

**这就是 LibreOffice Filter 架构的强大之处 - 完全透明，完全解耦！** 🎉

---

**文档版本**: 2.0
**最后更新**: 2025-03-05
**作者**: Claude Code 技术分析
