#!/bin/bash

# SmartBill Android App 构建脚本
# 此脚本用于编译生成APK文件

echo "================================"
echo "SmartBill 构建脚本"
echo "================================"
echo ""

# 检查是否在正确的目录
if [ ! -f "settings.gradle" ]; then
    echo "错误: 请在SmartBill项目根目录下运行此脚本"
    exit 1
fi

# 清理之前的构建
echo "1. 清理之前的构建..."
./gradlew clean

# 构建Debug版本APK
echo ""
echo "2. 开始构建Debug版本APK..."
./gradlew assembleDebug

# 检查构建是否成功
if [ $? -eq 0 ]; then
    echo ""
    echo "================================"
    echo "✓ 构建成功！"
    echo "================================"
    echo ""
    echo "APK文件位置:"
    echo "app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "你可以将此APK文件传输到手机上安装"
else
    echo ""
    echo "================================"
    echo "✗ 构建失败"
    echo "================================"
    echo ""
    echo "请检查错误信息并修复问题后重新运行"
    exit 1
fi
