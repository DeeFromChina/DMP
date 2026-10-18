# 外部云端LLM接口文档

## 概述

本文档详细列出外部云端LLM提供者的接口信息，包括API端点、认证方式、模型列表、价格等。

---

## 1. OpenAI

### 1.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | OpenAI API |
| **文档地址** | https://platform.openai.com/docs/api-reference |
| **API端点** | https://api.openai.com/v1 |
| **认证方式** | Bearer Token (API Key) |
| **支持模型** | GPT-4o、GPT-4、GPT-3.5-Turbo等 |
| **接口格式** | OpenAI API (标准) |

### 1.2 可用模型列表

| 模型名称 | 上下文长度 | 最大输出 | 输入价格 | 输出价格 | 说明 |
|----------|------------|----------|----------|----------|------|
| gpt-4o | 128K | 16K | $2.50/1M tokens | $10.00/1M tokens | 最新多模态模型 |
| gpt-4o-mini | 128K | 16K | $0.15/1M tokens | $0.60/1M tokens | 轻量级版本 |
| gpt-4-turbo | 128K | 4K | $10.00/1M tokens | $30.00/1M tokens | 高性能版本 |
| gpt-3.5-turbo | 16K | 4K | $0.50/1M tokens | $1.50/1M tokens | 经济实惠 |
| o1-preview | 128K | 32K | $15.00/1M tokens | $60.00/1M tokens | 推理优化 |
| o1-mini | 128K | 65K | $3.00/1M tokens | $12.00/1M tokens | 轻量推理 |

### 1.3 接口列表

#### 1.3.1 聊天接口

**请求**:
```http
POST https://api.openai.com/v1/chat/completions
Content-Type: application/json
Authorization: Bearer sk-xxx

{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "你是一个助手"},
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048,
  "stream": false
}
```

**响应**:
```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！有什么可以帮助你的？",
        "refusal": null
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 20,
    "total_tokens": 35,
    "prompt_tokens_details": {
      "cached_tokens": 0
    }
  }
}
```

#### 1.3.2 流式聊天接口

**请求**:
```http
POST https://api.openai.com/v1/chat/completions
Content-Type: application/json
Authorization: Bearer sk-xxx

{
  "model": "gpt-4o",
  "messages": [...],
  "stream": true,
  "stream_options": {
    "include_usage": true
  }
}
```

**响应**:
```
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"你好"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"content":"！"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[],"finish_reason":"stop","usage":{"prompt_tokens":15,"completion_tokens":20,"total_tokens":35}}

data: [DONE]
```

#### 1.3.3 模型列表接口

**请求**:
```http
GET https://api.openai.com/v1/models
Authorization: Bearer sk-xxx
```

**响应**:
```json
{
  "object": "list",
  "data": [
    {
      "id": "gpt-4o",
      "object": "model",
      "created": 1234567890,
      "owned_by": "openai"
    },
    ...
  ]
}
```

### 1.4 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| RPM限制 | 500-10000 | 每分钟请求数（取决于层级） |
| TPM限制 | 50K-2M | 每分钟token数 |
| 最大token | 16K-32K | 单次请求最大输出token数 |
| 并发数 | 无限制 | 但受RPM/TPM限制 |

### 1.5 SDK支持

- Python: `openai`
- Node.js: `openai`
- Java: `com.openai:openai-java`
- Go: `github.com/openai/openai-go`

---

## 2. Anthropic Claude

### 2.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | Anthropic Claude API |
| **文档地址** | https://docs.anthropic.com/en/api/getting-started |
| **API端点** | https://api.anthropic.com |
| **认证方式** | x-api-key Header |
| **支持模型** | Claude 3.5 Sonnet、Claude 3 Opus、Claude 3 Haiku等 |
| **接口格式** | Anthropic API (自定义) |

### 2.2 可用模型列表

| 模型名称 | 上下文长度 | 最大输出 | 输入价格 | 输出价格 | 说明 |
|----------|------------|----------|----------|----------|------|
| claude-3-5-sonnet-20241022 | 200K | 8K | $3.00/1M tokens | $15.00/1M tokens | 最新最强模型 |
| claude-3-opus-20240229 | 200K | 4K | $15.00/1M tokens | $75.00/1M tokens | 最强大模型 |
| claude-3-sonnet-20240229 | 200K | 4K | $3.00/1M tokens | $15.00/1M tokens | 平衡性能/价格 |
| claude-3-haiku-20240307 | 200K | 4K | $0.25/1M tokens | $1.25/1M tokens | 快速便宜 |

### 2.3 接口列表

#### 2.3.1 消息接口

**请求**:
```http
POST https://api.anthropic.com/v1/messages
Content-Type: application/json
x-api-key: sk-ant-xxx
anthropic-version: 2023-06-01

{
  "model": "claude-3-5-sonnet-20241022",
  "max_tokens": 2048,
  "system": "你是一个助手",
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7
}
```

