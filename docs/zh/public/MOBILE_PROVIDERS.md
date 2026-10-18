# 手机厂商LLM接口文档

## 概述

本文档详细列出各手机厂商提供的LLM接口，包括API端点、认证方式、请求/响应格式等。

---

## 1. 华为 HMS Core AI (盘古大模型)

### 1.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | 华为云 ModelArts Studio / 盘古大模型 |
| **文档地址** | https://support.huaweicloud.com/api-pangulm/ |
| **API端点** | https://pangu.myhuaweicloud.com |
| **认证方式** | Token / AK/SK |
| **支持模型** | 盘古NLP、盘古多模态、盘古代码等 |
| **接口格式** | OpenAI兼容格式 (V2) / 自定义格式 (V1) |

### 1.2 支持的手机型号

| 系列 | 型号 | 支持能力 |
|------|------|----------|
| Mate系列 | Mate 60/70系列 | 完整API访问 |
| P系列 | P60/P70系列 | 完整API访问 |
| Nova系列 | Nova 12/13系列 | 完整API访问 |
| 折叠屏 | Mate X5/X6 | 完整API访问 |

### 1.3 可用接口列表

#### 1.3.1 V2推理接口 (OpenAI兼容)

**接口说明**: 兼容OpenAI API格式，便于迁移。

