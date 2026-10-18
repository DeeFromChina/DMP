# DMP Android - Device Model Protocol

Android platform local MCP service implementation, automatically matches the optimal LLM interface based on device model and system version.

## 📱 Device Support

### Device Brand + System Version Dual Detection

| Device Brand | Android Version | Supported LLM | Description |
|-------------|-----------------|---------------|-------------|
| Huawei | Android 14+ (API 34) | Huawei Pangu, TFLite, Cloud | Latest AI features |
| Huawei | Android 12+ (API 31) | Huawei Pangu, TFLite, Cloud | Most AI features |
| Huawei | Android 10+ (API 29) | Huawei Pangu, Cloud | Basic AI support |
| Huawei | Below Android 10 | Cloud | Cloud only |
| Xiaomi | Android 14+ (API 34) | Xiaomi Large Model, TFLite, Cloud | Latest AI features |
| Xiaomi | Android 12+ (API 31) | Xiaomi Large Model, TFLite, Cloud | Most AI features |
| Xiaomi | Android 10+ (API 29) | Xiaomi Large Model, Cloud | Basic AI support |
| Xiaomi | Below Android 10 | Cloud | Cloud only |
| OPPO | Android 14+ (API 34) | OPPO Large Model, TFLite, Cloud | Latest AI features |
| OPPO | Android 12+ (API 31) | OPPO Large Model, TFLite, Cloud | Most AI features |
| OPPO | Android 10+ (API 29) | OPPO Large Model, Cloud | Basic AI support |
| OPPO | Below Android 10 | Cloud | Cloud only |
| vivo | Android 14+ (API 34) | vivo BlueLM, TFLite, Cloud | Latest AI features |
| vivo | Android 12+ (API 31) | vivo BlueLM, TFLite, Cloud | Most AI features |
| vivo | Android 10+ (API 29) | vivo BlueLM, Cloud | Basic AI support |
| vivo | Below Android 10 | Cloud | Cloud only |
| Other Brands | Android 12+ (API 31) | TFLite, Cloud | - |
| Other Brands | Android 10+ (API 29) | TFLite, Cloud | - |
| Other Brands | Below Android 10 | Cloud | Cloud only |

**See also**: [DeviceDetector](app/src/main/java/com/dmp/android/device/DeviceDetector.kt)

## 🚀 Quick Start

### 1. Basic Usage

```kotlin
import com.dmp.android.server.DMPServer

// Create MCP server
val server = DMPServer(context)

// Start service (with automatic input validation and logging)
server.start(
    tfliteModelPath = "models/llm.tflite",
    cloudApiKey = "sk-xxx"
)

// Server is running and ready to handle MCP requests
```

### 2. Use LLM Router Directly

```kotlin
import com.dmp.android.router.LLMRouter
import com.dmp.android.models.*

val router = LLMRouter(context)

// Auto-register (based on device model and system version)
router.autoRegister(
    tfliteModelPath = "models/llm.tflite",
    cloudApiKey = "sk-xxx"
)

// Chat request (with automatic logging, TraceID, error tracking)
val request = LLMRequest(
    messages = listOf(
        Message(role = "user", content = "Hello")
    )
)

val response = router.chat(request)
println(response.content)
```

### 3. Streaming Output

```kotlin
val request = LLMRequest(
    messages = listOf(Message(role = "user", content = "Tell me a story")),
    stream = true
)

lifecycleScope.launch {
    router.chatStream(request).collect { chunk ->
        print(chunk.content)
    }
}
```

### 4. Check Provider Status

```kotlin
val status = router.getProviderStatus()
status.forEach { (name, available) ->
    println("$name: ${if (available) "✅" else "❌"}")
}
```

## 📂 Project Structure

