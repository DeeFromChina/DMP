# DMP 服务 HTTP API 接口文档

## 概述

DMP (Device Model Protocol) 是运行在手机设备上的本地 MCP 服务，提供智能 LLM 路由功能。

App 应用通过 HTTP API 调用 DMP 服务，DMP 服务根据设备型号自动匹配最优 LLM 接口。

### 架构说明

```
┌─────────────────────┐
│   App 应用服务       │  (任何需要调用LLM的应用)
│   (MCP Client)      │
└──────────┬──────────┘
           │ HTTP API
           │ (REST + SSE)
┌──────────▼──────────┐
│   DMP 服务          │  (运行在手机上的本地服务)
│   (MCP Server)      │
│                     │
│  ┌───────────────┐  │
│  │ LLMRouter     │  │
│  │ ├─ 本地模型   │  │
│  │ ├─ 厂商API    │  │
│  │ └─ 云端API    │  │
│  └───────────────┘  │
└─────────────────────┘
```

### 服务地址

| 环境 | 默认地址 | 说明 |
|------|----------|------|
| Android | http://localhost:28473 | 本地服务地址（可配置） |
| iOS | http://localhost:28473 | 本地服务地址（可配置） |

### 端口配置

**端口不是固定的**，可以通过以下方式配置：

#### 1. 环境变量配置

```bash
# 设置自定义端口
export DMP_SERVER_PORT=9090

# 启动服务
python main.py
```

#### 2. 配置文件

在 `.env` 文件中配置：

```env
# DMP 服务配置
SERVER_PORT=28473
SERVER_HOST=127.0.0.1
```

#### 3. 代码中配置

```python
# Python
from dmp.config import Settings

settings = Settings(
    server_port=9090,  # 自定义端口
    server_host="127.0.0.1"
)
```

```kotlin
// Android
val server = DMPServer(context)
server.start(port = 9090)  // 自定义端口
```

```swift
// iOS
let server = DMPServer()
try await server.start(port: 9090)  // 自定义端口
```

### 端口冲突处理

DMP 服务内置了**端口冲突自动处理机制**：

#### 策略说明

| 策略 | 说明 | 默认值 |
|------|------|--------|
| 自动递增 | 如果端口被占用，自动尝试下一个端口（+1） | 启用 |
| 最大重试 | 最多尝试的端口数量 | 10 |
| 随机端口 | 如果连续 10 个端口都被占用，使用随机端口（0） | 启用 |

#### 工作流程

```
启动服务 (端口 28473)
    │
    ├─ 端口可用？ ── 是 ──> 使用 28473
    │
    否
    │
    ├─ 尝试 8081
    │
    ├─ 端口可用？ ── 是 ──> 使用 8081
    │
    否
    │
    ├─ 尝试 8082
    │
    ... (最多尝试 10 次)
    │
    ├─ 使用随机端口 (0)
    │
    └─ 返回实际使用的端口号
```

#### 获取实际端口

服务启动后会返回实际使用的端口：

```python
# Python
settings = Settings(server_port=28473)
mcp, fallback_manager = create_server(settings)

# 服务启动后
actual_port = mcp.port  # 实际使用的端口
print(f"DMP 服务运行在端口: {actual_port}")
```

```kotlin
// Android
val server = DMPServer(context)
val actualPort = server.start(port = 28473)
Log.d("DMP", "服务运行在端口: $actualPort")
```

```swift
// iOS
let server = DMPServer()
let actualPort = try await server.start(port: 28473)
print("服务运行在端口: \(actualPort)")
```

#### 服务发现机制

App 应用可以通过以下方式发现 DMP 服务的实际端口：

**方式一：广播发现**

```kotlin
// Android - 监听本地广播
val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val port = intent.getIntExtra("dmp_server_port", 28473)
        val url = "http://localhost:$port"
        // 使用发现的端口
    }
}

context.registerReceiver(receiver, IntentFilter("com.dmp.android.SERVER_STARTED"))
```

```swift
// iOS - 监听通知
NotificationCenter.default.addObserver(
    forName: NSNotification.Name("DMPServerStarted"),
    object: nil,
    queue: .main
) { notification in
    if let port = notification.userInfo?["port"] as? Int {
        let url = "http://localhost:\(port)"
        // 使用发现的端口
    }
}
```

**方式二：配置文件共享**

DMP 服务启动后会将实际端口写入共享配置文件：

```json
// /data/data/com.dmp.android/shared/config.json
{
  "server_port": 8081,
  "server_url": "http://localhost:8081",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

App 应用读取该文件获取实际端口。

**方式三：健康检查扫描**

```python
# Python - 扫描可能的端口范围
import requests

def discover_dmp_port(start_port=28473, max_ports=10):
    for port in range(start_port, start_port + max_ports):
        try:
            response = requests.get(f"http://localhost:{port}/health", timeout=1)
            if response.status_code == 200:
                return port
        except:
            continue
    return None

port = discover_dmp_port()
if port:
    print(f"DMP 服务运行在端口: {port}")
```

### 通用说明

- **协议**: HTTP/1.1
- **数据格式**: JSON
- **字符编码**: UTF-8
- **超时时间**: 30秒（可配置）
- **端口范围**: 1024-65535（建议使用 8000-9000 范围）

---

## 1. 接口列表

### 1.1 聊天接口（非流式）

**接口说明**: 发送聊天请求，等待完整响应后返回。

**请求**:
```http
POST /chat
Content-Type: application/json

