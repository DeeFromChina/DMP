# DMP iOS - 开发者指南

iOS 平台的本地 MCP 服务实现，根据设备型号自动匹配最优 LLM 接口。

## 🛠️ 开发环境

- Xcode 15.0 或更高版本
- Swift 5.9+
- iOS 16.0+
- macOS 14.0+

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd DMP/ios
```

### 2. 打开项目

用 Xcode 打开 `DMP.xcodeproj`，或直接用 Swift Package Manager：

```bash
swift build
```

### 3. 构建运行

```bash
# 用 Xcode 打开后点击 Run
# 或使用命令行
xcodebuild -project DMP.xcodeproj -scheme DMP -destination 'platform=iOS Simulator,name=iPhone 15' build
```

### 4. 运行测试

```bash
swift test
```

## 📂 项目结构

```
Sources/
├── App/
│   └── DMPApp.swift              # SwiftUI 应用入口
├── Config/
│   └── DMPConfigManager.swift    # 配置管理器
├── Device/
│   └── DeviceDetector.swift      # 设备检测器
├── Models/
│   └── LLMModels.swift           # 数据模型
├── Protocol/
│   └── MCPProtocol.swift         # MCP 协议
├── Providers/
│   ├── AppleIntelligenceProvider.swift  # Apple Intelligence
│   ├── CoreMLProvider.swift             # Core ML 本地模型
│   └── CloudProvider.swift              # 云端 API
├── Router/
│   └── LLMRouter.swift           # LLM 路由器
├── Server/
│   └── DMPServer.swift           # MCP 服务器
└── UI/
    ├── ChatView.swift            # 聊天界面
    └── ConfigView.swift          # 配置界面
```

## 🔧 核心模块说明

### LLMRouter - 智能路由

根据设备型号自动选择最优 LLM 接口，支持失败自动回退：

```swift
let router = LLMRouter()
router.autoRegister(
    coreMLModelPath: "/path/to/model.mlmodel",
    cloudAPIKey: "sk-xxx"
)

let response = try await router.chat(request: request)
```

### DMPServer - MCP 服务器

提供 HTTP API 服务，让其他 App 可以调用 LLM：

```swift
let server = DMPServer()
try await server.start(
    coreMLModelPath: Bundle.main.path(forResource: "llm", ofType: "mlmodel"),
    cloudAPIKey: "sk-xxx"
)
```

### 设备检测

根据设备型号决定启用哪些 Provider：

```swift
let detector = DeviceDetector()
let deviceType = detector.getDeviceType()  // iPhone15Pro, iPhone14, iPadPro, ...
let iosVersion = detector.getIOSVersion()  // 18.0, 17.0, ...
```

## 📋 开发路线图

- [x] 设备检测器
- [x] LLM 提供者协议
- [x] Apple Intelligence 提供者
- [x] Core ML 提供者
- [x] Cloud 提供者
- [x] LLM 路由器
- [x] MCP 服务器
- [x] 聊天界面（支持流式输出）
- [x] 配置界面
- [ ] Apple Intelligence 实际集成
- [ ] MCP 协议完整实现 (stdio/SSE)
- [ ] 单元测试
- [ ] UI 测试

## 📚 相关文档

- [公开文档](./docs/zh/public/INDEX.md) - 用户可见文档
- [内部文档](./docs/zh/internal/INDEX.md) - 开发者内部文档
- [English Docs](./docs/en/public/INDEX.md) - English documentation