#!/usr/bin/env python3
"""
DMP Android - 多APK安装二维码生成器

生成三个APK的安装二维码：
1. DMP 主应用
2. DMP Demo HTTP
3. DMP Demo SDK

依赖：
pip install qrcode[pil] pillow
"""

import subprocess
import sys
import os
import socket
import threading
from pathlib import Path

try:
    import qrcode
except ImportError:
    print("安装qrcode库...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "qrcode[pil]", "pillow"])
    import qrcode

# 颜色定义
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
RED = '\033[0;31m'
BLUE = '\033[0;34m'
NC = '\033[0m'

def get_local_ip():
    """获取本地IP地址"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "localhost"

def generate_qr_code(url, output_path):
    """生成二维码"""
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=10,
        border=4,
    )
    qr.add_data(url)
    qr.make(fit=True)
    
    img = qr.make_image(fill_color="black", back_color="white")
    img.save(output_path)
    return output_path

def find_latest_apk(directory, pattern):
    """查找目录下匹配模式的最新APK文件"""
    apk_dir = Path(directory)
    apks = list(apk_dir.glob(pattern))
    if not apks:
        return None
    return max(apks, key=lambda p: p.stat().st_mtime)

def main():
    print(f"{GREEN}========================================{NC}")
    print(f"{GREEN}  DMP - 多APK安装二维码生成器{NC}")
    print(f"{GREEN}========================================{NC}")
    print()
    
    local_ip = get_local_ip()
    port = 8888
    
    # 查找最新的APK文件
    dmp_apk = find_latest_apk("/Users/frieda.li/Desktop/pythonProject/DMP/android/app/build/outputs/apk/debug", "DMP-*-debug.apk")
    http_apk = find_latest_apk("/Users/frieda.li/Desktop/pythonProject/DMP/demo/demo-http/app/build/outputs/apk/debug", "DMPDemo-HTTP-*-debug.apk")
    sdk_apk = find_latest_apk("/Users/frieda.li/Desktop/pythonProject/DMP/demo/demo-sdk/app/build/outputs/apk/debug", "DMPDemo-SDK-*-debug.apk")
    
    if not dmp_apk or not http_apk or not sdk_apk:
        print(f"{RED}❌ 未找到APK文件{NC}")
        if not dmp_apk:
            print(f"   缺少: DMP 主应用")
        if not http_apk:
            print(f"   缺少: DMP Demo HTTP")
        if not sdk_apk:
            print(f"   缺少: DMP Demo SDK")
        return
    
    # 复制到 /tmp 目录
    import shutil
    apk_dir_tmp = "/tmp"
    dmp_dest = Path(apk_dir_tmp) / dmp_apk.name
    http_dest = Path(apk_dir_tmp) / http_apk.name
    sdk_dest = Path(apk_dir_tmp) / sdk_apk.name
    
    shutil.copy2(dmp_apk, dmp_dest)
    shutil.copy2(http_apk, http_dest)
    shutil.copy2(sdk_apk, sdk_dest)
    
    # 定义三个APK
    apks = [
        {
            "name": "DMP 主应用",
            "path": str(dmp_dest),
            "qr_path": "/tmp/dmp_main_qr.png"
        },
        {
            "name": "DMP Demo HTTP",
            "path": str(http_dest),
            "qr_path": "/tmp/dmp_demo_http_qr.png"
        },
        {
            "name": "DMP Demo SDK",
            "path": str(sdk_dest),
            "qr_path": "/tmp/dmp_demo_sdk_qr.png"
        }
    ]
    
    # 验证APK文件存在
    for apk in apks:
        if not os.path.exists(apk["path"]):
            print(f"{RED}❌ 未找到: {apk['path']}{NC}")
            return
        size = os.path.getsize(apk["path"]) / (1024 * 1024)
        print(f"{GREEN}✅ {apk['name']}: {size:.1f} MB{NC}")
    
    print()
    
    # 生成二维码
    for apk in apks:
        filename = os.path.basename(apk["path"])
        url = f"http://{local_ip}:{port}/{filename}"
        generate_qr_code(url, apk["qr_path"])
        print(f"{BLUE}📱 {apk['name']} 二维码: {apk['qr_path']}{NC}")
        print(f"   下载链接: {url}")
        print()
    
    # 启动HTTP服务器
    print(f"{GREEN}========================================{NC}")
    print(f"{GREEN}  安装说明{NC}")
    print(f"{GREEN}========================================{NC}")
    print()
    print("1. 确保手机和电脑在同一WiFi网络")
    print("2. 用手机扫描对应APK的二维码")
    print("3. 下载完成后点击安装")
    print()
    print(f"{YELLOW}⚠️  安装顺序建议：{NC}")
    print("   1️⃣  先安装 DMP 主应用（提供服务和端口）")
    print("   2️⃣  再安装 DMP Demo HTTP（通过HTTP调用）")
    print("   3️⃣  最后安装 DMP Demo SDK（通过AIDL调用）")
    print()
    print(f"{GREEN}HTTP服务器已启动: http://{local_ip}:{port}{NC}")
    print("按 Ctrl+C 停止服务器")
    print()
    
    # 启动HTTP服务器
    import http.server
    import socketserver
    
    apk_dir = "/tmp"
    os.chdir(apk_dir)
    
    class QuietHandler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, format, *args):
            print(f"{YELLOW}📥 {args[0]}{NC}")
    
    with socketserver.TCPServer(("", port), QuietHandler) as httpd:
        httpd.serve_forever()

if __name__ == "__main__":
    main()