{
  "messages": [
    {"role": "system", "content": "你是一个助手"},
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048,
  "model": "",
  "preferred_provider": "any"
}
```

**请求参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| messages | array | 是 | - | 对话消息列表 |
| messages[].role | string | 是 | - | 消息角色（system/user/assistant） |
| messages[].content | string | 是 | - | 消息内容 |
| temperature | number | 否 | 0.7 | 温度参数（0.0-2.0） |
| max_tokens | integer | 否 | 2048 | 最大生成token数（1-8192） |
| model | string | 否 | "" | 指定模型名称（空字符串表示自动选择） |
| preferred_provider | string | 否 | "any" | 优先使用的提供者类型（local/mobile/cloud/any） |

**响应**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "content": "你好！有什么可以帮助你的？",
  "provider": "openai",
  "model": "gpt-4",
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 20,
    "total_tokens": 35
  },
  "finish_reason": "stop"
}
```

**响应参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| content | string | LLM生成的内容 |
| provider | string | 实际使用的提供者名称 |
| model | string | 实际使用的模型名称 |
| usage | object | Token使用情况 |
| usage.prompt_tokens | integer | 输入token数 |
| usage.completion_tokens | integer | 输出token数 |
| usage.total_tokens | integer | 总token数 |
| finish_reason | string | 完成原因（stop/length/error） |

**错误响应**:
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "error": {
    "code": 500,
    "message": "所有LLM提供者均失败"
  }
}
```

---

### 1.2 聊天接口（流式）

**接口说明**: 发送聊天请求，使用 Server-Sent Events (SSE) 流式返回响应。

**请求**:
```http
POST /chat/stream
Content-Type: application/json
Accept: text/event-stream

{
  "messages": [
    {"role": "user", "content": "讲个故事"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048
}
```

**请求参数**: 同非流式接口

**响应**:
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"content":"从前","provider":"openai","model":"gpt-4","finish_reason":null}

data: {"content":"有座山","provider":"openai","model":"gpt-4","finish_reason":null}

data: {"content":"","provider":"openai","model":"gpt-4","finish_reason":"stop"}

data: [DONE]
```

**响应格式**:

每个 SSE 事件包含一个 JSON 对象：

| 参数 | 类型 | 说明 |
|------|------|------|
| content | string | 当前生成的内容片段 |
| provider | string | 实际使用的提供者名称 |
| model | string | 实际使用的模型名称 |
| finish_reason | string \| null | 完成原因（null表示未完成） |

流结束时返回 `data: [DONE]`。

---

### 1.3 获取提供者状态

**接口说明**: 获取所有 LLM 提供者的状态信息。

**请求**:
```http
GET /status
```

**响应**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "name": "openai",
    "priority": 1,
    "available": true,
    "type": "cloud"
  },
  {
    "name": "huawei",
    "priority": 2,
    "available": true,
    "type": "mobile"
  },
  {
    "name": "tflite",
    "priority": 3,
    "available": false,
    "type": "local"
  }
]
```

**响应参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| name | string | 提供者名称 |
| priority | integer | 优先级（数字越小优先级越高） |
| available | boolean | 是否可用 |
| type | string | 提供者类型（local/mobile/cloud） |

---

### 1.4 健康检查

**接口说明**: 检查 DMP 服务是否正常运行，返回服务版本和协议信息。

**请求**:
```http
GET /health
```

**响应**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "ok",
  "server_version": "1.0.0",
  "supported_protocols": ["v1"],
  "deprecated_protocols": {},
  "latest_protocol": "v1",
  "providers_count": 3
}
```

**响应参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| status | string | 服务状态（ok/error） |
| server_version | string | DMP 服务版本 |
| supported_protocols | array | 支持的协议版本列表 |
| deprecated_protocols | object | 已废弃的协议版本及迁移信息 |
| latest_protocol | string | 最新协议版本 |
| providers_count | integer | 已注册的提供者数量 |

---

### 1.5 获取服务端能力

**接口说明**: 获取服务端支持的协议版本和功能列表。

**请求**:
```http
GET /capabilities
```

**响应**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "server_version": "1.0.0",
  "negotiated_protocol": "v1",
  "available_features": ["chat", "chat_stream", "provider_status"],
  "deprecated_features": [],
  "latest_protocol": "v1"
}
```

---

### 1.6 协议协商

**接口说明**: 客户端声明支持的协议版本，服务端协商最优协议。

**请求**:
```http
POST /capabilities/negotiate
Content-Type: application/json

{
  "client_version": "1.0.0",
  "supported_protocols": ["v1"],
  "supported_features": ["chat", "chat_stream"]
}
```

**响应**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "server_version": "1.0.0",
  "negotiated_protocol": "v1",
  "available_features": ["chat", "chat_stream", "provider_status"],
  "deprecated_features": [],
  "latest_protocol": "v1"
}
```

---

## 2. 错误码

### 2.1 HTTP 状态码

| 状态码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 请求成功 | - |
| 400 | 请求参数错误 | 检查请求参数格式和范围 |
| 404 | 接口不存在 | 检查接口路径 |
| 500 | 服务器内部错误 | 查看错误信息，检查 DMP 服务状态 |
| 503 | 服务不可用 | DMP 服务未启动或正在重启 |

### 2.2 错误响应格式

```json
{
  "error": {
    "code": 500,
    "message": "错误描述信息"
  }
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| code | integer | 错误码 |
| message | string | 错误描述信息 |

### 2.3 常见错误

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 400 | "messages is required" | 缺少 messages 参数 |
| 400 | "temperature must be between 0 and 2" | temperature 超出范围 |
| 400 | "max_tokens must be between 1 and 8192" | max_tokens 超出范围 |
| 500 | "所有LLM提供者均失败" | 所有提供者都不可用或调用失败 |
| 500 | "提供者 xxx 不可用" | 指定提供者不可用 |
| 503 | "DMP 服务未启动" | 服务未运行 |

---

## 3. 调用示例

### 3.1 Android (Kotlin)

#### 非流式调用

```kotlin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*

