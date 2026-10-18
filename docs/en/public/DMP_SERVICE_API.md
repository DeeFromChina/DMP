# DMP Service HTTP API Documentation

## Overview

DMP (Device Model Protocol) is a local MCP service running on mobile devices, providing intelligent LLM routing functionality.

App applications call the DMP service via HTTP API, and the DMP service automatically matches the optimal LLM interface based on the device model.

### Architecture

```
┌─────────────────────┐
│   App Service       │  (Any app that needs to call LLM)
│   (MCP Client)      │
└──────────┬──────────┘
           │ HTTP API
           │ (REST + SSE)
┌──────────▼──────────┐
│   DMP Service       │  (Local service running on mobile)
│   (MCP Server)      │
│                     │
│  ┌───────────────┐  │
│  │ LLMRouter     │  │
│  │ ├─ Local      │  │
│  │ ├─ Mobile API │  │
│  │ └─ Cloud API  │  │
│  └───────────────┘  │
└─────────────────────┘
```

### Service Address

| Environment | Default Address | Description |
|-------------|-----------------|-------------|
| Android | http://localhost:28473 | Local service address (configurable) |
| iOS | http://localhost:28473 | Local service address (configurable) |

### Port Configuration

**The port is not fixed** and can be configured in the following ways:

#### 1. Environment Variables

```bash
# Set custom port
export SERVER_PORT=9090

# Start service
python main.py
```

#### 2. Configuration File

Configure in `.env` file:

```env
# DMP Service Configuration
SERVER_PORT=28473
SERVER_HOST=127.0.0.1
```

#### 3. Code Configuration

```python
# Python
from dmp.config import Settings

settings = Settings(
    server_port=9090,  # Custom port
    server_host="127.0.0.1"
)
```

```kotlin
// Android
val server = DMPServer(context)
server.start(port = 9090)  // Custom port
```

```swift
// iOS
let server = DMPServer()
try await server.start(port: 9090)  // Custom port
```

### Port Conflict Handling

The DMP service has a built-in **automatic port conflict resolution mechanism**:

#### Strategy

| Strategy | Description | Default |
|----------|-------------|---------|
| Auto-increment | If port is occupied, try next port (+1) | Enabled |
| Max Retries | Maximum number of ports to try | 10 |
| Random Port | If 10 consecutive ports are occupied, use random port (0) | Enabled |

#### Workflow

```
Start Service (port 28473)
    │
    ├─ Port available? ── Yes ──> Use 28473
    │
    No
    │
    ├─ Try 8081
    │
    ├─ Port available? ── Yes ──> Use 8081
    │
    No
    │
    ├─ Try 8082
    │
    ... (up to 10 attempts)
    │
    ├─ Use random port (0)
    │
    └─ Return actual port used
```

#### Get Actual Port

The service returns the actual port after startup:

```python
# Python
settings = Settings(server_port=28473)
mcp, fallback_manager = create_server(settings)

# After service starts
actual_port = mcp.port  # Actual port used
print(f"DMP service running on port: {actual_port}")
```

```kotlin
// Android
val server = DMPServer(context)
val actualPort = server.start(port = 28473)
Log.d("DMP", "Service running on port: $actualPort")
```

```swift
// iOS
let server = DMPServer()
let actualPort = try await server.start(port: 28473)
print("Service running on port: \(actualPort)")
```

#### Service Discovery Mechanism

App applications can discover the actual DMP service port through the following methods:

**Method 1: Broadcast Discovery**

```kotlin
// Android - Listen for local broadcast
val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val port = intent.getIntExtra("dmp_server_port", 28473)
        val url = "http://localhost:$port"
        // Use discovered port
    }
}

context.registerReceiver(receiver, IntentFilter("com.dmp.android.SERVER_STARTED"))
```

```swift
// iOS - Listen for notifications
NotificationCenter.default.addObserver(
    forName: NSNotification.Name("DMPServerStarted"),
    object: nil,
    queue: .main
) { notification in
    if let port = notification.userInfo?["port"] as? Int {
        let url = "http://localhost:\(port)"
        // Use discovered port
    }
}
```

