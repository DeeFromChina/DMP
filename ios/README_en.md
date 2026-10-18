# DMP iOS - Device Model Protocol

iOS platform local MCP service implementation, automatically matches the optimal LLM interface based on device model.

## 📱 Device Support

| Device Type | iOS Version | Supported LLM |
|-------------|-------------|---------------|
| iPhone 15 Pro+ | 18.0+ | Apple Intelligence, Core ML, Cloud |
| iPhone 12-14 | 16.0+ | Core ML, Cloud |
| Older iPhones | <16.0 | Cloud |
| iPad Pro (M-series) | 17.0+ | Core ML, Apple Intelligence |

## 🚀 Quick Start

### 1. Basic Usage

```swift
import DMP

// Create MCP server
let server = DMPServer()

// Start service
try await server.start(
    coreMLModelPath: Bundle.main.path(forResource: "llm", ofType: "mlmodel"),
    cloudAPIKey: "sk-xxx", // Optional
    cloudBaseURL: "https://api.openai.com/v1",
    cloudModelName: "gpt-3.5-turbo"
)

// Server is running and ready to handle MCP requests
```

### 2. Use LLM Router Directly

```swift
import DMP

let router = LLMRouter()

// Auto-register (based on device model)
router.autoRegister(
    coreMLModelPath: "/path/to/model.mlmodel",
    cloudAPIKey: "sk-xxx"
)

// Chat request
let request = LLMRequest(
    messages: [
        Message(role: "user", content = "Hello")
    ]
)

let response = try await router.chat(request: request)
print(response.content)
```

### 3. Streaming Output

```swift
let request = LLMRequest(
    messages: [Message(role: "user", content: "Tell me a story")],
    stream: true
)

for await chunk in router.chatStream(request: request) {
    print(chunk.content, terminator: "")
}
```

### 4. Check Provider Status

```swift
let status = await router.getProviderStatus()
for (name, available) in status {
    print("\(name): \(available ? "✅" : "❌")")
}
```

## 📂 Project Structure

```
Sources/
├── App/
│   └── DMPApp.swift              # SwiftUI app entry point
├── Config/
│   └── DMPConfigManager.swift    # Configuration manager
├── Device/
│   └── DeviceDetector.swift      # Device detector
├── Models/
│   └── LLMModels.swift           # Data models
├── Protocol/
│   └── MCPProtocol.swift         # MCP protocol
├── Providers/
│   ├── AppleIntelligenceProvider.swift  # Apple Intelligence
│   ├── CoreMLProvider.swift             # Core ML local model
│   └── CloudProvider.swift              # Cloud API
├── Router/
│   └── LLMRouter.swift           # LLM router
├── Server/
│   └── DMPServer.swift           # MCP server
└── UI/
    ├── ChatView.swift            # Chat interface
    └── ConfigView.swift          # Configuration interface
```

## ⚙️ Configuration Management

### In-App Configuration Screen

Built-in configuration screen, users can configure directly in the app:

1. **Core ML Local Model**
   - Enable/disable local model
   - Select model file path
   - Support import from file picker

2. **Cloud LLM**
   - API Key (secure storage)
   - Base URL (supports custom API address)
   - Model name (supports any OpenAI-compatible model)

3. **Advanced Settings**
   - Temperature (0-2)
   - Max Tokens (256-8192)
   - Preferred provider strategy (auto/local-first/cloud-first)

### Code Configuration

```swift
let config = DMPConfigManager.shared

// Set cloud API
config.cloudEnabled = true
config.cloudAPIKey = "sk-xxx"
config.cloudBaseURL = "https://api.openai.com/v1"
config.cloudModelName = "gpt-3.5-turbo"

// Set local model
config.coreMLEnabled = true
config.coreMLModelPath = "/path/to/model.mlmodel"

// Advanced settings
config.temperature = 0.7
config.maxTokens = 2048
config.preferredProvider = "auto"

// Save
config.save()
```

### Config Import/Export

```swift
// Export config
let configJSON = DMPConfigManager.shared.exportConfig()

// Import config
DMPConfigManager.shared.importConfig(configJSON)
```

## 🔧 Configuration

### Core ML Model Preparation

1. Train or export model to `.mlmodel` format
2. Optimize with `coremltools` quantization
3. Package into App Bundle or download on demand

```swift
// Load from Bundle
let modelPath = Bundle.main.path(forResource: "llm-quantized", ofType: "mlmodel")

// Or load from Documents
let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
let modelPath = documentsPath.appendingPathComponent("llm.mlmodel").path
```

### Cloud API Configuration

```swift
// OpenAI
router.autoRegister(
    cloudAPIKey: "sk-xxx",
    cloudBaseURL: "https://api.openai.com/v1",
    cloudModelName: "gpt-3.5-turbo"
)

// OpenAI-compatible API
router.autoRegister(
    cloudAPIKey: "your-key",
    cloudBaseURL: "https://your-api.com/v1",
    cloudModelName: "your-model"
)
```

## 📋 Roadmap

- [x] Device detector
- [x] LLM provider protocol
- [x] Apple Intelligence provider
- [x] Core ML provider
- [x] Cloud provider
- [x] LLM router
- [x] MCP server
- [ ] Apple Intelligence actual integration
- [ ] Full MCP protocol implementation (stdio/SSE)
- [ ] Unit tests
- [ ] Sample app