suspend fun chat(message: String): String {
    val client = OkHttpClient()
    val json = Json { ignoreUnknownKeys = true }
    
    // 构建请求体
    val requestBody = """
    {
        "messages": [
            {"role": "user", "content": "$message"}
        ],
        "temperature": 0.7,
        "max_tokens": 2048
    }
    """.trimIndent().toRequestBody("application/json".toMediaType())
    
    // 发送请求
    val request = Request.Builder()
        .url("http://localhost:28473/chat")
        .post(requestBody)
        .build()
    
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }
        
        val body = response.body?.string() ?: throw Exception("Empty response")
        val jsonTree = json.parseToJsonElement(body)
        return jsonTree.jsonObject["content"]?.jsonPrimitive?.content ?: ""
    }
}
```

#### 流式调用

```kotlin
import okhttp3.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun chatStream(message: String): Flow<String> = flow {
    val client = OkHttpClient()
    
    val requestBody = """
    {
        "messages": [
            {"role": "user", "content": "$message"}
        ],
        "stream": true
    }
    """.trimIndent().toRequestBody("application/json".toMediaType())
    
    val request = Request.Builder()
        .url("http://localhost:28473/chat/stream")
        .post(requestBody)
        .header("Accept", "text/event-stream")
        .build()
    
    client.newCall(request).execute().use { response ->
        response.body?.source()?.use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data == "[DONE]") break
                    
                    val jsonTree = Json.parseToJsonElement(data)
                    val content = jsonTree.jsonObject["content"]?.jsonPrimitive?.content
                    if (!content.isNullOrEmpty()) {
                        emit(content)
                    }
                }
            }
        }
    }
}

// 使用示例
lifecycleScope.launch {
    chatStream("讲个故事").collect { chunk ->
        textView.append(chunk)
    }
}
```

#### 获取提供者状态

```kotlin
suspend fun getProviderStatus() {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("http://localhost:28473/status")
        .get()
        .build()
    
    client.newCall(request).execute().use { response ->
        val body = response.body?.string()
        println("提供者状态: $body")
    }
}
```

---

### 3.2 iOS (Swift)

#### 非流式调用

```swift
import Foundation

func chat(message: String) async throws -> String {
    let url = URL(string: "http://localhost:28473/chat")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body: [String: Any] = [
        "messages": [
            ["role": "user", "content": message]
        ],
        "temperature": 0.7,
        "max_tokens": 2048
    ]
    
    request.httpBody = try JSONSerialization.data(withJSONObject: body)
    
    let (data, response) = try await URLSession.shared.data(for: request)
    
    guard let httpResponse = response as? HTTPURLResponse,
          httpResponse.statusCode == 200 else {
        throw NSError(domain: "DMP", code: -1, userInfo: [NSLocalizedDescriptionKey: "Request failed"])
    }
    
    let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
    return json?["content"] as? String ?? ""
}

// 使用示例
Task {
    do {
        let response = try await chat(message: "你好")
        print(response)
    } catch {
        print("错误: \(error)")
    }
}
```

#### 流式调用

```swift
import Foundation

func chatStream(message: String) -> AsyncThrowingStream<String, Error> {
    AsyncThrowingStream { continuation in
        Task {
            guard let url = URL(string: "http://localhost:28473/chat/stream") else {
                continuation.finish(throwing: NSError(domain: "DMP", code: -1))
                return
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
            
            let body: [String: Any] = [
                "messages": [["role": "user", "content": message]],
                "stream": true
            ]
            
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
            
            do {
                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                
                guard let httpResponse = response as? HTTPURLResponse,
                      httpResponse.statusCode == 200 else {
                    continuation.finish(throwing: NSError(domain: "DMP", code: -1))
                    return
                }
                
                for try await line in bytes.lines {
                    if line.hasPrefix("data: ") {
                        let data = String(line.dropFirst(6))
                        if data == "[DONE]" {
                            break
                        }
                        
                        if let jsonData = data.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
                           let content = json["content"] as? String {
                            continuation.yield(content)
                        }
                    }
                }
                
                continuation.finish()
            } catch {
                continuation.finish(throwing: error)
            }
        }
    }
}

// 使用示例
Task {
    do {
        let stream = chatStream(message: "讲个故事")
        for try await chunk in stream {
            print(chunk, terminator: "")
        }
    } catch {
        print("错误: \(error)")
    }
}
```

#### 获取提供者状态

```swift
func getProviderStatus() async throws {
    let url = URL(string: "http://localhost:28473/status")!
    let (data, _) = try await URLSession.shared.data(from: url)
    
    let json = try JSONSerialization.jsonObject(with: data)
    print("提供者状态: \(json)")
}
```

---

### 3.3 Python

#### 非流式调用

```python
import requests

def chat(message: str) -> str:
    response = requests.post(
        "http://localhost:28473/chat",
        json={
            "messages": [
                {"role": "user", "content": message}
            ],
            "temperature": 0.7,
            "max_tokens": 2048
        }
    )
    
    if response.status_code != 200:
        raise Exception(f"HTTP {response.status_code}: {response.text}")
    
    return response.json()["content"]

# 使用示例
result = chat("你好")
print(result)
```

#### 流式调用

```python
import requests

def chat_stream(message: str):
    response = requests.post(
        "http://localhost:28473/chat/stream",
        json={
            "messages": [
                {"role": "user", "content": message}
            ],
            "stream": True
        },
        headers={"Accept": "text/event-stream"},
        stream=True
    )
    
    for line in response.iter_lines():
        if line:
            line = line.decode('utf-8')
            if line.startswith("data: "):
                data = line[6:]
                if data == "[DONE]":
                    break
                
                import json
                chunk = json.loads(data)
                if chunk.get("content"):
                    yield chunk["content"]

# 使用示例
for chunk in chat_stream("讲个故事"):
    print(chunk, end="", flush=True)
```

#### 获取提供者状态

```python
import requests

def get_provider_status():
    response = requests.get("http://localhost:28473/status")
    return response.json()

# 使用示例
status = get_provider_status()
for provider in status:
    print(f"{provider['name']}: {'可用' if provider['available'] else '不可用'}")
```

---

### 3.4 cURL

#### 非流式调用

```bash
curl -X POST http://localhost:28473/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "temperature": 0.7,
    "max_tokens": 2048
  }'