**Method 2: Shared Configuration File**

The DMP service writes the actual port to a shared configuration file after startup:

```json
// /data/data/com.dmp.android/shared/config.json
{
  "server_port": 8081,
  "server_url": "http://localhost:8081",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

App applications read this file to get the actual port.

**Method 3: Health Check Scanning**

```python
# Python - Scan possible port range
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
    print(f"DMP service running on port: {port}")
```

### General Notes

- **Protocol**: HTTP/1.1
- **Data Format**: JSON
- **Character Encoding**: UTF-8
- **Timeout**: 30 seconds (configurable)
- **Port Range**: 1024-65535 (recommend using 8000-9000 range)

---

## 1. API Endpoints

### 1.1 Chat (Non-streaming)

**Description**: Send a chat request and wait for the complete response.

**Request**:
```http
POST /chat
Content-Type: application/json

{
  "messages": [
    {"role": "system", "content": "You are an assistant"},
    {"role": "user", "content": "Hello"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048,
  "model": "",
  "preferred_provider": "any"
}
```

**Request Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| messages | array | Yes | - | List of conversation messages |
| messages[].role | string | Yes | - | Message role (system/user/assistant) |
| messages[].content | string | Yes | - | Message content |
| temperature | number | No | 0.7 | Temperature parameter (0.0-2.0) |
| max_tokens | integer | No | 2048 | Maximum tokens to generate (1-8192) |
| model | string | No | "" | Specify model name (empty string means auto-select) |
| preferred_provider | string | No | "any" | Preferred provider type (local/mobile/cloud/any) |

**Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "content": "Hello! How can I help you?",
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

**Response Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| content | string | Content generated by LLM |
| provider | string | Name of the actual provider used |
| model | string | Name of the actual model used |
| usage | object | Token usage information |
| usage.prompt_tokens | integer | Input token count |
| usage.completion_tokens | integer | Output token count |
| usage.total_tokens | integer | Total token count |
| finish_reason | string | Completion reason (stop/length/error) |

**Error Response**:
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "error": {
    "code": 500,
    "message": "All LLM providers failed"
  }
}
```

---

### 1.2 Chat (Streaming)

**Description**: Send a chat request and receive streaming response via Server-Sent Events (SSE).

**Request**:
```http
POST /chat/stream
Content-Type: application/json
Accept: text/event-stream

{
  "messages": [
    {"role": "user", "content": "Tell me a story"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048
}
```

**Request Parameters**: Same as non-streaming endpoint

**Response**:
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"content":"Once upon a time","provider":"openai","model":"gpt-4","finish_reason":null}

data: {"content":"there was a mountain","provider":"openai","model":"gpt-4","finish_reason":null}

data: {"content":"","provider":"openai","model":"gpt-4","finish_reason":"stop"}

data: [DONE]
```

**Response Format**:

Each SSE event contains a JSON object:

| Parameter | Type | Description |
|-----------|------|-------------|
| content | string | Current generated content snippet |
| provider | string | Name of the actual provider used |
| model | string | Name of the actual model used |
| finish_reason | string \| null | Completion reason (null means not finished) |

Stream ends with `data: [DONE]`.

---

### 1.3 Get Provider Status

**Description**: Get status information for all LLM providers.

**Request**:
```http
GET /status
```

**Response**:
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

**Response Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| name | string | Provider name |
| priority | integer | Priority (lower number = higher priority) |
| available | boolean | Whether the provider is available |
| type | string | Provider type (local/mobile/cloud) |

---

### 1.4 Health Check

**Description**: Check if the DMP service is running normally, returns service version and protocol information.

**Request**:
```http
GET /health
```

**Response**:
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

**Response Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| status | string | Service status (ok/error) |
| server_version | string | DMP service version |
| supported_protocols | array | List of supported protocol versions |
| deprecated_protocols | object | Deprecated protocol versions with migration info |
| latest_protocol | string | Latest protocol version |
| providers_count | integer | Number of registered providers |

---

### 1.5 Get Server Capabilities

**Description**: Get the protocol versions and feature list supported by the server.

**Request**:
```http
GET /capabilities
```

**Response**:
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

### 1.6 Protocol Negotiation

**Description**: Client declares supported protocol versions, server negotiates the optimal protocol.

**Request**:
```http
POST /capabilities/negotiate
Content-Type: application/json

{
  "client_version": "1.0.0",
  "supported_protocols": ["v1"],
  "supported_features": ["chat", "chat_stream"]
}
```

**Response**:
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

## 2. Error Codes

### 2.1 HTTP Status Codes

| Status Code | Description | Suggestion |
|-------------|-------------|------------|
| 200 | Request successful | - |
| 400 | Bad request | Check request parameter format and range |
| 404 | Endpoint not found | Check endpoint path |
| 500 | Internal server error | Check error message and DMP service status |
| 503 | Service unavailable | DMP service not started or restarting |

---

## 3. Code Examples

### 3.1 Android (Kotlin)

#### Non-streaming Call

```kotlin
import okhttp3.*
import kotlinx.serialization.json.*

suspend fun chat(message: String): String {
    val client = OkHttpClient()
    val json = Json { ignoreUnknownKeys = true }
    
    val requestBody = """
    {
        "messages": [
            {"role": "user", "content": "$message"}
        ],
        "temperature": 0.7,
        "max_tokens": 2048
    }
    """.trimIndent().toRequestBody("application/json".toMediaType())
    
    // Send request
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

#### Streaming Call

```kotlin
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

// Usage example
lifecycleScope.launch {
    chatStream("Tell me a story").collect { chunk ->
        textView.append(chunk)
    }
}
```

#### Get Provider Status

```kotlin
suspend fun getProviderStatus() {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("http://localhost:28473/status")
        .get()
        .build()
    
    client.newCall(request).execute().use { response ->
        val body = response.body?.string()
        println("Provider status: $body")
    }
}
```

---

### 3.2 iOS (Swift)

#### Non-streaming Call

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

// Usage example
Task {
    do {
        let response = try await chat(message: "Hello")
        print(response)
    } catch {
        print("Error: \(error)")
    }
}
```

#### Streaming Call

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

// Usage example
Task {
    do {
        let stream = chatStream(message: "Tell me a story")
        for try await chunk in stream {
            print(chunk, terminator: "")
        }
    } catch {
        print("Error: \(error)")
    }
}
```

#### Get Provider Status

```swift
func getProviderStatus() async throws {
    let url = URL(string: "http://localhost:28473/status")!
    let (data, _) = try await URLSession.shared.data(from: url)
    
    let json = try JSONSerialization.jsonObject(with: data)
    print("Provider status: \(json)")
}
```

---

### 3.3 Python

#### Non-streaming Call

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
    
    return response.json()["content"]

# Usage example
result = chat("Hello")
print(result)
```

#### Streaming Call

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

# Usage example
for chunk in chat_stream("Tell me a story"):
    print(chunk, end="", flush=True)
```

#### Get Provider Status

```python
import requests

def get_provider_status():
    response = requests.get("http://localhost:28473/status")
    return response.json()

# Usage example
status = get_provider_status()
for provider in status:
    print(f"{provider['name']}: {'Available' if provider['available'] else 'Unavailable'}")
```

---

### 3.4 cURL

#### Non-streaming Call

```bash
curl -X POST http://localhost:28473/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Hello"}
    ],
    "temperature": 0.7,
    "max_tokens": 2048
  }'
```

#### Streaming Call

```bash
curl -X POST http://localhost:28473/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "messages": [
      {"role": "user", "content": "Tell me a story"}
    ],
    "stream": true
  }'
```

#### Get Provider Status

```bash
curl http://localhost:28473/status
```

---

## 4. MCP Protocol (Optional)

In addition to HTTP API, DMP also supports MCP (Model Context Protocol) for more advanced integration scenarios. See [MCP Documentation](../../internal/ARCHITECTURE.md) for details.