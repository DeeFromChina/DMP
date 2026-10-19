# DMP Android - 第三方应用接入文档

## 概述

DMP Android 应用在设备上启动本地服务，提供多种接入方式，让其他应用可以调用本地 LLM 能力。

**特点**：
- ✅ 完全本地运行，数据不出设备
- ✅ 多种接入方式，灵活选择
- ✅ OpenAI 兼容 HTTP API
- ✅ 高性能 AIDL 接口（SDK 推荐）
- ✅ 自动服务发现，零配置接入

---

## 架构概览

```
DMP App
├── HTTP Server (localhost:28473-28499)
│   └── OpenAI 兼容 API
│   └── 供 Web、iOS、跨设备、快速原型调用
│
├── AIDL Service
│   └── 高性能 IPC 接口
│   └── 供 DMP SDK 调用（官方推荐）
│   └── 类型安全、流式支持完善
│
└── ContentProvider
    └── 服务发现 + 配置查询
    └── 供第三方 app 解耦调用
    └── 无需依赖 SDK
```

---

## 接入方式对比

| 方式 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **HTTP API** | 快速原型、跨平台、Web/iOS | 简单、通用、OpenAI 兼容 | 性能略低 |
| **AIDL (SDK)** | Android 应用、高性能需求 | 类型安全、性能好、流式完善 | 需引入 SDK |
| **ContentProvider** | 第三方 app 解耦调用 | 无需 SDK、灵活 | 需自行封装 |

---

## 方式一：HTTP API（最简单）

### 1. 服务发现

#### 默认端口（推荐）

大多数情况下，DMP 服务运行在 `localhost:28473`，直接连接即可：

```kotlin
val baseUrl = "http://localhost:28473"
```

#### 通过 ContentProvider 查询（端口冲突时）

如果 28473 端口被占用，DMP 会自动选择其他端口（28474-28499）。此时可以通过 ContentProvider 查询实际端口：

```kotlin
fun getDMPBaseUrl(context: Context): String? {
    val uri = Uri.parse("content://com.dmp.android.service/config")
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    
    cursor?.use {
        if (it.moveToFirst()) {
            val portIndex = it.getColumnIndex("http_port")
            if (portIndex >= 0) {
                val port = it.getInt(portIndex)
                return "http://localhost:$port"
            }
        }
    }
    return null
}

// 使用
val baseUrl = getDMPBaseUrl(context) ?: "http://localhost:28473"
```

#### 端口扫描（备用）

如果 ContentProvider 查询失败，可以扫描端口范围：

```kotlin
fun findDMPService(): String? {
    for (port in 28473..28499) {
        val url = "http://localhost:$port/health"
        try {
            val response = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(url).get().build())
                .execute()
            
            if (response.isSuccessful) {
                return "http://localhost:$port"
            }
        } catch (e: Exception) {
            // 继续尝试下一个端口
        }
    }
    return null
}
```

### 2. API 端点

#### 健康检查

```
GET /health
```

**响应**：
```json
{
    "status": "ok",
    "service": "DMP HTTP Server",
    "endpoints": [
        "/v1/chat/completions",
        "/v1/chat/completions/stream",
        "/v1/responses",
        "/v1/responses/stream",
        "/health"
    ]
}
```

#### 聊天补全（非流式）

```
POST /v1/chat/completions
Content-Type: application/json
```

**请求**：
```json
{
    "messages": [
        {"role": "system", "content": "你是一个助手"},
        {"role": "user", "content": "什么是机器学习？"}
    ],
    "temperature": 0.7,
    "max_tokens": 2048,
    "model": "auto"
}
```

**响应**：
```json
{
    "id": "dmp-1234567890",
    "object": "chat.completion",
    "created": 1234567890,
    "model": "qwen-7b",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "机器学习是..."
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 20,
        "completion_tokens": 150,
        "total_tokens": 170
    }
}
```

#### 聊天补全（流式）