```

#### 流式调用

```bash
curl -X POST http://localhost:28473/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "messages": [
      {"role": "user", "content": "讲个故事"}
    ],
    "stream": true
  }'
```

#### 获取提供者状态

```bash
curl http://localhost:28473/status
```

---

## 4. MCP 协议接口（可选）

如果 App 使用标准 MCP 协议调用 DMP 服务，可以使用以下接口：

### 4.1 MCP 请求格式

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "chat",
    "arguments": {
      "messages": [
        {"role": "user", "content": "你好"}
      ],
      "temperature": 0.7,
      "max_tokens": 2048
    }
  },
  "id": 1
}
```

### 4.2 MCP 响应格式

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": "你好！有什么可以帮助你的？",
    "provider": "openai",
    "model": "gpt-4"
  },
  "id": 1
}
```

### 4.3 MCP 错误响应

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -1,
    "message": "所有LLM提供者均失败"
  },
  "id": 1
}
```

---

---

## 6. 跨应用服务发现机制

### 6.1 问题场景

当 DMP 服务安装到手机后，其他 App 需要：
1. **发现 DMP 服务是否已安装**
2. **获取 DMP 服务的实际运行端口**
3. **建立通信连接**

### 6.2 Android 平台方案

#### 方案一：Package Manager 检测 + ContentProvider（推荐）

**步骤 1：DMP App 暴露 ContentProvider**

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name=".service.DMPServiceProvider"
    android:authorities="com.dmp.android.provider"
    android:exported="true"
    android:readPermission="com.dmp.android.permission.ACCESS_SERVICE" />
```

```kotlin
// DMPServiceProvider.kt
class DMPServiceProvider : ContentProvider() {
    
    companion object {
        const val AUTHORITY = "com.dmp.android.provider"
        const val URI_SERVICE_INFO = "content://com.dmp.android.provider/service_info"
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uri.toString()) {
            URI_SERVICE_INFO -> {
                val port = DMPServiceManager.getCurrentPort()
                val matrixCursor = MatrixCursor(arrayOf("port", "url", "version"))
                matrixCursor.addRow(arrayOf(
                    port.toString(),
                    "http://localhost:$port",
                    BuildConfig.VERSION_NAME
                ))
                matrixCursor
            }
            else -> null
        }
    }
    
    override fun onCreate(): Boolean = true
    // ... 其他方法
}
```

**步骤 2：第三方 App 检测并获取服务信息**

```kotlin
class DMPServiceDiscovery(private val context: Context) {
    
    companion object {
        const val DMP_PACKAGE_NAME = "com.dmp.android"
        const val DMP_PROVIDER_URI = "content://com.dmp.android.provider/service_info"
    }
    
    /**
     * 检查 DMP 服务是否已安装
     */
    fun isDMPInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(DMP_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 获取 DMP 服务信息
     */
    fun getServiceInfo(): DMPServiceInfo? {
        return try {
            val uri = Uri.parse(DMP_PROVIDER_URI)
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("port", "url", "version"),
                null, null, null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    DMPServiceInfo(
                        port = it.getInt(it.getColumnIndexOrThrow("port")),
                        url = it.getString(it.getColumnIndexOrThrow("url")),
                        version = it.getString(it.getColumnIndexOrThrow("version"))
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 引导用户安装 DMP 服务
     */
    fun openDMPDownload() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=com.dmp.android")
        }
        context.startActivity(intent)
    }
}

data class DMPServiceInfo(
    val port: Int,
    val url: String,
    val version: String
)

// 使用示例
val discovery = DMPServiceDiscovery(context)

if (discovery.isDMPInstalled()) {
    val serviceInfo = discovery.getServiceInfo()
    if (serviceInfo != null) {
        // 使用发现的端口
        val client = DMPServerClient(baseURL = serviceInfo.url)
    } else {
        // DMP 服务未运行
        showError("DMP 服务未运行，请打开 DMP App")
    }
} else {
    // DMP 未安装，引导用户安装
    discovery.openDMPDownload()
}
```

---

#### 方案二：BroadcastReceiver 广播（实时发现）

**步骤 1：DMP 服务启动时发送广播**

```kotlin
class DMPServiceManager {
    
    companion object {
        const val ACTION_SERVICE_STARTED = "com.dmp.android.ACTION_SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "com.dmp.android.ACTION_SERVICE_STOPPED"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_URL = "extra_url"
    }
    
    fun notifyServiceStarted(context: Context, port: Int) {
        val intent = Intent(ACTION_SERVICE_STARTED).apply {
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_URL, "http://localhost:$port")
            setPackage("com.dmp.android")  // 限制只发送给 DMP 自己的组件
        }
        context.sendBroadcast(intent)
        
        // 同时发送给已注册的第三方 App
        sendBroadcastToRegisteredApps(context, port)
    }
    
    private fun sendBroadcastToRegisteredApps(context: Context, port: Int) {
        // 查询已注册监听的 App
        val intent = Intent(ACTION_SERVICE_STARTED).apply {
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_URL, "http://localhost:$port")
        }
        context.sendBroadcast(intent)
    }
}
```

**步骤 2：第三方 App 注册监听**

```kotlin
class DMPServiceListener(private val context: Context) {
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DMPServiceManager.ACTION_SERVICE_STARTED -> {
                    val port = intent.getIntExtra(DMPServiceManager.EXTRA_PORT, 28473)
                    val url = intent.getStringExtra(DMPServiceManager.EXTRA_URL)
                    onServiceDiscovered(url ?: "http://localhost:$port")
                }
                DMPServiceManager.ACTION_SERVICE_STOPPED -> {
                    onServiceLost()
                }
            }
        }
    }
    
    fun startListening() {
        val filter = IntentFilter().apply {
            addAction(DMPServiceManager.ACTION_SERVICE_STARTED)
            addAction(DMPServiceManager.ACTION_SERVICE_STOPPED)
        }
        context.registerReceiver(receiver, filter)
    }
    
    fun stopListening() {
        context.unregisterReceiver(receiver)
    }
    
    private fun onServiceDiscovered(url: String) {
        // 服务可用，保存 URL
        SharedPreferencesManager.saveDMPUrl(url)
    }
    
    private fun onServiceLost() {
        // 服务不可用，清除缓存
        SharedPreferencesManager.clearDMPUrl()
    }
}
```

---

#### 方案三：App Links / Deep Link（引导安装）

```xml
<!-- DMP App AndroidManifest.xml -->
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="dmp" android:host="connect" />
    </intent-filter>