**响应**:
```json
{
  "id": "msg_xxx",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "text",
      "text": "你好！有什么可以帮助你的？"
    }
  ],
  "model": "claude-3-5-sonnet-20241022",
  "stop_reason": "end_turn",
  "stop_sequence": null,
  "usage": {
    "input_tokens": 15,
    "output_tokens": 20
  }
}
```

#### 2.3.2 流式消息接口

**请求**:
```http
POST https://api.anthropic.com/v1/messages
Content-Type: application/json
x-api-key: sk-ant-xxx
anthropic-version: 2023-06-01

{
  "model": "claude-3-5-sonnet-20241022",
  "max_tokens": 2048,
  "messages": [...],
  "stream": true
}
```

**响应**:
```
event: message_start
data: {"type":"message_start","message":{"id":"msg_xxx","type":"message","role":"assistant","content":[],"model":"claude-3-5-sonnet-20241022","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":15}}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"！"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":20}}

event: message_stop
data: {"type":"message_stop"}
```

### 2.4 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| RPM限制 | 50-1000 | 每分钟请求数（取决于层级） |
| 最大token | 8K | 单次请求最大输出token数 |
| 并发数 | 无限制 | 但受RPM限制 |

### 2.5 SDK支持

- Python: `anthropic`
- Node.js: `@anthropic-ai/sdk`
- Java: 社区SDK
- Go: 社区SDK

---

## 3. 通义千问 (阿里云)

### 3.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | 阿里云 DashScope (通义千问) |
| **文档地址** | https://help.aliyun.com/zh/dashscope/ |
| **API端点** | https://dashscope.aliyuncs.com |
| **认证方式** | Bearer Token (API Key) |
| **支持模型** | qwen-turbo、qwen-plus、qwen-max等 |
| **接口格式** | 自定义格式 / OpenAI兼容 |

### 3.2 可用模型列表

| 模型名称 | 上下文长度 | 最大输出 | 输入价格 | 输出价格 | 说明 |
|----------|------------|----------|----------|----------|------|
| qwen-turbo | 8K | 2K | ¥0.002/千token | ¥0.006/千token | 快速便宜 |
| qwen-plus | 32K | 8K | ¥0.004/千token | ¥0.012/千token | 平衡性能 |
| qwen-max | 8K | 2K | ¥0.04/千token | ¥0.12/千token | 最强模型 |
| qwen-max-longcontext | 32K | 8K | ¥0.04/千token | ¥0.12/千token | 长上下文 |
| qwen-vl-max | 8K | 2K | ¥0.02/千token | ¥0.06/千token | 多模态 |
| qwen-coder-plus | 64K | 8K | ¥0.008/千token | ¥0.024/千token | 代码生成 |

### 3.3 接口列表

#### 3.3.1 聊天接口 (自定义格式)

**请求**:
```http
POST https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
Content-Type: application/json
Authorization: Bearer sk-xxx
X-DashScope-SSE: disable

{
  "model": "qwen-turbo",
  "input": {
    "messages": [
      {"role": "system", "content": "你是一个助手"},
      {"role": "user", "content": "你好"}
    ]
  },
  "parameters": {
    "temperature": 0.7,
    "max_tokens": 2048
  }
}
```

**响应**:
```json
{
  "output": {
    "text": "你好！有什么可以帮助你的？",
    "finish_reason": "stop"
  },
  "usage": {
    "input_tokens": 15,
    "output_tokens": 20,
    "total_tokens": 35
  },
  "request_id": "xxx-xxx-xxx"
}
```

#### 3.3.2 流式聊天接口

**请求**:
```http
POST https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
Content-Type: application/json
Authorization: Bearer sk-xxx

{
  "model": "qwen-turbo",
  "input": {
    "messages": [...]
  },
  "parameters": {
    "incremental_output": true
  }
}
```

**响应**:
```
data:{"output":{"text":"你好","finish_reason":"null"},"usage":{"input_tokens":15,"output_tokens":1}}

data:{"output":{"text":"！","finish_reason":"null"},"usage":{"input_tokens":15,"output_tokens":2}}

data:{"output":{"text":"","finish_reason":"stop"},"usage":{"input_tokens":15,"output_tokens":20}}
```

#### 3.3.3 OpenAI兼容接口

**请求**:
```http
POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
Content-Type: application/json
Authorization: Bearer sk-xxx

{
  "model": "qwen-turbo",
  "messages": [...],
  "temperature": 0.7,
  "max_tokens": 2048
}
```

**响应**: OpenAI格式（同OpenAI API）

### 3.4 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| QPS限制 | 50-500 | 每秒请求数（取决于模型） |
| 并发数 | 50-200 | 同时进行的请求数 |
| 最大token | 2K-8K | 单次请求最大输出token数 |
| 日调用量 | 不限 | 但受QPS限制 |

