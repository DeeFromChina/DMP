<div align="center">

[English](./docs/en/public/INDEX.md) · **简体中文**

# DMP：设备上的 AI 模型服务

### 让手机上的任意 App 都能方便地调用 LLM。

**看到想用的 AI 功能，直接在设备上调用。**

[![License](https://img.shields.io/badge/license-MIT-f4c430?style=flat-square)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-10+-3DDC84?style=flat-square&logo=android&logoColor=white)](#支持设备)
[![iOS](https://img.shields.io/badge/iOS-16+-000000?style=flat-square&logo=apple&logoColor=white)](#支持设备)
[![MCP](https://img.shields.io/badge/MCP-Server-5c4ee5?style=flat-square)](#架构)

**[快速开始](#快速开始) · [架构](#架构) · [集成教程](#集成教程) · [支持设备](#支持设备) · [文档](#文档)**

</div>

> [!IMPORTANT]
> DMP 运行在手机设备上，提供本地 HTTP API 和 AIDL SDK 两种调用方式。请通过官方仓库获取，相似的名称或复制的内容不能证明来自官方来源。

---

DMP (Device Model Protocol) 是运行在手机设备上的本地 AI 服务，提供智能 LLM 路由功能。用户可以在手机上直接调用各大厂商内置的 AI 大模型，或云端 LLM API。

DMP 将智能体或任意 App 连接到各类 LLM 提供方，用于调用厂商内置模型、云端 API 和本地部署模型。服务在本机运行，结果包含响应内容和性能指标。

## 快速开始

### 1. 安装应用

根据你的设备选择安装方式：

| 平台 | 安装指南 |
|------|----------|
| Android | [Android 安装指南](./android/docs/zh/public/INSTALL_GUIDE.md) |
| iOS | [iOS 安装指南](./ios/docs/zh/public/INSTALL_GUIDE.md) |

### 2. 配置 LLM

安装完成后打开 DMP 应用，进入配置页面：

1. **厂商内置 AI**：如果设备支持，会自动检测并启用
2. **云端 LLM**：填写 API Key 和 Base URL（可选）
3. **本地模型**：导入 TFLite/Core ML 模型文件（可选）

### 3. 测试对话

在 DMP 应用中直接发送一条消息，测试 LLM 调用是否成功：

- 如果收到回复，说明配置成功
- 页面会显示使用的 Provider 名称和响应时间

## DMP 的工作原理

DMP 提供两种调用方式，供其他 App 集成：

- **HTTP API**：通过 REST + SSE 接口调用，适用于任意平台和语言
- **AIDL SDK**（Android 专属）：通过 Binder IPC 调用，性能更高，支持流式回调

LLMRouter 根据设备型号和可用 Provider 自动路由到最优的 LLM 接口，失败时自动回退到备选 Provider。

```
┌─────────────────────┐
│   任意 App           │  (需要调用 LLM 的应用)
│                     │
│  ┌───────────────┐  │
│  │ HTTP API      │  │── 适用于所有平台
│  │ AIDL SDK      │  │── Android 专属，高性能 IPC
│  └───────┬───────┘  │
└──────────┬──────────┘
           │ HTTP / AIDL
┌──────────▼──────────┐
│   DMP 服务          │  (运行在手机上的本地服务)
│   (MCP Server)      │
│                     │
│  ┌───────────────┐  │
│  │ LLMRouter     │  │
│  │ ├─ 厂商内置AI │  │
│  │ ├─ 本地模型   │  │
│  │ └─ 云端API    │  │
│  └───────────────┘  │
└─────────────────────┘
```

## 集成教程

### 方式一：HTTP API 调用

适用于所有平台，通过标准 HTTP 请求即可调用 DMP 服务。

#### 步骤 1：发现 DMP 服务端口

DMP 服务默认运行在 `localhost:28473`。Android 设备可通过 ContentProvider 自动发现实际端口：

```kotlin
val uri = Uri.parse("content://com.dmp.android.service/config")
val cursor = contentResolver.query(uri, null, null, null, null)
cursor?.use {
    if (it.moveToFirst()) {
        val port = it.getInt(it.getColumnIndex("http_port"))
        val ip = it.getString(it.getColumnIndex("http_ip")) ?: "127.0.0.1"
        val baseUrl = "http://$ip:$port"
    }
}
```

#### 步骤 2：发送聊天请求

```kotlin
val jsonBody = JSONObject().apply {
    put("messages", JSONArray().apply {
        put(JSONObject().apply {
            put("role", "user")
            put("content", "你好")
        })
    })
    put("temperature", 0.7)
    put("max_tokens", 2048)
}

val request = Request.Builder()
    .url("$baseUrl/chat")
    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
    .build()

client.newCall(request).execute().use { response ->
    val body = response.body?.string() ?: ""
    val json = JSONObject(body)
    val content = json.getString("content")
    val provider = json.optString("provider", "")
    val model = json.optString("model", "")
}
```

#### 步骤 3：流式调用（SSE）

```kotlin
val request = Request.Builder()
    .url("$baseUrl/chat/stream")
    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
    .header("Accept", "text/event-stream")
    .build()

client.newCall(request).execute().use { response ->
    response.body?.source()?.use { source ->
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data: ")) {
                val data = line.substring(6)
                if (data == "[DONE]") break
                val chunk = JSONObject(data)
                val content = chunk.optString("content", "")
                // 实时更新 UI
            }
        }
    }
}
```

#### 可用 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/chat` | POST | 非流式聊天 |
| `/chat/stream` | POST | 流式聊天（SSE） |
| `/status` | GET | 获取 Provider 状态 |
| `/capabilities` | GET | 获取服务端能力 |
| `/capabilities/negotiate` | POST | 协议协商 |

### 方式二：AIDL SDK 调用（Android 专属）

通过 AIDL Binder IPC 调用 DMP 服务，性能优于 HTTP，支持流式回调。

#### 步骤 1：添加 AIDL 文件

在你的项目中添加以下 AIDL 接口文件：

```
src/main/aidl/com/dmp/android/service/
├── IDMPServer.aidl        # 主服务接口
├── IStreamCallback.aidl   # 流式回调接口
├── Message.aidl           # 消息 Parcelable
└── LLMResponse.aidl       # 响应 Parcelable
```

AIDL 文件可从 [DMP 仓库](./android/app/src/main/aidl/com/dmp/android/service/) 获取。

#### 步骤 2：绑定 DMP 服务

```kotlin
private var aidlService: IDMPServer? = null
private var serviceConnected = false

private fun connectDMPService() {
    val intent = Intent().apply {
        component = ComponentName(
            "com.dmp.android",
            "com.dmp.android.service.DMPService"
        )
    }
    
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aidlService = IDMPServer.Stub.asInterface(service)
            serviceConnected = true
        }
        
        override fun onServiceDisconnected(name: ComponentName) {
            aidlService = null
            serviceConnected = false
        }
    }
    
    bindService(intent, connection, BIND_AUTO_CREATE)
}
```

#### 步骤 3：非流式聊天

```kotlin
val messages = listOf(
    Message("user", "你好")
)

val response = aidlService!!.chat(messages, 0.7, 2048)
println("回复: ${response.content}")
println("Provider: ${response.provider}")
println("模型: ${response.model}")
```

#### 步骤 4：流式聊天

```kotlin
val messages = listOf(
    Message("user", "讲个故事")
)

val fullContent = StringBuilder()

aidlService!!.chatStream(messages, 0.7, 2048, object : IStreamCallback.Stub() {
    override fun onChunk(chunk: String) {
        fullContent.append(chunk)
        // 实时更新 UI
    }
    
    override fun onComplete() {
        println("完成: $fullContent")
    }
    
    override fun onError(error: String) {
        println("错误: $error")
    }
})
```

#### AIDL 接口说明

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `chat` | messages, temperature, maxTokens | LLMResponse | 非流式聊天 |
| `chatStream` | messages, temperature, maxTokens, callback | void | 流式聊天 |
| `isRunning` | - | boolean | 检查服务状态 |
| `getActiveProvider` | - | String | 获取当前 Provider |
| `getCurrentModel` | - | String | 获取当前模型 |

## 支持设备

DMP 支持 Android 10+ 和 iOS 16+。不同设备和系统版本支持的 LLM API 有所不同：

### Android

| 设备品牌 | Android 版本 | 本地 LLM API | 云端 LLM API | 说明 |
|---------|-------------|-------------|-------------|------|
| Google (Pixel) | Android 14+ (API 34+) | ✅ ML Kit GenAI (Gemini Nano) | TFLite、Cloud | 系统级端侧模型 API，完全离线 |
| 三星 (Samsung) | Android 14+ (API 34+) | ✅ 通过 Google ML Kit | TFLite、Cloud | Galaxy AI，通过 Google API 间接支持 |
| 一加 (OnePlus) | Android 14+ (API 34+) | ✅ ML Kit GenAI (部分机型) | TFLite、Cloud | OnePlus 13/13s/Pad 3 等旗舰机型支持 |
| OPPO | Android 14+ (API 34+) | ✅ ML Kit GenAI (部分机型) | 安第斯大模型 (云端)、TFLite、Cloud | Find N5/X8 系列支持端侧，其他仅云端 |
| 小米 | Android 10+ | ❌ 仅云端 | MiMo 大模型 (云端)、TFLite、Cloud | MiMo API 为云端推理服务，非本地 |
| vivo | Android 10+ | ❌ 仅云端 | 蓝心大模型 (云端)、TFLite、Cloud | 蓝心 API 为云端服务，非本地 |
| 荣耀 | Android 10+ | ❌ 仅云端 | 魔法大模型 (云端)、TFLite、Cloud | 端云协同方案，非直接本地 API |
| 华为 | Android 10+ | ❌ 仅云端/自部署 | 盘古大模型 (云端)、TFLite、Cloud | HarmonyOS 手机端无预置文本生成 LLM |
| 其他品牌 | Android 10+ | ❌ 无开放 API | TFLite、Cloud | 可通过 TFLite 自行部署本地模型 |

> **注意**：Google ML Kit GenAI APIs 支持的设备需搭载兼容的旗舰芯片（如 Snapdragon 8 Gen 2/3、Dimensity 9200/9300 等）。具体支持情况请参考 [Google AI Edge 文档](https://ai.google.dev/gemini-api/docs/ml-kit)。

### iOS

| 设备类型 | iOS 版本 | 系统级本地 LLM API | 本地模型部署 | 云端 LLM API | 说明 |
|---------|---------|-------------------|-------------|-------------|------|
| iPhone 15 Pro+ | 18.0+ | ✅ Foundation Models (Apple Intelligence) | ✅ Core ML | Cloud | 系统级端侧模型 API，支持 Private Cloud Compute |
| iPhone 16 系列 | 18.0+ | ✅ Foundation Models (Apple Intelligence) | ✅ Core ML | Cloud | 完整 Apple Intelligence 支持 |
| iPhone 12-14 | 16.0+ | ❌ 不支持 Apple Intelligence | ✅ Core ML | Cloud | 可通过 Core ML 自行部署本地模型 |
| 旧款 iPhone | <16.0 | ❌ 不支持 | ❌ 不支持 | Cloud | 仅支持云端 LLM |
| iPad Pro (M 系列) | 17.0+ | ✅ Foundation Models (Apple Intelligence) | ✅ Core ML | Cloud | M 系列芯片完整支持 |
| iPad Air/mini | 17.0+ | ❌ 不支持 Apple Intelligence | ✅ Core ML | Cloud | 可通过 Core ML 自行部署本地模型 |

> **注意**：Apple Intelligence 需要 A17 Pro 或 M 系列及以上芯片，且系统版本需 iOS 18.0+。

## 常见问题

<details>
<summary><strong>DMP 会上传我的数据吗？</strong></summary>

DMP 在设备本地运行，HTTP API 仅在本机监听。云端 LLM 调用由你自行配置的 API Key 控制，数据是否上传取决于你选择的 Provider 和其隐私政策。

</details>

<details>
<summary><strong>HTTP API 和 AIDL SDK 有什么区别？</strong></summary>

- **HTTP API**：跨平台，适用于所有语言和框架，通过标准 REST 接口调用
- **AIDL SDK**：Android 专属，通过 Binder IPC 通信，性能更高，支持原生流式回调

推荐优先使用 AIDL SDK（Android），其他平台使用 HTTP API。

</details>

<details>
<summary><strong>支持哪些 LLM Provider？</strong></summary>

- **厂商内置模型**：Google Gemini Nano (ML Kit)、Apple Intelligence (Foundation Models)
- **云端厂商模型**：华为盘古、小米 MiMo、vivo 蓝心、OPPO 安第斯、荣耀魔法大模型等
- **云端 LLM**：OpenAI、Claude、通义千问、DeepSeek 等
- **本地模型**：TFLite (Android)、Core ML (iOS) 自行部署的端侧模型

</details>

<details>
<summary><strong>遇到错误时该怎么办？</strong></summary>

- 检查 DMP 应用内的配置页面，确认 Provider 配置正确
- 查看日志输出，确认 HTTP 服务正常启动
- 如果问题仍然存在，请[提交 issue](https://github.com/your-org/DMP/issues)，附上设备型号、系统版本、复现步骤和错误输出

</details>

## 文档

可以先阅读网站上的[实操指南](./docs/zh/public/INDEX.md)。具体选项、前提条件和接口说明见：

- [安装与设置](./android/docs/zh/public/INSTALL_GUIDE.md)：Android 和 iOS 安装指南。
- [核心文档](./docs/zh/public/INDEX.md)：公开文档，面向普通用户。
- [内部文档](./docs/zh/internal/INDEX.md)：内部文档，面向开发者。
- [Android 文档](./android/docs/zh/public/INDEX.md)：Android 平台文档。
- [iOS 文档](./ios/docs/zh/public/INDEX.md)：iOS 平台文档。
- [开发者指南](./android/README.md)：本地启动和参与开发的指南。

## 构建 APK / IPA

### Android

```bash
cd android

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 安装到已连接设备
./gradlew installDebug
```

APK 输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`

### iOS

用 Xcode 打开 `ios/DMP.xcodeproj`，选择目标设备后点击 **Product → Build** 即可构建。

## 参与贡献

欢迎帮助改进 DMP！你可以[提交 issue](https://github.com/your-org/DMP/issues) 报告错误或建议功能，也可以[提交 pull request](https://github.com/your-org/DMP/pulls) 改进代码或文档。

开发设置和检查见 [Android 开发者指南](./android/README.md) 或 [iOS 开发者指南](./ios/README.md)。

## 许可证

[MIT](./LICENSE)

DMP 自行编写的代码采用 MIT 许可；第三方内容保留各自的许可和著作权声明。