</activity>
```

**第三方 App 调用**：

```kotlin
// 尝试打开 DMP App
val intent = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("dmp://connect?callback=myapp://dmp-callback")
}

if (intent.resolveActivity(context.packageManager) != null) {
    context.startActivity(intent)
} else {
    // DMP 未安装，引导下载
    openPlayStore()
}
```

---

### 6.3 iOS 平台方案

#### 方案一：App Groups + UserDefaults（推荐）

**步骤 1：配置 App Groups**

在 Xcode 中为 DMP App 和第三方 App 配置相同的 App Group：
```
group.com.dmp.shared
```

**步骤 2：DMP 服务写入共享配置**

```swift
class DMPServiceRegistrar {
    
    static func registerService(port: Int) {
        guard let sharedDefaults = UserDefaults(suiteName: "group.com.dmp.shared") else {
            return
        }
        
        sharedDefaults.set([
            "port": port,
            "url": "http://localhost:\(port)",
            "version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            "timestamp": Date().timeIntervalSince1970
        ], forKey: "dmp_service_info")
    }
    
    static func unregisterService() {
        UserDefaults(suiteName: "group.com.dmp.shared")?
            .removeObject(forKey: "dmp_service_info")
    }
}
```

**步骤 3：第三方 App 读取共享配置**

```swift
class DMPServiceDiscovery {
    
    static func discoverService() -> DMPServiceInfo? {
        guard let sharedDefaults = UserDefaults(suiteName: "group.com.dmp.shared"),
              let info = sharedDefaults.dictionary(forKey: "dmp_service_info") as? [String: Any],
              let url = info["url"] as? String,
              let port = info["port"] as? Int else {
            return nil
        }
        
        // 检查时间戳，确保是最近注册的
        if let timestamp = info["timestamp"] as? TimeInterval,
           Date().timeIntervalSince1970 - timestamp > 300 {  // 5分钟过期
            return nil
        }
        
        return DMPServiceInfo(port: port, url: url)
    }
    
    static func isDMPInstalled() -> Bool {
        // 检查是否可以打开 DMP App
        guard let url = URL(string: "dmp://") else { return false }
        return UIApplication.shared.canOpenURL(url)
    }
}

// 使用示例
if DMPServiceDiscovery.isDMPInstalled() {
    if let serviceInfo = DMPServiceDiscovery.discoverService() {
        let client = DMPServerClient(baseURL: serviceInfo.url)
        // 使用服务
    } else {
        // DMP 服务未运行
        print("DMP 服务未运行")
    }
} else {
    // DMP 未安装，引导用户安装
    if let url = URL(string: "https://apps.apple.com/app/dmp/id123456789") {
        UIApplication.shared.open(url)
    }
}
```

---

#### 方案二：URL Scheme（跨应用通信）

**步骤 1：DMP App 注册 URL Scheme**

```xml
<!-- Info.plist -->
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLName</key>
        <string>com.dmp.service</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>dmp</string>
        </array>
    </dict>
</array>
```

**步骤 2：第三方 App 通过 URL Scheme 查询**

```swift
class DMPURLSchemeClient {
    
    static func queryServiceInfo(completion: @escaping (DMPServiceInfo?) -> Void) {
        guard let url = URL(string: "dmp://query-service?callback=myapp://callback") else {
            completion(nil)
            return
        }
        
        UIApplication.shared.open(url, options: [:]) { success in
            if success {
                // DMP App 会通过 callback URL 返回服务信息
                // 需要在 AppDelegate 中处理回调
            } else {
                completion(nil)
            }
        }
    }
}
```

---

#### 方案三：Bonjour 服务发现（局域网）

```swift
class DMPBonjourService {
    private var netService: NetService?
    private var netServiceBrowser: NetServiceBrowser?
    
    // 发布服务（DMP App）
    func publishService(port: Int) {
        netService = NetService(domain: "local.", type: "_dmp._tcp.", name: "DMP Service", port: Int32(port))
        netService?.publish()
    }
    
