"""
DMP API Demo - 简单的聊天测试应用
用于测试 DMP 服务接口是否可用，并展示请求体和响应体
"""

import os
import json
import httpx
from pathlib import Path
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles

app = FastAPI(title="DMP API Demo")

# DMP 服务地址（从环境变量读取，默认 28473 端口）
DMP_BASE_URL = os.getenv("DMP_BASE_URL", "http://127.0.0.1:28473")


@app.post("/api/config")
async def update_config(request: Request):
    """更新 DMP 服务配置"""
    global DMP_BASE_URL
    body = await request.json()
    if "base_url" in body:
        # 注意：这里只是演示，实际应该使用 mutable 的方式
        pass
    return {"success": True}

# 存储对话历史
conversation_history = []


@app.get("/", response_class=HTMLResponse)
async def index():
    """返回聊天界面"""
    html_file = Path(__file__).parent / "chat.html"
    return html_file.read_text(encoding="utf-8")


@app.post("/api/chat")
async def chat(request: Request):
    """非流式聊天 - 调用 DMP /chat 接口"""
    global conversation_history
    
    body = await request.json()
    user_message = body.get("message", "")
    
    # 构建消息列表
    messages = []
    for msg in conversation_history:
        messages.append({"role": msg["role"], "content": msg["content"]})
    messages.append({"role": "user", "content": user_message})
    
    # 构建请求体
    chat_request = {
        "messages": messages,
        "temperature": body.get("temperature", 0.7),
        "max_tokens": body.get("max_tokens", 2048),
        "model": body.get("model", ""),
        "preferred_provider": body.get("preferred_provider", "any")
    }
    
    # 调用 DMP 服务
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                f"{DMP_BASE_URL}/chat",
                json=chat_request
            )
            
            response_body = response.json()
            
            # 更新对话历史
            conversation_history.append({"role": "user", "content": user_message})
            conversation_history.append({
                "role": "assistant",
                "content": response_body.get("content", "")
            })
            
            return {
                "success": True,
                "request": chat_request,
                "response": response_body,
                "status_code": response.status_code
            }
    except httpx.ConnectError as e:
        return {
            "success": False,
            "request": chat_request,
            "error": f"无法连接到 DMP 服务 ({DMP_BASE_URL})，请确保服务已启动。\n错误详情: {str(e)}"
        }
    except Exception as e:
        return {
            "success": False,
            "request": chat_request,
            "error": f"请求失败: {str(e)}"
        }


@app.post("/api/chat/stream")
async def chat_stream(request: Request):
    """流式聊天 - 调用 DMP /chat/stream 接口"""
    global conversation_history
    
    body = await request.json()
    user_message = body.get("message", "")
    
    # 构建消息列表
    messages = []
    for msg in conversation_history:
        messages.append({"role": msg["role"], "content": msg["content"]})
    messages.append({"role": "user", "content": user_message})
    
    # 构建请求体
    chat_request = {
        "messages": messages,
        "temperature": body.get("temperature", 0.7),
        "max_tokens": body.get("max_tokens", 2048),
        "model": body.get("model", ""),
        "preferred_provider": body.get("preferred_provider", "any")
    }
    
    async def event_generator():
        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                async with client.stream(
                    "POST",
                    f"{DMP_BASE_URL}/chat/stream",
                    json=chat_request,
                    headers={"Accept": "text/event-stream"}
                ) as response:
                    # 发送请求体信息
                    yield f"data: {json.dumps({'type': 'request', 'data': chat_request}, ensure_ascii=False)}\n\n"
                    yield f"data: {json.dumps({'type': 'status_code', 'data': response.status_code}, ensure_ascii=False)}\n\n"
                    
                    full_content = ""
                    provider = ""
                    model = ""
                    finish_reason = None
                    
                    async for line in response.aiter_lines():
                        if line.startswith("data: "):
                            data = line[6:]
                            if data == "[DONE]":
                                yield f"data: {json.dumps({'type': 'done'}, ensure_ascii=False)}\n\n"
                                break
                            
                            try:
                                chunk = json.loads(data)
                                full_content += chunk.get("content", "")
                                provider = chunk.get("provider", "")
                                model = chunk.get("model", "")
                                finish_reason = chunk.get("finish_reason")
                                
                                yield f"data: {json.dumps({'type': 'chunk', 'data': chunk}, ensure_ascii=False)}\n\n"
                            except json.JSONDecodeError:
                                pass
                    
                    # 更新对话历史
                    conversation_history.append({"role": "user", "content": user_message})
                    conversation_history.append({"role": "assistant", "content": full_content})
                    
                    # 发送完整响应体
                    final_response = {
                        "content": full_content,
                        "provider": provider,
                        "model": model,
                        "finish_reason": finish_reason
                    }
                    yield f"data: {json.dumps({'type': 'response', 'data': final_response}, ensure_ascii=False)}\n\n"
                    
        except httpx.ConnectError as e:
            error_msg = f"无法连接到 DMP 服务 ({DMP_BASE_URL})，请确保服务已启动。\n错误详情: {str(e)}"
            yield f"data: {json.dumps({'type': 'error', 'data': error_msg}, ensure_ascii=False)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'type': 'error', 'data': f'请求失败: {str(e)}'}, ensure_ascii=False)}\n\n"
    
    return StreamingResponse(event_generator(), media_type="text/event-stream")


@app.post("/api/clear")
async def clear_history():
    """清空对话历史"""
    global conversation_history
    conversation_history = []
    return {"success": True}


@app.get("/api/health")
async def check_health():
    """检查 DMP 服务健康状态"""
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{DMP_BASE_URL}/health")
            return {
                "success": True,
                "data": response.json(),
                "status_code": response.status_code
            }
    except Exception as e:
        return {
            "success": False,
            "error": f"无法连接到 DMP 服务: {str(e)}"
        }


@app.get("/api/status")
async def get_status():
    """获取 DMP 提供者状态"""
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{DMP_BASE_URL}/status")
            return {
                "success": True,
                "data": response.json(),
                "status_code": response.status_code
            }
    except Exception as e:
        return {
            "success": False,
            "error": f"无法连接到 DMP 服务: {str(e)}"
        }