```
POST /v1/chat/completions/stream
Content-Type: application/json
Accept: text/event-stream
```

**请求**：同上

**响应**（SSE 格式）：
```
data: {"content": "机器", "model": "qwen-7b"}

data: {"content": "学习", "model": "qwen-7b"}

data: {"content": "是...", "model": "qwen-7b"}

data: [DONE]
```

### 3. 完整示例

```kotlin
class DMPHttpClient(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 获取 DMP 服务地址
     */
    fun getBaseUrl(): String {
        // 1. 尝试 ContentProvider
        val uri = Uri.parse("content://com.dmp.android.service/config")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val portIndex = it.getColumnIndex("http_port")
                if (portIndex >= 0) {
                    return "http://localhost:${it.getInt(portIndex)}"
                }
            }
        }
        
        // 2. 返回默认端口
        return "http://localhost:28473"
    }
    
    /**
     * 检查服务是否可用
     */
    fun isServiceAvailable(): Boolean {
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url("${getBaseUrl()}/health")
                    .get()
                    .build()
            ).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 发送聊天消息（非流式）
     */
    suspend fun chat(messages: List<Message>): LLMResponse {
        val jsonBody = JSONObject().apply {
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }.toString()
        
        val request = Request.Builder()
            .url("${getBaseUrl()}/v1/chat/completions")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        
        return parseResponse(body)
    }
    
    /**
     * 发送聊天消息（流式）
     */
    fun chatStream(messages: List<Message>): Flow<String> = flow {
        val jsonBody = JSONObject().apply {
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }.toString()
        
        val request = Request.Builder()
            .url("${getBaseUrl()}/v1/chat/completions/stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Accept", "text/event-stream")
            .build()
        
        client.newCall(request).execute().use { response ->
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        
                        val json = JSONObject(data)
                        val content = json.optString("content", "")
                        if (content.isNotEmpty()) {
                            emit(content)
                        }
                    }
                }
            }
        }
    }
}
```

---

## 方式二：AIDL + SDK（推荐）

### 1. 引入 SDK

```gradle
// build.gradle
dependencies {
    implementation("com.dmp:client-sdk:1.0.0")
}
```

### 2. 使用示例

```kotlin
// 初始化客户端
val client = DMPClient(context)

// 连接服务
client.connect { success ->
    if (success) {
        // 非流式调用
        val response = client.chat(
            messages = listOf(Message("user", "你好")),
            temperature = 0.7,
            maxTokens = 2048
        )
        println(response?.content)
        
        // 流式调用
        client.chatStream(
            messages = listOf(Message("user", "讲个故事")),
            temperature = 0.7,
            maxTokens = 2048,
            onChunk = { chunk -> print(chunk) },
            onComplete = { println("\n完成") },
            onError = { error -> println("错误: $error") }
        )
    }
}
```

### 3. SDK API 参考

#### DMPClient

| 方法 | 说明 |
|------|------|
| `connect(callback)` | 连接 DMP 服务 |
| `disconnect()` | 断开连接 |
| `isConnected()` | 检查连接状态 |
| `chat(messages, temperature, maxTokens)` | 非流式聊天 |
| `chatStream(messages, temperature, maxTokens, onChunk, onComplete, onError)` | 流式聊天 |
| `getProviderStatus()` | 获取提供者状态 |

---

## 方式三：ContentProvider（解耦调用）

### 1. 查询服务配置

```kotlin
// 查询服务配置
val uri = Uri.parse("content://com.dmp.android.service/config")
val cursor = contentResolver.query(uri, null, null, null, null)

cursor?.use {
    if (it.moveToFirst()) {
        val httpPort = it.getInt(it.getColumnIndex("http_port"))
        val aidlService = it.getString(it.getColumnIndex("aidl_service_name"))
        val version = it.getString(it.getColumnIndex("version"))
        val endpoints = it.getString(it.getColumnIndex("endpoints"))
        val capabilities = it.getString(it.getColumnIndex("capabilities"))
        
        // 根据需求选择调用方式
        if (useAIDL) {
            // 绑定 AIDL 服务
            bindAIDLService(aidlService)
        } else {
            // 使用 HTTP
            val baseUrl = "http://localhost:$httpPort"
        }
    }
}
```

