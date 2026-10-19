#!/bin/bash
# DMP API Demo 启动脚本

echo "======================================"
echo "  DMP API Demo 启动中..."
echo "======================================"
echo ""

# 检查 DMP 服务是否运行
echo "正在检查 DMP 服务状态..."
if curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
    echo "✅ DMP 服务正在运行"
else
    echo "⚠️  DMP 服务未运行，请先启动 DMP 服务:"
    echo "   python main.py"
    echo ""
fi

# 启动 Demo 服务
echo "启动 Demo 服务..."
echo "访问地址: http://localhost:8000"
echo ""

cd "$(dirname "$0")"
uvicorn app:app --host 127.0.0.1 --port 8000 --reload