    // 发现服务（第三方 App）
    func discoverService(completion: @escaping (Int?) -> Void) {
        netServiceBrowser = NetServiceBrowser()
        netServiceBrowser?.delegate = self
        netServiceBrowser?.searchForServices(ofType: "_dmp._tcp.", inDomain: "local.")
    }
}

extension DMPBonjourService: NetServiceBrowserDelegate {
    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        service.delegate = self
        service.resolve(withTimeout: 5.0)
    }
}

extension DMPBonjourService: NetServiceDelegate {
    func netServiceDidResolveAddress(_ sender: NetService) {
        let port = sender.port
        print("发现 DMP 服务在端口: \(port)")
    }
}
```

---

### 6.4 完整集成流程

#### 第三方 App 集成 DMP 服务的标准流程

```
1. 检测 DMP App 是否安装
   ├─ 已安装 → 继续
   └─ 未安装 → 引导用户安装
   
2. 获取 DMP 服务信息
   ├─ Android: ContentProvider / BroadcastReceiver
   ├─ iOS: App Groups / URL Scheme
   └─ 兜底: 端口扫描
   
3. 验证服务可用性
   └─ 调用 /health 接口
   
4. 建立连接
   └─ 使用获取到的端口创建客户端
   
5. 监听服务状态
   ├─ 服务断开 → 重新发现
   └─ 服务恢复 → 重新连接
```

#### 示例代码（完整流程）

```kotlin
class DMPIntegrationManager(private val context: Context) {
    
    private var client: DMPServerClient? = null
    private var serviceListener: DMPServiceListener? = null
    
    /**
     * 初始化 DMP 服务连接
     */
    suspend fun initialize(): Boolean {
        // 1. 检查是否安装
        val discovery = DMPServiceDiscovery(context)
        if (!discovery.isDMPInstalled()) {
            Log.w("DMP", "DMP App 未安装")
            return false
        }
        
        // 2. 获取服务信息
        val serviceInfo = discovery.getServiceInfo()
        if (serviceInfo == null) {
            Log.w("DMP", "DMP 服务未运行")
            return false
        }
        
        // 3. 验证服务
        try {
            val response = OkHttpClient().newCall(
                Request.Builder()
                    .url("${serviceInfo.url}/health")
                    .get()
                    .build()
            ).execute()
            
            if (response.isSuccessful) {
                // 4. 创建客户端
                client = DMPServerClient(baseURL = serviceInfo.url)
                Log.d("DMP", "DMP 服务连接成功: ${serviceInfo.url}")
                
                // 5. 监听服务状态
                startServiceMonitoring()
                return true
            }
        } catch (e: Exception) {
            Log.e("DMP", "服务验证失败", e)
        }
        
        return false
    }
    
    /**
     * 监听服务状态变化
     */
    private fun startServiceMonitoring() {
        serviceListener = DMPServiceListener(context).apply {
            setServiceLostListener {
                Log.w("DMP", "DMP 服务断开，尝试重新连接")
                client = null
                // 可以触发重新发现
            }
            startListening()
        }
    }
    
    /**
     * 聊天接口
     */
    suspend fun chat(message: String): String {
        val client = client ?: throw IllegalStateException("DMP 服务未连接")
        
        return try {
            val response = client.chat(
                messages = listOf(Message(role = "user", content = message))
            )
            response.content
        } catch (e: Exception) {
            Log.e("DMP", "聊天失败", e)
            throw e
        }
    }
}
```

---

### 6.5 安全考虑

#### 权限控制

```xml
<!-- Android: 限制只有授权 App 才能访问 -->
<permission
    android:name="com.dmp.android.permission.ACCESS_SERVICE"
    android:protectionLevel="signature" />

<uses-permission android:name="com.dmp.android.permission.ACCESS_SERVICE" />
```

#### 数据加密

```kotlin
// 使用 HTTPS 而非 HTTP
val url = "https://localhost:$port"

// 或者使用本地 Socket（Android）
val localSocket = LocalSocket()
localSocket.connect(LocalSocketAddress("dmp_service"))
```

#### 签名验证

```kotlin
// 验证调用者签名
fun verifyCallerSignature(context: Context): Boolean {
    val callingUid = Binder.getCallingUid()
    val packageManager = context.packageManager
    val packages = packageManager.getPackagesForUid(callingUid)
    
    return packages?.any { packageName ->
        val signature = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNATURES
        ).signatures[0]
        
        // 验证签名是否匹配白名单
        signature.toCharsString() == EXPECTED_SIGNATURE
    } ?: false
}
```

---

---

## 8. 版本管理与向下兼容策略

### 8.1 语义化版本（Semantic Versioning）

DMP 采用业界标准的语义化版本规范：`主版本.次版本.修订版本`

```
v1.2.3
│ │ │
│ │ └─ 修订版本：Bug 修复，完全兼容
│ └─── 次版本：新增功能，向后兼容
└───── 主版本：破坏性变更，可能不兼容
```

**版本更新规则**：

| 更新类型 | 示例 | 兼容性 | 第三方 App 需要更新 |
|----------|------|--------|---------------------|
| 修订版本 | 1.2.3 → 1.2.4 | ✅ 完全兼容 | ❌ 不需要 |
| 次版本 | 1.2.x → 1.3.0 | ✅ 向后兼容 | ❌ 不需要（旧 API 仍可用） |
| 主版本 | 1.x.x → 2.0.0 | ⚠️ 可能不兼容 | ✅ 需要迁移 |

---

### 8.2 多版本协议共存

DMP 服务同时支持多个协议版本，确保向下兼容。

#### 接口路径设计

```
/v1/chat    ← 旧版 App 继续使用（v1 协议）
/v2/chat    ← 新版 App 使用（v2 协议）
/v3/chat    ← 最新版本（v3 协议）
```

#### 或使用请求头指定版本

```http
POST /chat
Content-Type: application/json
X-Protocol-Version: v2

