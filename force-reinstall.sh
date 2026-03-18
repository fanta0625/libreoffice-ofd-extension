#!/bin/bash
# 强制清理并重新安装 OFD Import Filter

set -e

# 解析命令行参数
BRAND="${1:-}"  # 可选的品牌参数

echo "=========================================================================="
echo "强制清理并重新安装 OFD Import Filter"
echo "=========================================================================="
echo ""

# 1. 查找并关闭所有 LibreOffice 进程
echo "步骤1: 强制关闭所有 LibreOffice 进程..."
echo "--------------------------------------------------------------------------"
pkill -9 soffice 2>/dev/null && echo "✅ 已关闭 LibreOffice" || echo "ℹ️  LibreOffice 未运行"
pkill -9 libreoffice 2>/dev/null || true
sleep 3

# 确认进程已关闭
if pgrep -x soffice > /dev/null; then
    echo "❌ 警告: LibreOffice 进程仍在运行！"
    ps aux | grep soffice | grep -v grep
    echo "请手动关闭所有 LibreOffice 窗口后重试"
    exit 1
fi
echo "✅ 确认 LibreOffice 已完全关闭"
echo ""

# 2. 卸载扩展
echo "步骤2: 卸载所有 OFD 相关扩展..."
echo "--------------------------------------------------------------------------"

# 查找并卸载所有 OFD 相关扩展
if command -v unopkg &> /dev/null; then
    unopkg list 2>/dev/null | grep -i "ofd" | while read -r line; do
        if [[ $line =~ Identifier:\ ([a-zA-Z0-9._-]+) ]]; then
            ext_id="${BASH_REMATCH[1]}"
            echo "发现扩展: $ext_id"
            unopkg remove "$ext_id" 2>/dev/null || true
        fi
    done

    # 特别处理我们可能的扩展ID
    unopkg remove org.loongoffice.ofd.reader 2>/dev/null || true
    unopkg remove org.loongoffice.ofd.reader.loongoffice 2>/dev/null || true
    unopkg remove org.loongoffice.ofd.filter 2>/dev/null || true
    unopkg remove org.openoffice.legacy.loongoffice-ofd-import.oxt 2>/dev/null || true
fi

echo "✅ 扩展清理完成"
echo ""

# 3. 清除 LibreOffice 缓存和配置
echo "步骤3: 清除 LibreOffice 缓存..."
echo "--------------------------------------------------------------------------"