### 2. 查询服务状态

```kotlin
// 查询服务状态
val uri = Uri.parse("content://com.dmp.android.service/status")
val cursor = contentResolver.query(uri, null, null, null, null)

cursor?.use {
    if (it.moveToFirst()) {
        val isRunning = it.getInt(it.getColumnIndex("is_running")) == 1
        val activeProvider = it.getString(it.getColumnIndex("active_provider"))
        val modelName = it.getString(it.getColumnIndex("model_name"))
        
        if (isRunning) {
            println("DMP 服务运行中")
            println("当前提供者: $activeProvider")
            println("当前模型: $modelName")
        }
    }
}
```

### 3. ContentProvider 返回字段

#### config 端点

| 字段 | 类型 | 说明 |
|------|------|------|
| `http_port` | int | HTTP 服务端口 |
| `aidl_service_name` | String | AIDL 服务接口名 |
| `version` | String | DMP 版本 |
| `endpoints` | String | HTTP 端点列表（逗号分隔） |
| `capabilities` | String | 支持的能力（逗号分隔） |

#### status 端点

| 字段 | 类型 | 说明 |
|------|------|------|
| `is_running` | int | 服务是否运行（1=运行，0=停止） |
| `active_provider` | String | 当前活跃的提供者 |
| `model_name` | String | 当前使用的模型名称 |

---

## 权限要求

### HTTP 方式

```xml
<!-- 网络权限（用于 localhost 连接） -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- 查询 DMP 服务配置（可选） -->
<queries>
    <provider android:authorities="com.dmp.android.service" />
</queries>
```

### AIDL 方式

```xml
<!-- 绑定 DMP 服务权限 -->
<uses-permission android:name="com.dmp.android.permission.BIND_SERVICE" />

<!-- 查询 DMP 服务配置 -->
<queries>
    <provider android:authorities="com.dmp.android.service" />
    <intent>
        <action android:name="com.dmp.android.service.DMPService" />
    </intent>
</queries>
```

---

## 错误处理

### 常见错误码

| 错误码 | 说明 | 处理方式 |
|--------|------|----------|
| 404 | 端点不存在 | 检查 URL 是否正确 |
| 500 | 服务器内部错误 | 检查 DMP 服务状态 |
| Connection refused | 服务未运行 | 提示用户启动 DMP 应用 |
| Timeout | 请求超时 | 增加超时时间或重试 |

### 错误响应格式

```json
{
    "error": {
        "code": 500,
        "message": "LLM request failed: Model not available",
        "api_type": "chat_completions"
    }
}
```

---

## 最佳实践

1. **服务检查**：调用前先检查服务是否可用
2. **超时设置**：建议 connectTimeout=30s, readTimeout=60s
3. **错误重试**：网络错误可以重试 1-2 次
4. **流式处理**：长文本建议使用流式接口，提升用户体验
5. **端口回退**：如果 28473 连接失败，尝试通过 ContentProvider 查询实际端口
6. **生命周期管理**：AIDL 绑定要注意解绑，避免内存泄漏

---

## 迁移指南

### 从 HTTP 迁移到 AIDL

如果你已经使用 HTTP API，迁移到 AIDL 很简单：

```kotlin
// 原 HTTP 调用
val response = httpClient.chat(messages)

// 改为 AIDL 调用
val response = aidlClient.chat(messages)
```

接口保持一致，只需更换客户端实现。

---

## 技术支持

- GitHub: https://github.com/your-org/dmp-android
- 问题反馈: https://github.com/your-org/dmp-android/issues
- 文档: https://github.com/your-org/dmp-android/docs