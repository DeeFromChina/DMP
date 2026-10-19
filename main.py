#!/usr/bin/env python3
"""
DMP 服务启动脚本

用法:
    python main.py                    # 启动 HTTP 服务（默认端口 28473）
    python main.py --port 9090        # 指定端口
    python main.py --mcp              # 启动 MCP 服务（非 HTTP）
"""

import argparse
import sys
from pathlib import Path

# 添加项目根目录到 Python 路径
sys.path.insert(0, str(Path(__file__).parent))

from dmp.config import Settings
from dmp.server import create_server, create_http_server, run_http_server
from dmp.fallback import FallbackManager


def main():
    parser = argparse.ArgumentParser(description="DMP (Device Model Protocol) Server")
    parser.add_argument("--port", type=int, default=None, help="HTTP 服务端口（默认 28473）")
    parser.add_argument("--host", type=str, default=None, help="监听地址（默认 127.0.0.1）")
    parser.add_argument("--mcp", action="store_true", help="启动 MCP 服务（非 HTTP）")
    
    args = parser.parse_args()
    
    # 创建配置
    settings = Settings()
    if args.port:
        settings.server_port = args.port
    if args.host:
        settings.server_host = args.host
    
    if args.mcp:
        # 启动 MCP 服务
        print("Starting DMP MCP server...")
        mcp, fallback_manager = create_server(settings)
        mcp.run()
    else:
        # 启动 HTTP 服务
        print(f"Starting DMP HTTP server on {settings.server_host}:{settings.server_port}...")
        
        # 创建回退管理器
        fallback_manager = FallbackManager(settings)
        
        # 注册提供者
        if settings.openai.enabled:
            from dmp.providers.cloud.openai_provider import OpenAILLMProvider
            from dmp.providers.registry import registry
            registry.register(OpenAILLMProvider(settings.openai))
            print(f"Registered OpenAI provider")
        
        if settings.claude.enabled:
            from dmp.providers.cloud.claude_provider import ClaudeLLMProvider
            from dmp.providers.registry import registry
            registry.register(ClaudeLLMProvider(settings.claude))
            print(f"Registered Claude provider")
        
        if settings.tongyi.enabled:
            from dmp.providers.cloud.tongyi_provider import TongyiLLMProvider
            from dmp.providers.registry import registry
            registry.register(TongyiLLMProvider(settings.tongyi))
            print(f"Registered Tongyi provider")
        
        # 创建 HTTP 服务器
        http_app, _, actual_port = create_http_server(fallback_manager, settings)
        
        print(f"DMP HTTP server is running on http://{settings.server_host}:{actual_port}")
        print(f"Health check: http://{settings.server_host}:{actual_port}/health")
        print(f"API docs: http://{settings.server_host}:{actual_port}/docs")
        
        # 运行服务器
        run_http_server(http_app, host=settings.server_host, port=actual_port)


if __name__ == "__main__":
    main()