{
  "messages": [
    {"role": "user", "content": "你好"}
  ]
}
```

#### 服务端实现示例

```kotlin
class DMPProtocolHandler {
    
    // 支持多个协议版本
    private val adapters = mapOf(
        "v1" to V1ProtocolAdapter(),
        "v2" to V2ProtocolAdapter(),
        "v3" to V3ProtocolAdapter()  // 最新版本
    )
    
    fun handleRequest(request: Request, version: String): Response {
        val adapter = adapters[version] 
            ?: run {
                // 如果版本不支持，降级到最新兼容版本
                Log.w("DMP", "不支持的版本 $version，降级到 v3")
                adapters["v3"]!!
            }
        
        // 使用对应版本的适配器处理
        return adapter.handle(request)
    }
}
```

---

### 8.3 废弃标记与过渡期

当需要废弃旧版本协议时，采用渐进式废弃策略：

#### 废弃时间线示例

```
v1 发布: 2023-01-01
v2 发布: 2023-06-01 (v1 标记为废弃，过渡期 6 个月)
v3 发布: 2024-01-01 (v2 标记为废弃，v1 停止支持)
```

#### 废弃警告响应头

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Protocol-Version: v1
X-Deprecation-Warning: true
X-Deprecation-Message: "v1 协议将于 2025-01-01 停止支持，请升级到 v3"
X-Sunset-Date: 2025-01-01
X-Migration-Guide: "https://dmp.example.com/migrate/v1-to-v3"
```

---

### 8.4 能力协商机制

客户端和服务端通过能力协商，自动选择最优协议版本。

#### 能力查询接口

```http
GET /capabilities
```

**请求参数**：

```json
{
  "client_version": "1.2.0",
  "supported_protocols": ["v1", "v2"],
  "supported_features": ["chat", "chat_stream", "provider_status"]
}
```

**响应**：

```json
{
  "server_version": "3.0.0",
  "negotiated_protocol": "v2",
  "available_features": [
    "chat",
    "chat_stream",
    "provider_status",
    "multi_model"
  ],
  "deprecated_features": ["simple_chat"],
  "latest_protocol": "v3"
}
```

---

### 8.5 健康检查接口（增强版）

```http
GET /health
```

**响应**：

```json
{
  "status": "ok",
  "server_version": "3.0.0",
  "supported_protocols": ["v1", "v2", "v3"],
  "deprecated_protocols": {
    "v1": {
      "sunset_date": "2025-01-01",
      "migration_guide": "https://dmp.example.com/migrate/v1-to-v3"
    }
  },
  "latest_protocol": "v3",
  "providers_count": 3
}
```

**响应参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| status | string | 服务状态（ok/error） |
| server_version | string | DMP 服务版本 |
| supported_protocols | array | 支持的协议版本列表 |
| deprecated_protocols | object | 已废弃的协议版本及迁移信息 |
| deprecated_protocols.{version}.sunset_date | string | 停止支持日期 |
| deprecated_protocols.{version}.migration_guide | string | 迁移指南链接 |
| latest_protocol | string | 最新协议版本 |
| providers_count | integer | 已注册的提供者数量 |

---

### 8.6 版本兼容性测试

DMP 服务每次更新必须通过兼容性测试：

```kotlin
class CompatibilityTest {
    
    @Test
    fun `v1 protocol still works`() {
        val request = V1ChatRequest(message = "你好")
        val response = server.handleChat(request, version = "v1")
        assertEquals("v1", response.protocolVersion)
        assertNotNull(response.content)
    }
    
    @Test
    fun `v2 protocol still works`() {
        val request = V2ChatRequest(
            messages = listOf(Message("user", "你好"))
        )
        val response = server.handleChat(request, version = "v2")
        assertEquals("v2", response.protocolVersion)
    }
    
    @Test
    fun `deprecated version returns warning`() {
        val response = server.handleChat(request, version = "v1")
        assertTrue(response.headers.containsKey("X-Deprecation-Warning"))
        assertTrue(response.headers["X-Deprecation-Message"].isNotEmpty())
    }
    
    @Test
    fun `unsupported version downgrades to latest`() {
        val response = server.handleChat(request, version = "v99")
        assertEquals("v3", response.protocolVersion)  // 降级到最新版本
    }
}
```

---

### 8.7 迁移指南模板

当需要废弃旧版本时，提供完整的迁移指南：

```markdown
# v1 到 v3 迁移指南

## 变更说明

### 请求格式变更
- v1 使用 `message` 字段（字符串）
- v3 使用 `messages` 数组（支持多轮对话）

### 新增功能
- v3 支持流式输出（`/v3/chat/stream`）
- v3 支持温度参数（`temperature`）
- v3 支持最大 token 控制（`max_tokens`）

## 迁移步骤

### 1. 修改请求格式

**v1 格式（旧）**：
```json
{
  "message": "你好"
}
```

**v3 格式（新）**：
```json
{
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048
}
```

### 2. 修改请求头

```http
POST /v3/chat
X-Protocol-Version: v3
```

### 3. 处理响应格式变更

**v1 响应（旧）**：
```json
{
  "response": "你好！有什么可以帮助你的？"
}
```

**v3 响应（新）**：
```json
{
  "content": "你好！有什么可以帮助你的？",
  "provider": "openai",
  "model": "gpt-4",
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 20,
    "total_tokens": 35
  },
  "finish_reason": "stop"
}
```

## 时间线

- 2024-06-01: v1 标记为废弃
- 2024-12-01: v1 停止支持
- 建议在此之前完成迁移

## 技术支持

如有问题，请联系：
- 文档：https://dmp.example.com/docs
- 邮件：support@dmp.example.com
```

