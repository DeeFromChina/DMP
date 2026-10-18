#!/bin/bash

# DMP Android - 生成APK安装二维码
# 功能：构建APK -> 启动HTTP服务器 -> 生成二维码 -> 手机扫码安装

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  DMP Android - APK安装二维码生成器${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 步骤1：构建Debug APK
echo -e "${YELLOW}[1/4] 构建Debug APK...${NC}"
cd /Users/frieda.li/Desktop/pythonProject/DMP/android
./gradlew assembleDebug --quiet

APK_PATH=$(ls app/build/outputs/apk/debug/DMP-*-debug.apk 2>/dev/null | head -1)

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ APK构建失败！${NC}"
    exit 1
fi

echo -e "${GREEN}✅ APK构建成功: $APK_PATH${NC}"
echo ""

# 步骤2：获取本地IP
echo -e "${YELLOW}[2/4] 获取本地IP地址...${NC}"

# macOS获取IP
LOCAL_IP=$(ipconfig getifaddr en0)

if [ -z "$LOCAL_IP" ]; then
    # 尝试en1（WiFi）
    LOCAL_IP=$(ipconfig getifaddr en1)
fi

if [ -z "$LOCAL_IP" ]; then
    echo -e "${RED}❌ 无法获取本地IP，请确保已连接WiFi${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 本地IP: $LOCAL_IP${NC}"
echo ""

# 步骤3：启动HTTP服务器
echo -e "${YELLOW}[3/4] 启动HTTP服务器...${NC}"

PORT=8888

# 创建临时服务器目录
TEMP_DIR=$(mktemp -d)
APK_FILENAME=$(basename "$APK_PATH")
cp "$APK_PATH" "$TEMP_DIR/$APK_FILENAME"

# 启动Python HTTP服务器（后台运行）
cd "$TEMP_DIR"
python3 -m http.server $PORT &
SERVER_PID=$!

APK_URL="http://${LOCAL_IP}:${PORT}/${APK_FILENAME}"

echo -e "${GREEN}✅ HTTP服务器已启动 (PID: $SERVER_PID)${NC}"
echo -e "${GREEN}   APK下载链接: $APK_URL${NC}"
echo ""

# 步骤4：生成二维码
echo -e "${YELLOW}[4/4] 生成安装二维码...${NC}"

# 检查是否安装了qrencode
if ! command -v qrencode &> /dev/null; then
    echo -e "${YELLOW}⚠️  未安装qrencode，使用Python生成二维码...${NC}"
    
    # 使用Python生成二维码
    python3 << EOF
import subprocess
import sys

try:
    import qrcode
except ImportError:
    print("安装qrcode库...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "qrcode[pil]", "pillow"])
    import qrcode

qr = qrcode.QRCode(
    version=1,
    error_correction=qrcode.constants.ERROR_CORRECT_H,
    box_size=10,
    border=4,
)
qr.add_data("$APK_URL")
qr.make(fit=True)

img = qr.make_image(fill_color="black", back_color="white")
img.save("/tmp/dmp_install_qr.png")
print("✅ 二维码已生成: /tmp/dmp_install_qr.png")
EOF
    
    QR_PATH="/tmp/dmp_install_qr.png"
else
    # 使用qrencode生成
    QR_PATH="/tmp/dmp_install_qr.png"
    qrencode -o "$QR_PATH" -s 10 -m 4 -l H "$APK_URL"
    echo -e "${GREEN}✅ 二维码已生成: $QR_PATH${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装说明${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "📱 手机扫码安装步骤："
echo ""
echo "1. 打开二维码图片："
echo "   open $QR_PATH"
echo ""
echo "2. 使用手机扫描二维码："
echo "   - 微信/QQ/浏览器扫码均可"
echo "   - 或直接在浏览器输入：$APK_URL"
echo ""
echo "3. 手机下载APK后点击安装"
echo "   - 如提示'未知来源'，请允许安装"
echo "   - 安卓手机：设置 → 应用设置 → 安装识别 → 允许"
echo ""
echo "4. 安装完成后即可使用"
echo ""
echo -e "${YELLOW}⚠️  注意事项：${NC}"
echo "   - 手机和电脑必须在同一WiFi网络"
echo "   - HTTP服务器会持续运行，按Ctrl+C停止"
echo "   - 安装完成后可关闭服务器"
echo ""
echo -e "${GREEN}========================================${NC}"
echo ""

# 打开二维码图片
open "$QR_PATH"

# 等待用户操作
echo -e "${YELLOW}按 Enter 键停止服务器并退出...${NC}"
read

# 停止服务器
kill $SERVER_PID 2>/dev/null || true
rm -rf "$TEMP_DIR"

echo -e "${GREEN}✅ 服务器已停止${NC}"