```
app/src/main/
├── java/com/dmp/android/
│   ├── core/
│   │   ├── DMPLogger.kt              # Logging system (graded logging, trace tracking, sensitive data masking)
│   │   ├── DMPSecurity.kt            # Security module (input validation, encrypted storage, certificate verification)
│   │   ├── DMPErrorTracker.kt        # Error tracking (error codes, retry strategy, pattern analysis)
│   │   └── DMPVersionManager.kt      # Version management (version detection, config migration, capability assessment)
│   ├── config/
│   │   └── DMPConfigManager.kt       # Config manager (encrypted storage, config masking)
│   ├── device/
│   │   └── DeviceDetector.kt         # Device detector (brand + system version)
│   ├── models/
│   │   └── LLMModels.kt              # Data models
│   ├── providers/
│   │   ├── LLMProvider.kt            # LLM provider interface
│   │   ├── HuaweiProvider.kt         # Huawei HMS Core AI
│   │   ├── XiaomiProvider.kt         # Xiaomi AI Platform
│   │   ├── OPPOProvider.kt           # OPPO AI Platform
│   │   ├── VivoProvider.kt           # vivo AI Platform
│   │   ├── TFLiteProvider.kt         # TFLite local model
│   │   └── CloudProvider.kt          # Cloud API
│   ├── router/
│   │   └── LLMRouter.kt              # LLM router (auto fallback, logging)
│   ├── server/
│   │   └── DMPServer.kt              # MCP server (input validation, error handling)
│   └── ui/
│       ├── ChatActivity.kt           # Chat screen
│       ├── ConfigActivity.kt         # Config screen
│       └── MessageAdapter.kt         # Message list adapter
└── res/
    ├── layout/
    │   ├── activity_chat.xml         # Chat screen layout
    │   ├── activity_config.xml       # Config screen layout
    │   └── item_message.xml          # Message item layout
    └── values/
        ├── colors.xml                # Color definitions
        ├── strings.xml               # String resources
        └── themes.xml                # Theme styles
```

## 🛡️ Core Features

### 1. Logging System

- **Graded logging**: trace/debug/info/warn/error/fatal
- **Trace tracking**: Each request automatically generates TraceID
- **Sensitive data masking**: API Key, Token, etc. automatically masked
- **Async writing**: Does not block main thread
- **File rotation**: Automatically manages log file size

```kotlin
// Initialize
DMPLogger.setup(level = DMPLogLevel.DEBUG, enableFileOutput = true)

// Usage
DMPLogger.info("App started")
DMPLogger.debug("Debug info", file = "MainActivity.kt", function = "onCreate", line = 42)
DMPLogger.error("Error info", throwable = exception)
```

**See also**: [DMPLogger.kt](app/src/main/java/com/dmp/android/core/DMPLogger.kt)

### 2. Security Module

- **Input validation**: SQL injection detection, XSS detection, format validation
- **Encrypted storage**: EncryptedSharedPreferences (AES-256-GCM)
- **Certificate verification**: HTTPS enforced, certificate pinning
- **Data masking**: API Key, Token, User ID masking

```kotlin
// Input validation
DMPInputValidator.validateMessage(message)
DMPInputValidator.validateAPIKey(apiKey)
DMPInputValidator.validateURL(baseURL)

// Encrypted storage (automatic)
val secureStorage = DMPSecureStorage(context)
secureStorage.saveAPIKey("sk-xxx", "cloud_api_key")
val key = secureStorage.loadAPIKey("cloud_api_key")
```

**See also**: [DMPSecurity.kt](app/src/main/java/com/dmp/android/core/DMPSecurity.kt)

### 3. Error Tracking

- **Unified error codes**: Network/Auth/Config/Business/System errors
- **Automatic retry**: Exponential backoff strategy
- **Error analysis**: Consecutive error detection, pattern recognition
- **Error statistics**: Error rate, category statistics

```kotlin
// Error tracking
DMPErrorTracker.trackError(error)

// Get statistics
val stats = DMPErrorTracker.getErrorStats()
println("Total errors: ${stats.totalErrors}")
println("Error rate: ${stats.errorRate}")

// Execute with retry
val response = withRetry {
    provider.chat(request)
}
```

**See also**: [DMPErrorTracker.kt](app/src/main/java/com/dmp/android/core/DMPErrorTracker.kt)

### 4. Version Management

- **Version detection**: Automatic detection of app version and system version
- **Config migration**: Automatic backup and migration during major version upgrades
- **Capability assessment**: Automatic re-evaluation of AI capabilities after system upgrade
- **Backward compatibility**: Config automatically preserved, no need to re-enter

```kotlin
// Initialize
DMPVersionManager.initialize(context)

// Startup check (call on app startup)
val result = DMPVersionManager.startupCheck()

if (result.needsReinitialization) {
    // After system upgrade, re-register LLM providers
    router.autoRegister(...)
}

if (result.newAICapabilities.isNotEmpty()) {
    // Notify user of new AI capabilities
    showNewCapabilitiesAlert(result.newAICapabilities)
}
```

