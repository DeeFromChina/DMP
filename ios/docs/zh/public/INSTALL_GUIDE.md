# DMP iOS - 安装与使用指南

DMP 是运行在 iPhone 上的本地 AI 服务，让你可以直接调用 Apple Intelligence、Core ML 本地模型或云端 LLM。

## 📱 设备要求

| 设备类型 | iOS 版本 | 支持 LLM |
|---------|---------|---------|
| iPhone 15 Pro+ | 18.0+ | Apple Intelligence、Core ML、Cloud |
| iPhone 12-14 | 16.0+ | Core ML、Cloud |
| 旧款 iPhone | <16.0 | Cloud |
| iPad Pro (M 系列) | 17.0+ | Core ML、Apple Intelligence |

## 📥 安装方式

### 方式一：TestFlight（推荐）

1. 打开 TestFlight 邀请链接
2. 点击"安装"按钮
3. 等待下载完成

### 方式二：本地构建

如果你有开发环境，可以参考 [开发者指南](../../../README.md) 自行构建。

## ⚙️ 配置 LLM

安装完成后打开 DMP 应用：

### 1. 厂商内置 AI

- 如果你的设备支持 Apple Intelligence，会自动检测并启用
- 无需额外配置

### 2. 云端 LLM（可选）

1. 点击设置图标进入配置页面
2. 填写 API Key（如 OpenAI、Claude 等）
3. 填写 Base URL（默认 `https://api.openai.com/v1`）
4. 选择模型名称（如 `gpt-3.5-turbo`）

### 3. 本地模型（可选）

1. 准备 Core ML 格式的模型文件（`.mlmodel` 或 `.mlpackage`）
2. 在配置页面选择模型文件
3. 启用本地模型

## 💬 测试对话

配置完成后，回到聊天页面：

1. 在输入框输入消息
2. 点击发送按钮
3. 等待回复

如果收到回复，说明配置成功！页面会显示使用的 Provider 名称和响应时间。

## 🔌 其他 App 调用

DMP 服务启动后，其他 App 可以通过 HTTP API 调用：

```bash
# 健康检查
curl http://localhost:28473/health

# 聊天请求
curl -X POST http://localhost:28473/chat \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "你好"}]}'
```

## ❓ 常见问题

### Q: 为什么检测不到 Apple Intelligence？

A: 需要 iPhone 15 Pro 或更新机型，且系统版本为 iOS 18.0+。

### Q: 如何导入本地模型？

A: 需要将模型转换为 Core ML 格式（`.mlpackage`），然后通过配置页面导入。

### Q: 支持哪些云端 LLM？

A: 支持所有 OpenAI 兼容的 API，包括 OpenAI、Claude、通义千问等。