**请求**:
```http
POST https://{endpoint}/api/v2/chat/completions
Content-Type: application/json
Authorization: Bearer {token}

{
  "model": "pangu-nlp-n1-32k",
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
  "model": "pangu-nlp-n1-32k",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！有什么可以帮助你的？"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

**流式响应**:
```json
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1234567890,"model":"pangu-nlp-n1-32k","choices":[{"index":0,"delta":{"role":"assistant","content":"你好"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1234567890,"model":"pangu-nlp-n1-32k","choices":[{"index":0,"delta":{"content":"！"},"finish_reason":null}]}

data: [DONE]
```

#### 1.3.2 V1推理接口 (自定义格式)

**接口说明**: 华为自定义格式，需要IAM Token认证。

**请求**:
```http
POST https://{endpoint}/v1/{project_id}/infer/{deployment_id}
Content-Type: application/json
X-Auth-Token: {token}

{
  "inputs": [
    {"role": "system", "content": "你是一个助手"},
    {"role": "user", "content": "你好"}
  ],
  "parameters": {
    "temperature": 0.7,
    "max_tokens": 2048
  }
}
```

#### 1.3.3 Token获取接口

**接口说明**: 获取IAM Token用于API认证。

**请求**:
```http
POST https://iam.myhuaweicloud.com/v3/auth/tokens
Content-Type: application/json

{
  "auth": {
    "identity": {
      "methods": ["password"],
      "password": {
        "user": {
          "name": "{username}",
          "password": "{password}",
          "domain": {
            "name": "{domain_name}"
          }
        }
      }
    },
    "scope": {
      "project": {
        "name": "{project_name}"
      }
    }
  }
}
```

**响应**: Token在响应Header的`X-Subject-Token`字段中。

### 1.4 可用模型列表

| 模型名称 | 参数量 | 上下文长度 | 适用场景 | 价格 |
|----------|--------|------------|----------|------|
| pangu-nlp-n1-8k | 7B | 8K | 通用对话 | ¥0.01/千token |
| pangu-nlp-n1-32k | 7B | 32K | 长文本对话 | ¥0.015/千token |
| pangu-code | 7B | 8K | 代码生成 | ¥0.02/千token |
| pangu-multimodal | 13B | 4K | 多模态 | ¥0.03/千token |

### 1.5 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| QPS限制 | 100 | 每秒请求数 |
| 并发数 | 50 | 同时进行的请求数 |
| 最大token | 8192 | 单次请求最大token数 |
| 日调用量 | 100万 | 每日最大调用量 |

### 1.6 SDK支持

- Python SDK: `huaweicloudsdkcore`
- Java SDK: `huaweicloud-sdk-java`
- 其他语言: 通过REST API调用

---

## 2. 小米 MiMo 大模型

### 2.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | Xiaomi MiMo API |
| **文档地址** | https://mimo.mi.com/docs/zh-CN/ |
| **API端点** | https://mimo-api.mi.com |
| **认证方式** | API Key + Secret Key |
| **支持模型** | MiMo、MiMo-Pro等 |
| **接口格式** | 兼容OpenAI API / Anthropic API |

### 2.2 支持的手机型号

| 系列 | 型号 | 支持能力 |
|------|------|----------|
| 数字系列 | Xiaomi 14/15系列 | 完整API访问 |
| MIX系列 | MIX Fold/Flip | 完整API访问 |
| Civi系列 | Civi 4/5 | 完整API访问 |
| Redmi | K70/K80系列 | 完整API访问 |

### 2.3 可用接口列表

#### 2.3.1 聊天接口 (OpenAI兼容)

**请求**:
```http
POST https://mimo-api.mi.com/v1/chat/completions
Content-Type: application/json
X-API-Key: {api_key}
X-Secret-Key: {secret_key}

{
  "model": "mimo",
  "messages": [
    {"role": "system", "content": "You are MiMo, an AI assistant developed by Xiaomi."},
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
  "id": "chatcmpl-mimo-xxx",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "mimo",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！我是MiMo，有什么可以帮助你的？"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 25,
    "total_tokens": 40
  }
}
```

#### 2.3.2 流式聊天接口

**请求**:
```http
POST https://mimo-api.mi.com/v1/chat/completions
Content-Type: application/json
X-API-Key: {api_key}
X-Secret-Key: {secret_key}

{
  "model": "mimo",
  "messages": [...],
  "stream": true
}
```

**响应**:
```
data: {"id":"chatcmpl-mimo-xxx","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}

data: {"id":"chatcmpl-mimo-xxx","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"！"},"finish_reason":null}]}

data: [DONE]
```

#### 2.3.3 健康检查接口

**请求**:
```http
GET https://mimo-api.mi.com/v1/health
X-API-Key: {api_key}
X-Secret-Key: {secret_key}
```

**响应**:
```json
{
  "status": "ok",
  "model": "mimo",
  "version": "1.0.0"
}
```

### 2.4 可用模型列表

| 模型名称 | 参数量 | 上下文长度 | 适用场景 | 价格 |
|----------|--------|------------|----------|------|
| mimo | 7B | 32K | 通用对话 | 免费(限流) |
| mimo-pro | 70B | 128K | 复杂任务 | ¥0.02/千token |
| mimo-vision | 7B | 8K | 多模态 | ¥0.03/千token |

### 2.5 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| QPS限制 | 50 (免费) / 500 (付费) | 每秒请求数 |
| 最大token | 4096 | 单次请求最大token数 |
| 日调用量 | 1000 (免费) / 不限 (付费) | 每日最大调用量 |

---

## 3. Apple Core ML (本地模型)

### 3.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | Apple Core ML |
| **文档地址** | https://developer.apple.com/documentation/coreml |
| **API端点** | 本地调用，无网络API |
| **认证方式** | 无需认证 (本地运行) |
| **支持模型** | Llama 3.1、Gemma、Phi等 (Core ML格式) |
| **接口格式** | Core ML API (Swift/Objective-C) |

### 3.2 支持的手机型号

| 系列 | 型号 | 芯片 | 支持模型 |
|------|------|------|----------|
| iPhone 15 Pro | A17 Pro | Llama 3.1 8B、Gemma 2B |
| iPhone 16系列 | A18/A18 Pro | Llama 3.1 8B、Gemma 7B |
| iPhone 15 | A16 | Llama 3.2 3B、Phi 3 |
| iPhone 14 | A15 | Llama 3.2 1B、Phi 2 |
| iPad Pro | M系列 | Llama 3.1 8B、Gemma 7B |

### 3.3 可用接口列表

#### 3.3.1 Core ML模型加载

**Swift代码**:
```swift
import CoreML

// 加载模型
let config = MLModelConfiguration()
config.computeUnits = .all  // 使用CPU+GPU+Neural Engine

let model = try MLModel(
    contentsOf: Bundle.main.url(forResource: "Llama3_8B", withExtension: "mlmodelc")!,
    configuration: config
)
```

#### 3.3.2 模型推理

**Swift代码**:
```swift
// 准备输入
let input = try MLDictionaryFeatureProvider(dictionary: [
    "text": MLMultiArray(string: "你好", dataType: .int32)
])

// 执行推理
let output = try model.prediction(from: input)

// 获取结果
if let outputDict = output.featureValue(for: "text")?.dictionaryValue {
    let response = outputDict["text"]?.stringValue
    print(response)
}
```

#### 3.3.3 模型转换

**命令行**:
```bash
# 使用coremltools转换模型
pip install coremltools

python -c "
import coremltools as ct
import torch

# 加载PyTorch模型
model = torch.load('llama3_8b.pt')

# 转换为Core ML格式
mlmodel = ct.convert(
    model,
    inputs=[ct.TensorType(shape=(1, 1024))],
    convert_to='mlprogram'
)

# 保存
mlmodel.save('Llama3_8B.mlpackage')
"
```

### 3.4 可用模型列表

| 模型名称 | 参数量 | 模型大小 | 推理速度 | 内存占用 |
|----------|--------|----------|----------|----------|
| Llama 3.2 1B | 1B | 1.2GB | 50 tokens/s | 2GB |
| Llama 3.2 3B | 3B | 2.5GB | 30 tokens/s | 4GB |
| Llama 3.1 8B | 8B | 5GB | 15 tokens/s | 8GB |
| Gemma 2B | 2B | 1.5GB | 40 tokens/s | 3GB |
| Phi 3 Mini | 3.8B | 2.8GB | 25 tokens/s | 4GB |

### 3.5 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| 内存限制 | 设备可用内存 | 大模型需要更多内存 |
| 推理速度 | 取决于芯片 | A17 Pro最快 |
| 模型大小 | < 设备内存50% | 避免OOM |
| 电池消耗 | 较高 | 长时间推理耗电 |

---

## 4. OPPO AI开放平台

### 4.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | OPPO AI开放平台 |
| **文档地址** | https://open.oppomobile.com/documentation/ |
| **API端点** | https://api.openai.heytap.com |
| **认证方式** | OAuth 2.0 (client_id + client_secret) |
| **支持模型** | OPPO LM、多模态模型 |
| **接口格式** | OpenAI兼容格式 |

### 4.2 支持的手机型号

| 系列 | 型号 | 支持能力 |
|------|------|----------|
| Find系列 | Find X7/X8系列 | 完整API访问 |
| Reno系列 | Reno 12/13系列 | 完整API访问 |
| A系列 | A3 Pro等 | 基础API访问 |
| 折叠屏 | Find N3 | 完整API访问 |

### 4.3 可用接口列表

#### 4.3.1 Token获取接口

**请求**:
```http
POST https://api.openai.heytap.com/oauth/token
Content-Type: application/x-www-form-urlencoded

client_id={client_id}&client_secret={client_secret}&grant_type=client_credentials
```

**响应**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api"
}
```

#### 4.3.2 聊天接口

**请求**:
```http
POST https://api.openai.heytap.com/v1/chat/completions
Content-Type: application/json
Authorization: Bearer {access_token}

{
  "model": "oppo-lm",
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048
}
```

**响应**:
```json
{
  "id": "chatcmpl-oppo-xxx",
  "object": "chat.completion",
  "model": "oppo-lm",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！我是OPPO AI助手。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 5,
    "completion_tokens": 15,
    "total_tokens": 20
  }
}
```

#### 4.3.3 健康检查接口

**请求**:
```http
GET https://api.openai.heytap.com/v1/health
Authorization: Bearer {access_token}
```

**响应**:
```json
{
  "status": "healthy",
  "version": "1.0.0"
}
```

### 4.4 可用模型列表

| 模型名称 | 参数量 | 上下文长度 | 适用场景 | 价格 |
|----------|--------|------------|----------|------|
| oppo-lm | 7B | 32K | 通用对话 | 免费 |
| oppo-lm-pro | 70B | 128K | 复杂任务 | 待定 |

### 4.5 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| QPS限制 | 100 | 每秒请求数 |
| Token有效期 | 3600秒 | access_token过期时间 |
| 最大token | 4096 | 单次请求最大token数 |

---

## 5. vivo 蓝心大模型

### 5.1 基本信息

| 项目 | 说明 |
|------|------|
| **平台名称** | vivo蓝心大模型 / BlueLM |
| **文档地址** | https://developers.vivo.com/product/ai/bluelm |
| **API端点** | https://api.vivo.com.cn |
| **认证方式** | API Key + App ID |
| **支持模型** | BlueLM 7B、BlueLM 70B |
| **接口格式** | OpenAI兼容格式 |

### 5.2 支持的手机型号

| 系列 | 型号 | 支持能力 |
|------|------|----------|
| X系列 | X100/X200系列 | 完整API访问 + 端侧部署 |
| S系列 | S18/S19系列 | 完整API访问 |
| Y系列 | Y系列 | 基础API访问 |
| iQOO | iQOO 12/13 | 完整API访问 |
| 折叠屏 | X Fold3 | 完整API访问 + 端侧部署 |

### 5.3 可用接口列表

#### 5.3.1 聊天接口

**请求**:
```http
POST https://api.vivo.com.cn/v1/chat/completions
Content-Type: application/json
Authorization: Bearer {api_key}
X-App-Id: {app_id}

{
  "model": "bluelm-7b",
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048
}
```

**响应**:
```json
{
  "id": "chatcmpl-vivo-xxx",
  "object": "chat.completion",
  "model": "bluelm-7b",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！我是蓝心大模型。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 5,
    "completion_tokens": 12,
    "total_tokens": 17
  }
}
```

#### 5.3.2 流式聊天接口

**请求**:
```http
POST https://api.vivo.com.cn/v1/chat/completions
Content-Type: application/json
Authorization: Bearer {api_key}
X-App-Id: {app_id}

{
  "model": "bluelm-7b",
  "messages": [...],
  "stream": true
}
```

#### 5.3.3 健康检查接口

**请求**:
```http
GET https://api.vivo.com.cn/v1/health
Authorization: Bearer {api_key}
```

**响应**:
```json
{
  "status": "ok",
  "model": "bluelm-7b",
  "version": "1.0.0"
}
```

### 5.4 端侧AI能力 (VCAP)

vivo提供端侧AI部署能力，支持在手机上直接运行大模型：

**支持的能力**:
- 文本总结
- 文本创作
- 全局文件命名
- 卡证识别
- LoRA动态加载

**支持的模型结构**:
- LLaMA
- ChatGLM
- Bloom
- BlueLM

**端侧SDK**:
```java
// Android SDK示例
VCAPModel model = new VCAPModel.Builder()
    .modelPath("/data/local/models/bluelm_7b.bin")
    .maxTokens(2048)
    .temperature(0.7f)
    .build();

String response = model.generate("你好");
```

### 5.5 可用模型列表

| 模型名称 | 参数量 | 上下文长度 | 适用场景 | 价格 |
|----------|--------|------------|----------|------|
| bluelm-7b | 7B | 32K | 通用对话 | 免费 |
| bluelm-70b | 70B | 128K | 复杂任务 | 待定 |
| bluelm-edge | 1.8B | 8K | 端侧部署 | 免费 |

### 5.6 限制说明

| 限制项 | 值 | 说明 |
|--------|-----|------|
| QPS限制 | 100 | 每秒请求数 |
| 最大token | 4096 | 单次请求最大token数 |
| 日调用量 | 10万 | 每日最大调用量 |

---

## 6. 对比总结

### 6.1 功能对比

| 特性 | 华为 | 小米 | Apple | OPPO | vivo |
|------|------|------|-------|------|------|
| OpenAI兼容 | ✅ | ✅ | ❌ | ✅ | ✅ |
| 流式输出 | ✅ | ✅ | ❌ | ✅ | ✅ |
| 端侧部署 | ❌ | ❌ | ✅ | ❌ | ✅ |
| 免费额度 | 有限 | 有限 | 免费 | 免费 | 免费 |
| 多模态 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 代码生成 | ✅ | ❌ | ❌ | ❌ | ❌ |

### 6.2 性能对比

| 指标 | 华为 | 小米 | Apple | OPPO | vivo |
|------|------|------|-------|------|------|
| 响应时间 | 1-2s | 1-2s | 0.5-1s | 1-2s | 1-2s |
| 推理速度 | 50 tok/s | 40 tok/s | 30 tok/s | 45 tok/s | 40 tok/s |
| 上下文长度 | 32K | 128K | 8K | 32K | 128K |
| 最大模型 | 13B | 70B | 8B | 70B | 70B |

### 6.3 价格对比

| 提供者 | 免费额度 | 付费价格 |
|--------|----------|----------|
| 华为 | 10万token/月 | ¥0.01/千token |
| 小米 | 1000次/日 | ¥0.02/千token |
| Apple | 完全免费 | N/A (本地) |
| OPPO | 10万次/日 | 待定 |
| vivo | 10万次/日 | 待定 |

---

## 7. 接入指南

### 7.1 华为接入

1. 注册华为云账号
2. 开通ModelArts Studio服务
3. 创建IAM用户并授权
4. 获取API Key或配置AK/SK
5. 调用API

### 7.2 小米接入

1. 注册小米开发者账号
2. 创建应用获取API Key
3. 联系小米获取Secret Key
4. 调用API

### 7.3 Apple接入

1. 下载Core ML格式模型
2. 使用coremltools转换模型（如果需要）
3. 在Xcode中集成Core ML框架
4. 调用本地API

### 7.4 OPPO接入

1. 注册OPPO开发者账号
2. 创建应用获取client_id和client_secret
3. 获取access_token
4. 调用API

### 7.5 vivo接入

1. 注册vivo开发者账号
2. 创建应用获取API Key和App ID
3. 调用API

---

## 8. 常见问题

### Q1: 哪个厂商的API最稳定？

A: 华为和小米的API相对稳定，文档完善。OPPO和vivo的API仍在快速迭代中。

### Q2: 端侧部署需要什么条件？

A: Apple需要A15以上芯片，vivo需要VCAP支持。一般需要至少6GB内存。

### Q3: 如何选择合适的模型？

A: 
- 简单任务：1-3B模型
- 通用对话：7B模型
- 复杂任务：70B模型（云端）

### Q4: 流式输出支持情况？

A: 除Apple本地模型外，所有云端API都支持流式输出。