### 3.5 SDK支持

- Python: `dashscope`
- Java: `com.alibaba:dashscope-sdk-java`
- Node.js: 社区SDK

---

## 4. 其他云端LLM (未来支持)

### 4.1 Google Gemini

| 项目 | 说明 |
|------|------|
| **API端点** | https://generativelanguage.googleapis.com |
| **认证方式** | API Key |
| **支持模型** | gemini-1.5-pro、gemini-1.5-flash |
| **状态** | 待实现 |

### 4.2 百度文心一言

| 项目 | 说明 |
|------|------|
| **API端点** | https://aip.baidubce.com |
| **认证方式** | API Key + Secret Key |
| **支持模型** | ernie-bot、ernie-bot-4 |
| **状态** | 待实现 |

### 4.3 智谱AI

| 项目 | 说明 |
|------|------|
| **API端点** | https://open.bigmodel.cn |
| **认证方式** | API Key |
| **支持模型** | glm-4、glm-3-turbo |
| **状态** | 待实现 |

### 4.4 MiniMax

| 项目 | 说明 |
|------|------|
| **API端点** | https://api.minimax.chat |
| **认证方式** | API Key |
| **支持模型** | abab6、abab5.5 |
| **状态** | 待实现 |

---

## 5. 对比总结

### 5.1 功能对比

| 特性 | OpenAI | Claude | 通义千问 |
|------|--------|--------|----------|
| 多模态 | ✅ | ✅ | ✅ |
| 流式输出 | ✅ | ✅ | ✅ |
| 函数调用 | ✅ | ✅ | ✅ |
| 长上下文 | 128K | 200K | 32K |
| 中文能力 | 好 | 好 | 优秀 |
| 代码能力 | 优秀 | 优秀 | 好 |

### 5.2 价格对比 (每1M tokens)

| 提供者 | 输入价格 | 输出价格 | 性价比 |
|--------|----------|----------|--------|
| OpenAI GPT-4o | $2.50 | $10.00 | 中等 |
| OpenAI GPT-4o-mini | $0.15 | $0.60 | 高 |
| Claude 3.5 Sonnet | $3.00 | $15.00 | 中等 |
| Claude 3 Haiku | $0.25 | $1.25 | 高 |
| 通义千问 qwen-turbo | ¥0.002/千 | ¥0.006/千 | 最高 |
| 通义千问 qwen-plus | ¥0.004/千 | ¥0.012/千 | 高 |

### 5.3 性能对比

| 指标 | OpenAI | Claude | 通义千问 |
|------|--------|--------|----------|
| 响应时间 | 1-2s | 1-3s | 0.5-1s |
| 推理速度 | 50-100 tok/s | 40-80 tok/s | 60-120 tok/s |
| 可用性 | 99.9% | 99.9% | 99.5% |

---

## 6. 接入指南

### 6.1 OpenAI接入

1. 注册OpenAI账号 (https://platform.openai.com)
2. 创建API Key
3. 安装SDK: `pip install openai`
4. 调用API

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")
response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "你好"}]
)
print(response.choices[0].message.content)
```

### 6.2 Claude接入

1. 注册Anthropic账号 (https://console.anthropic.com)
2. 创建API Key
3. 安装SDK: `pip install anthropic`
4. 调用API

```python
from anthropic import Anthropic

client = Anthropic(api_key="sk-ant-xxx")
response = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    max_tokens=2048,
    messages=[{"role": "user", "content": "你好"}]
)
print(response.content[0].text)
```

### 6.3 通义千问接入

1. 注册阿里云账号
2. 开通DashScope服务
3. 创建API Key
4. 安装SDK: `pip install dashscope`
5. 调用API

```python
import dashscope

dashscope.api_key = "sk-xxx"
response = dashscope.Generation.call(
    model="qwen-turbo",
    messages=[{"role": "user", "content": "你好"}]
)
print(response.output.text)
```

---

## 7. 常见问题

### Q1: 哪个云端LLM性价比最高？

A: 通义千问的qwen-turbo性价比最高，价格仅为OpenAI的1/10。

### Q2: 哪个云端LLM中文能力最强？

A: 通义千问的中文能力最强，其次是Claude和OpenAI。

### Q3: 如何选择模型？

A: 
- 简单任务：GPT-4o-mini / Claude Haiku / qwen-turbo
- 通用任务：GPT-4o / Claude Sonnet / qwen-plus
- 复杂任务：GPT-4o / Claude Opus / qwen-max

### Q4: 流式输出支持情况？

A: 所有主流云端LLM都支持流式输出。

### Q5: 如何优化API调用成本？

A: 
- 使用更小的模型
- 缓存常见请求的响应
- 使用更短的prompt
- 批量处理请求