---

### 8.8 客户端版本检测示例

#### Android (Kotlin)

```kotlin
class DMPVersionChecker(private val client: OkHttpClient) {
    
    suspend fun checkServerCompatibility(): CompatibilityInfo {
        val response = client.newCall(
            Request.Builder()
                .url("http://localhost:28473/health")
                .get()
                .build()
        ).execute()
        
        val body = response.body?.string() ?: throw Exception("Empty response")
        val json = Json.parseToJsonElement(body).jsonObject
        
        return CompatibilityInfo(
            serverVersion = json["server_version"]?.jsonPrimitive?.content ?: "",
            supportedProtocols = json["supported_protocols"]?.jsonArray?.map { 
                it.jsonPrimitive.content 
            } ?: emptyList(),
            deprecatedProtocols = parseDeprecatedProtocols(json["deprecated_protocols"]),
            latestProtocol = json["latest_protocol"]?.jsonPrimitive?.content ?: "v1"
        )
    }
    
    suspend fun negotiateProtocol(clientSupported: List<String>): String {
        val serverInfo = checkServerCompatibility()
        
        // 找到客户端和服务端都支持的 highest 版本
        val commonProtocols = clientSupported.intersect(serverInfo.supportedProtocols.toSet())
        
        return if (commonProtocols.isEmpty()) {
            // 没有共同支持的版本，使用最新协议
            Log.w("DMP", "没有共同支持的协议版本，使用最新版本")
            serverInfo.latestProtocol
        } else {
            // 使用最高版本
            commonProtocols.maxBy { 
                it.substring(1).toInt()  // 提取版本号数字
            }
        }
    }
}

data class CompatibilityInfo(
    val serverVersion: String,
    val supportedProtocols: List<String>,
    val deprecatedProtocols: Map<String, DeprecationInfo>,
    val latestProtocol: String
)

data class DeprecationInfo(
    val sunsetDate: String,
    val migrationGuide: String
)

// 使用示例
val versionChecker = DMPVersionChecker(OkHttpClient())

// 检查兼容性
val compatibility = versionChecker.checkServerCompatibility()
println("服务端版本: ${compatibility.serverVersion}")
println("支持的协议: ${compatibility.supportedProtocols}")

// 协商协议
val negotiatedProtocol = versionChecker.negotiateProtocol(
    clientSupported = listOf("v1", "v2")
)
println("协商后的协议: $negotiatedProtocol")
```

#### iOS (Swift)

```swift
struct DMPCompatibilityInfo: Codable {
    let serverVersion: String
    let supportedProtocols: [String]
    let deprecatedProtocols: [String: DeprecationInfo]
    let latestProtocol: String
}

struct DeprecationInfo: Codable {
    let sunsetDate: String
    let migrationGuide: String
}

class DMPVersionChecker {
    
    func checkServerCompatibility() async throws -> DMPCompatibilityInfo {
        guard let url = URL(string: "http://localhost:28473/health") else {
            throw NSError(domain: "DMP", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }
        
        let (data, _) = try await URLSession.shared.data(from: url)
        return try JSONDecoder().decode(DMPCompatibilityInfo.self, from: data)
    }
    
    func negotiateProtocol(clientSupported: [String]) async throws -> String {
        let serverInfo = try await checkServerCompatibility()
        
        // 找到客户端和服务端都支持的 highest 版本
        let commonProtocols = Set(clientSupported).intersection(serverInfo.supportedProtocols)
        
        if commonProtocols.isEmpty {
            print("没有共同支持的协议版本，使用最新版本")
            return serverInfo.latestProtocol
        } else {
            // 使用最高版本
            return commonProtocols.sorted { v1, v2 in
                let num1 = Int(v1.dropFirst()) ?? 0
                let num2 = Int(v2.dropFirst()) ?? 0
                return num1 > num2
            }.first!
        }
    }
}

// 使用示例
Task {
    do {
        let checker = DMPVersionChecker()
        let compatibility = try await checker.checkServerCompatibility()
        print("服务端版本: \(compatibility.serverVersion)")
        print("支持的协议: \(compatibility.supportedProtocols)")
        
        let negotiatedProtocol = try await checker.negotiateProtocol(
            clientSupported: ["v1", "v2"]
        )
        print("协商后的协议: \(negotiatedProtocol)")
    } catch {
        print("版本检查失败: \(error)")
    }
}
```

---

### 8.9 向下兼容检查清单

DMP 服务每次发布新版本前，必须完成以下检查：

```markdown
## 发布前检查清单

### 协议兼容性
- [ ] v1 协议仍然可用（如果未到达 sunset date）
- [ ] v2 协议仍然可用（如果未到达 sunset date）
- [ ] v3 协议正常工作
- [ ] 废弃版本返回正确的警告头

### API 兼容性
- [ ] 所有旧版接口路径仍然可用（/v1/*, /v2/*）
- [ ] 请求参数向后兼容
- [ ] 响应格式向后兼容
- [ ] 错误码保持一致

### 测试
- [ ] v1 协议兼容性测试通过
- [ ] v2 协议兼容性测试通过
- [ ] 版本协商测试通过
- [ ] 降级逻辑测试通过

### 文档
- [ ] 更新接口文档
- [ ] 更新迁移指南（如有废弃）
- [ ] 更新 CHANGELOG
```

---

## 9. 版本信息

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2024-01-15 | 初始版本 |