# 备份配置
CONFIG_DIR=~/.config/libreoffice
if [ -d "$CONFIG_DIR" ]; then
    echo "发现 LibreOffice 配置目录: $CONFIG_DIR"

    # 查找所有版本目录
    for version_dir in "$CONFIG_DIR"/*/; do
        if [ -d "$version_dir" ]; then
            echo "处理: $version_dir"

            # 备份关键配置
            if [ -f "$version_dir/user/registrymodifications.xcu" ]; then
                cp "$version_dir/user/registrymodifications.xcu" \
                   "$version_dir/user/registrymodifications.xcu.bak.$(date +%s)" 2>/dev/null || true
                echo "  - 已备份 registrymodifications.xcu"
            fi

            # 删除缓存
            rm -f "$version_dir/user/registrymodifications.xcu" 2>/dev/null || true
            echo "  - 已清除缓存文件"

            # 清除扩展缓存
            if [ -d "$version_dir/user/uno_packages" ]; then
                rm -rf "$version_dir/user/uno_packages/cache" 2>/dev/null || true
                echo "  - 已清除扩展缓存"
            fi
        fi
    done
    echo "✅ 缓存清理完成"
else
    echo "ℹ️  未找到 LibreOffice 配置目录（正常，首次运行）"
fi
echo ""

# 4. 清除用户级扩展目录
echo "步骤4: 清除用户级扩展目录..."
echo "--------------------------------------------------------------------------"
UNO_DIR=~/.libreoffice
if [ -d "$UNO_DIR" ]; then
    echo "发现 UNO 扩展目录: $UNO_DIR"
    rm -rf "$UNO_DIR" 2>/dev/null || true
    echo "✅ UNO 扩展目录已清除"
else
    echo "ℹ️  UNO 扩展目录不存在"
fi
echo ""

# 5. 清除旧日志
echo "步骤5: 清除旧日志..."
echo "--------------------------------------------------------------------------"
rm -f /tmp/ofd-import-filter.log 2>/dev/null && echo "✅ 已清除旧日志" || echo "ℹ️  旧日志不存在"
echo ""

# 6. 构建项目
echo "步骤6: 构建项目..."
echo "--------------------------------------------------------------------------"

if [ -n "$BRAND" ]; then
    echo "构建品牌化版本: $BRAND"
    mvn clean package -DskipTests -P branded -Dbrand="$BRAND"
    OXT_FILE="target/$BRAND-ofd-reader.oxt"
    EXT_ID="org.loongoffice.ofd.reader.$BRAND"
else
    echo "构建默认版本"
    mvn clean package -DskipTests
    OXT_FILE="target/ofd-reader.oxt"
    EXT_ID="org.loongoffice.ofd.reader"
fi

echo ""

if [ ! -f "$OXT_FILE" ]; then
    echo "❌ 构建失败，未找到 OXT 文件: $OXT_FILE"
    exit 1
fi

echo "OXT 文件: $OXT_FILE"
echo "大小: $(du -h "$OXT_FILE" | cut -f1)"
echo "扩展标识符: $EXT_ID"
echo ""

echo "验证 OXT 包结构:"
unzip -t "$OXT_FILE" > /dev/null 2>&1 && echo "✅ OXT 包完整性验证通过" || {
    echo "❌ OXT 包损坏"
    exit 1
}
echo ""

echo "OXT 包内容:"
unzip -l "$OXT_FILE" | grep -E "\.(xcu|xml|jar)$" | head -20
echo ""

# 7. 重新安装扩展
echo "步骤7: 重新安装扩展..."
echo "--------------------------------------------------------------------------"
echo "正在安装: $OXT_FILE"
echo ""

if unopkg add --force "$OXT_FILE"; then
    echo ""
    echo "✅ 扩展安装成功！"
else
    echo ""
    echo "❌ 扩展安装失败！"
    echo "请检查上面的错误信息"
    exit 1
fi
echo ""

# 8. 验证安装
echo "步骤8: 验证安装..."
echo "--------------------------------------------------------------------------"
echo "已安装的 OFD 相关扩展:"
unopkg list | grep -A 10 -i "ofd" || echo "⚠️  未找到 OFD 相关扩展"
echo ""

echo "检查扩展详情:"
unopkg list | grep -B 2 -A 5 "$EXT_ID" || true
echo ""

# 9. 检查配置文件
echo "步骤9: 检查配置文件..."
echo "--------------------------------------------------------------------------"

echo "Filter.xcu 内容:"
unzip -p "$OXT_FILE" Filter.xcu | head -30
echo ""

echo "Types.xcu 内容:"
unzip -p "$OXT_FILE" Types.xcu | head -30
echo ""

# 10. 准备测试
echo "=========================================================================="
echo "安装完成！"
echo "=========================================================================="
echo ""
echo "扩展标识符: $EXT_ID"
echo "OXT 文件: $OXT_FILE"
echo ""

# 查找测试文件
TEST_OFD=$(find /home/loongson/projects/loongoffice/ofd_doc -name "*.ofd" 2>/dev/null | head -1)

if [ -z "$TEST_OFD" ]; then
    echo "⚠️  未找到测试 OFD 文件"
    echo "请提供一个 OFD 文件进行测试"
    exit 0
fi

echo "找到测试文件: $TEST_OFD"
echo "文件大小: $(du -h "$TEST_OFD" | cut -f1)"
echo ""

# 等待确认
read -p "按 Enter 键启动测试，或按 Ctrl+C 退出..."

# 启动测试
echo ""
echo "=========================================================================="
echo "开始测试..."
echo "=========================================================================="
echo ""

echo "正在启动 LibreOffice Draw 打开 $TEST_OFD ..."
libreoffice --draw "$TEST_OFD" &
SOFFICE_PID=$!

echo "LibreOffice PID: $SOFFICE_PID"
echo ""

# 等待 LibreOffice 启动
echo "等待 10 秒让 LibreOffice 完全启动..."
for i in {10..1}; do
    echo -n "$i... "
    sleep 1
done
echo ""
echo ""

# 检查日志
echo "=========================================================================="
echo "检查日志文件..."
echo "=========================================================================="
echo ""

LOG_FILE="/tmp/ofd-import-filter.log"

if [ -f "$LOG_FILE" ]; then
    echo "✅✅✅ 成功！日志文件已创建（说明过滤器被调用了）"
    echo ""
    echo "日志内容:"
    echo "--------------------------------------------------------------------------"
    tail -50 "$LOG_FILE"
    echo "--------------------------------------------------------------------------"
    echo ""
    echo "🎉🎉🎉 过滤器已成功调用！"
    echo ""
    echo "如果 LibreOffice 中还是显示错误，请检查日志中的错误信息"
else
    echo "❌ 日志文件不存在"
    echo ""
    echo "这说明过滤器仍然没有被调用！"
    echo ""
    echo "可能的原因："
    echo "1. 类型检测配置没有生效"
    echo "2. LibreOffice 没有重新加载配置"
    echo "3. 扩展注册有问题"
    echo ""
    echo "进一步调试步骤："
    echo "--------------------------------------------------------------------------"
    echo "1. 完全关闭 LibreOffice:"
    echo "   pkill -9 soffice"
    echo ""
    echo "2. 手动打开 LibreOffice 并检查扩展:"
    echo "   libreoffice &"
    echo "   工具 → 扩展管理器"
    echo "   应该看到: OFD Reader"
    echo ""
    echo "3. 在 LibreOffice 中手动打开 OFD 文件:"
    echo "   文件 → 打开 → 选择 OFD 文件"
    echo "   选择文件类型: Open Fixed-layout Document (*.ofd)"
    echo ""
    echo "4. 如果还是不行，查看 LibreOffice 日志:"
    echo "   journalctl --user -f | grep -i libreoffice"
    echo ""
fi

echo "=========================================================================="
echo "测试完成"
echo "=========================================================================="
