#!/bin/bash

# DMP iOS - Xcode安装脚本
# 功能：构建项目 -> 安装到iPhone -> 启动应用

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  DMP iOS - Xcode安装器${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 步骤1：检查Xcode
echo -e "${YELLOW}[1/4] 检查Xcode...${NC}"

if ! command -v xcodebuild &> /dev/null; then
    echo -e "${RED}❌ 未找到Xcode，请先安装Xcode${NC}"
    echo "   下载地址: https://developer.apple.com/xcode/"
    exit 1
fi

XCODE_VERSION=$(xcodebuild -version | head -n 1)
echo -e "${GREEN}✅ Xcode已安装: $XCODE_VERSION${NC}"
echo ""

# 步骤2：检查连接的设备
echo -e "${YELLOW}[2/4] 检查iOS设备...${NC}"

DEVICES=$(xcrun xctrace list devices 2>&1 | grep -v "Simulator" | grep "iPhone\|iPad")

if [ -z "$DEVICES" ]; then
    echo -e "${RED}❌ 未找到iOS设备${NC}"
    echo ""
    echo "请确保："
    echo "1. iPhone已通过USB连接到Mac"
    echo "2. iPhone已信任此电脑"
    echo "3. iPhone已开启开发者模式"
    echo ""
    echo "开启开发者模式："
    echo "设置 → 隐私与安全性 → 开发者模式 → 开启"
    exit 1
fi

echo -e "${GREEN}✅ 找到设备:${NC}"
echo "$DEVICES" | while read -r line; do
    echo "   $line"
done
echo ""

# 步骤3：构建项目
echo -e "${YELLOW}[3/4] 构建DMP项目...${NC}"

cd /Users/frieda.li/Desktop/pythonProject/DMP/ios

# 获取第一个真实设备
DEVICE_NAME=$(echo "$DEVICES" | head -n 1 | awk -F'(' '{print $1}' | sed 's/ *$//')

echo "目标设备: $DEVICE_NAME"
echo ""

# 使用xcodebuild构建
xcodebuild -project DMP.xcodeproj \
  -scheme DMP \
  -destination "platform=iOS,name=$DEVICE_NAME" \
  -configuration Debug \
  clean build

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 构建成功${NC}"
else
    echo -e "${RED}❌ 构建失败${NC}"
    exit 1
fi

echo ""

# 步骤4：安装到设备
echo -e "${YELLOW}[4/4] 安装到iPhone...${NC}"

# xcodebuild会自动安装到连接的设备
xcodebuild -project DMP.xcodeproj \
  -scheme DMP \
  -destination "platform=iOS,name=$DEVICE_NAME" \
  -configuration Debug \
  build

if [ $? -eq 0 ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  ✅ 安装成功！${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "📱 应用已安装到: $DEVICE_NAME"
    echo ""
    echo "下一步："
    echo "1. 在iPhone上打开DMP应用"
    echo "2. 配置API Key（如果需要）"
    echo "3. 开始使用"
    echo ""
    echo -e "${BLUE}💡 提示：${NC}"
    echo "   - 首次安装需要在iPhone上信任开发者"
    echo "   - 设置 → 通用 → VPN与设备管理 → 信任开发者"
    echo "   - Debug版本有效期7天"
    echo ""
else
    echo -e "${RED}❌ 安装失败${NC}"
    exit 1
fi