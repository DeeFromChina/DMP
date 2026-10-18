# DMP Android - 开发者指南

Android 平台的本地 MCP 服务实现，根据设备型号和系统版本自动匹配最优 LLM 接口。

## 🛠️ 开发环境

- Android Studio Hedgehog 或更高版本
- JDK 17+
- Kotlin 1.9+
- minSdk 29 (Android 10)

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd DMP/android
```

### 2. 打开项目

用 Android Studio 打开 `android/` 目录，等待 Gradle 同步完成。

### 3. 构建运行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 或直接点击 Android Studio 的 Run 按钮
```

### 4. 运行测试

```bash
./gradlew test
```

## 📂 项目结构

```
app/src/main/
├── java/com/dmp/android/
│   ├── core/
│   │   ├── DMPLogger.kt              # 日志系统
│   │   ├── DMPSecurity.kt            # 安全模块
│   │   ├── DMPErrorTracker.kt        # 错误追踪
│   │   └── DMPVersionManager.kt      # 版本管理
│   ├── config/
│   │   └── DMPConfigManager.kt       # 配置管理器
│   ├── device/
│   │   └── DeviceDetector.kt         # 设备检测器
│   ├── models/
│   │   └── LLMModels.kt              # 数据模型
│   ├── providers/
│   │   ├── LLMProvider.kt            # LLM 提供者接口
│   │   ├── HuaweiProvider.kt         # 华为 HMS Core AI
│   │   ├── XiaomiProvider.kt         # 小米 AI 开放平台
│   │   ├── OPPOProvider.kt           # OPPO AI 开放平台
│   │   ├── VivoProvider.kt           # vivo AI 开放平台
│   │   ├── TFLiteProvider.kt         # TFLite 本地模型
│   │   └── CloudProvider.kt          # 云端 API
│   ├── router/
│   │   └── LLMRouter.kt              # LLM 路由器
│   ├── server/
│   │   └── DMPServer.kt              # MCP 服务器
│   └── ui/
│       ├── ChatActivity.kt           # 聊天页面
│       ├── ConfigActivity.kt         # 配置页面
│       └── MessageAdapter.kt         # 消息列表适配器
└── res/
    ├── layout/
    │   ├── activity_chat.xml         # 聊天页面布局
    │   ├── activity_config.xml       # 配置页面布局
    │   └── item_message.xml          # 消息项布局
    └── values/
        ├── colors.xml                # 颜色定义
        ├── strings.xml               # 字符串资源
        └── themes.xml                # 主题样式
```

## 🔧 核心模块说明

### LLMRouter - 智能路由

根据设备型号自动选择最优 LLM 接口，支持失败自动回退：

```kotlin
val router = LLMRouter(context)
router.autoRegister(
    tfliteModelPath = "models/llm.tflite",
    cloudApiKey = "sk-xxx"
)

val response = router.chat(request)
```

### DMPServer - MCP 服务器

提供 HTTP API 服务，让其他 App 可以调用 LLM：

```kotlin
val server = DMPServer(context)
server.start(
    tfliteModelPath = "models/llm.tflite",
    cloudApiKey = "sk-xxx"
)
```

### 设备检测

根据品牌 + 系统版本双重检测，决定启用哪些 Provider：

```kotlin
val detector = DeviceDetector(context)
val brand = detector.getBrand()      // huawei, xiaomi, oppo, vivo, other
val sdkInt = detector.getSdkInt()    // 29, 31, 34, ...
```

## 📋 开发路线图

- [x] 设备检测器（品牌+系统版本）
- [x] LLM 提供者接口
- [x] 华为 HMS Core AI 提供者
- [x] 小米 AI 开放平台提供者
- [x] OPPO AI 开放平台提供者
- [x] vivo AI 开放平台提供者
- [x] TFLite 本地模型提供者
- [x] Cloud 提供者
- [x] LLM 路由器（自动回退、日志追踪）
- [x] MCP 服务器（输入验证、错误处理）
- [x] 日志系统（分级日志、链路追踪、敏感脱敏）
- [x] 安全模块（输入验证、加密存储、证书验证）
- [x] 错误追踪（错误码、重试策略、模式分析）
- [x] 版本管理（版本检测、配置迁移、能力评估）
- [x] 配置管理器（加密存储、配置脱敏）
- [x] 聊天页面（支持流式输出）
- [x] 配置页面（应用内配置）
- [ ] 厂商 API 实际集成
- [ ] TFLite 推理实现
- [ ] MCP 协议完整实现
- [ ] 单元测试
- [ ] UI 测试

## 📚 相关文档

- [公开文档](./docs/zh/public/INDEX.md) - 用户可见文档
- [内部文档](./docs/zh/internal/INDEX.md) - 开发者内部文档
- [English Docs](./docs/en/public/INDEX.md) - English documentation