**See also**: [DMPVersionManager.kt](app/src/main/java/com/dmp/android/core/DMPVersionManager.kt)

## ⚙️ Configuration Management

### In-App Configuration Screen

Built-in configuration screen (ConfigActivity), users can configure directly in the app:

1. **TFLite Local Model**
   - Enable/disable local model
   - Select model file path
   - Support import from file picker

2. **Vendor APIs**
   - Huawei HMS Core AI (API Key, encrypted storage)
   - Xiaomi AI Platform (API Key, encrypted storage)
   - OPPO AI Platform (API Key, encrypted storage)
   - vivo AI Platform (API Key, encrypted storage)

3. **Cloud LLM**
   - API Key (encrypted storage)
   - Base URL (supports custom API address)
   - Model name (supports any OpenAI-compatible model)

4. **Advanced Settings**
   - Temperature (0-2, slider control)
   - Max Tokens (256-8192, slider control)
   - Preferred provider strategy (auto/local-first/cloud-first)

### Code Configuration

```kotlin
val config = DMPConfigManager.getInstance(context)

// Set TFLite local model
config.tfliteEnabled = true
config.tfliteModelPath = "models/llm.tflite"

// Set vendor APIs (automatically encrypted)
config.huaweiEnabled = true
config.huaweiApiKey = "your-huawei-api-key"

config.xiaomiEnabled = true
config.xiaomiApiKey = "your-xiaomi-api-key"

// Set cloud API (automatically encrypted)
config.cloudEnabled = true
config.cloudApiKey = "sk-xxx"
config.cloudBaseURL = "https://api.openai.com/v1"
config.cloudModelName = "gpt-3.5-turbo"

// Advanced settings
config.temperature = 0.7f
config.maxTokens = 2048
config.preferredProvider = "auto"
```

### Config Import/Export

```kotlin
// Export config (sensitive info automatically masked)
val configMap = config.exportConfig()

// Import config
config.importConfig(configMap)
```

### Open Configuration Screen

```kotlin
// Open config screen from ChatActivity
startActivity(Intent(this, ConfigActivity::class.java))

// Or add config button in Toolbar
supportActionBar?.setDisplayHomeAsUpEnabled(true)
```

## 🔧 Configuration

### TFLite Model Preparation

1. Train or export model to `.tflite` format
2. Optimize with TFLite quantization tools
3. Package into `assets/models/` directory

```kotlin
// Load from assets
val modelPath = "models/llm-quantized.tflite"
```

### Vendor API Configuration

```kotlin
// Huawei
router.autoRegister(
    huaweiApiKey = "your-huawei-api-key"
)

// Xiaomi
router.autoRegister(
    xiaomiApiKey = "your-xiaomi-api-key"
)

// OPPO
router.autoRegister(
    oppoApiKey = "your-oppo-api-key"
)

// vivo
router.autoRegister(
    vivoApiKey = "your-vivo-api-key"
)

// Cloud API
router.autoRegister(
    cloudApiKey = "sk-xxx",
    cloudBaseURL = "https://api.openai.com/v1",
    cloudModelName = "gpt-3.5-turbo"
)
```

## 📋 Roadmap

- [x] Device detector (brand + system version)
- [x] LLM provider interface
- [x] Huawei HMS Core AI provider
- [x] Xiaomi AI Platform provider
- [x] OPPO AI Platform provider
- [x] vivo AI Platform provider
- [x] TFLite local model provider
- [x] Cloud provider
- [x] LLM router (auto fallback, logging)
- [x] MCP server (input validation, error handling)
- [x] Logging system (graded logging, trace tracking, sensitive data masking)
- [x] Security module (input validation, encrypted storage, certificate verification)
- [x] Error tracking (error codes, retry strategy, pattern analysis)
- [x] Version management (version detection, config migration, capability assessment)
- [x] Config manager (encrypted storage, config masking)
- [ ] Vendor API actual integration
- [ ] TFLite inference implementation
- [ ] Full MCP protocol implementation
- [ ] Unit tests
- [ ] Sample app

## 📚 Related Documentation

- [Architecture Design](../../docs/en/internal/ARCHITECTURE.md)
- [System Design](../../docs/en/internal/DESIGN.md)
- [Logging Standards](../../docs/en/internal/LOGGING.md)
- [Security Design](../../docs/en/internal/SECURITY.md)
- [Version Management](../../docs/en/internal/VERSION_MANAGEMENT.md)