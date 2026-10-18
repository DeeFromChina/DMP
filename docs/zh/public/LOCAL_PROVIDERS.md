# 本地LLM部署文档

## 概述

本文档详细介绍可在手机/本地设备上部署的轻量级LLM引擎，包括MLC LLM、llama-cpp-python、MediaPipe等。

---

## 1. MLC LLM

### 1.1 基本信息

| 项目 | 说明 |
|------|------|
| **项目名称** | MLC LLM (Machine Learning Compilation LLM) |
| **项目地址** | https://github.com/mlc-ai/mlc-llm |
| **文档地址** | https://llm.mlc.ai/docs/ |
| **支持平台** | Android、iOS、Web、macOS、Linux、Windows |
| **后端支持** | Vulkan、OpenCL、Metal、CUDA、WebGPU |
| **支持模型** | Llama 3.2、Gemma 2、Phi 3.5、Qwen 2.5等 |

### 1.2 支持的模型列表

| 模型名称 | 参数量 | 量化后大小 | Android支持 | iOS支持 | 推理速度 |
|----------|--------|------------|-------------|---------|----------|
| Llama 3.2 1B | 1B | 0.7GB | ✅ | ✅ | 80 tok/s |
| Llama 3.2 3B | 3B | 2.0GB | ✅ | ✅ | 40 tok/s |
| Llama 3.1 8B | 8B | 4.5GB | ✅ (高端) | ✅ (M系列) | 20 tok/s |
| Gemma 2 2B | 2B | 1.5GB | ✅ | ✅ | 50 tok/s |
| Gemma 2 9B | 9B | 5.5GB | ✅ (高端) | ✅ (M系列) | 15 tok/s |
| Phi 3.5 Mini | 3.8B | 2.3GB | ✅ | ✅ | 35 tok/s |
| Qwen 2.5 1.5B | 1.5B | 1.0GB | ✅ | ✅ | 70 tok/s |
| Qwen 2.5 7B | 7B | 4.2GB | ✅ (高端) | ✅ (M系列) | 22 tok/s |
| Mistral 7B | 7B | 4.2GB | ✅ (高端) | ✅ (M系列) | 20 tok/s |

### 1.3 设备要求

#### Android设备要求

| 要求 | 最低配置 | 推荐配置 |
|------|----------|----------|
| 芯片 | Snapdragon 8 Gen 1 | Snapdragon 8 Gen 3 |
| 内存 | 6GB | 12GB+ |
| 存储 | 5GB可用空间 | 10GB+可用空间 |
| Android版本 | 10+ | 14+ |
| GPU支持 | Adreno 730+ | Adreno 750+ |

#### iOS设备要求

| 要求 | 最低配置 | 推荐配置 |
|------|----------|----------|
| 芯片 | A15 Bionic | A17 Pro / M系列 |
| 内存 | 4GB | 8GB+ |
| 存储 | 5GB可用空间 | 10GB+可用空间 |
| iOS版本 | 16+ | 17+ |

### 1.4 部署步骤

#### 1.4.1 Android部署

**步骤1: 安装MLC LLM**
```bash
# 克隆仓库
git clone https://github.com/mlc-ai/mlc-llm.git
cd mlc-llm

# 安装Python依赖
pip install mlc-llm
```

**步骤2: 下载预编译模型**
```bash
# 使用MLC CLI下载模型
mlc_llm download Llama-3.2-3B-Instruct-q4f16_1
```

**步骤3: 编译模型 (可选)**
```bash
# 编译模型为Android格式
mlc_llm compile Llama-3.2-3B-Instruct \
  --target android \
  --quantization q4f16_1 \
  --output llama3_2_3b_android.tar
```

**步骤4: 构建Android APK**
```bash
# 进入Android目录
cd android/MLCChat

# 使用Android Studio打开项目
# 或使用命令行构建
./gradlew assembleRelease
```

**步骤5: 安装和运行**
```bash
# 安装APK到设备
adb install app-release.apk

# 运行应用
adb shell am start -n ai.mlc.mlcchat/.MainActivity
```

#### 1.4.2 iOS部署

**步骤1: 安装依赖**
```bash
# 安装coremltools
pip install coremltools

# 安装mlc-llm
pip install mlc-llm
```

**步骤2: 转换模型为Core ML格式**
```bash
# 转换模型
mlc_llm convert Llama-3.2-3B-Instruct \
  --target ios \
  --quantization q4f16_1
```

**步骤3: 构建iOS应用**
```bash
# 进入iOS目录
cd ios/MLCChat

# 使用Xcode打开项目
open MLCChat.xcodeproj

# 在Xcode中构建和运行
```

#### 1.4.3 命令行使用

```bash
# 运行MLC LLM CLI
mlc_llm chat Llama-3.2-3B-Instruct-q4f16_1

# 交互式对话
>>> 你好
>>> 你好！有什么可以帮助你的？
```

### 1.5 Python API使用

```python
from mlc_llm import MLCEngine

# 创建引擎
engine = MLCEngine(model="Llama-3.2-3B-Instruct-q4f16_1")

# 非流式生成
response = engine.generate("你好")
print(response)

# 流式生成
for chunk in engine.generate("你好", stream=True):
    print(chunk.text, end="", flush=True)

# 聊天
messages = [
    {"role": "system", "content": "你是一个助手"},
    {"role": "user", "content": "你好"}
]
response = engine.chat(messages)
print(response)
```

### 1.6 性能优化

#### 量化选项

| 量化方法 | 精度 | 大小减少 | 性能损失 | 推荐场景 |
|----------|------|----------|----------|----------|
| q4f16_1 | 4-bit | 75% | 5-10% | 通用场景 |
| q4f16_2 | 4-bit | 75% | 3-5% | 高质量要求 |
| q8f16_1 | 8-bit | 50% | 1-2% | 最高质量 |
| q0f16 | 16-bit | 0% | 0% | 服务器 |

#### GPU优化

```python
# 指定GPU后端
engine = MLCEngine(
    model="Llama-3.2-3B-Instruct-q4f16_1",
    device="vulkan",  # Android
    # device="metal",  # iOS/macOS
    # device="cuda",   # NVIDIA GPU
)
```

### 1.7 限制和注意事项

| 限制项 | 说明 |
|--------|------|
| 内存占用 | 模型大小 + 2-3GB运行时开销 |
| 电池消耗 | 高，长时间使用会显著耗电 |
| 发热 | 推理时设备会发热 |
| 首次加载 | 模型加载需要5-10秒 |
| 多任务 | 可能影响其他应用性能 |

---

## 2. llama-cpp-python

### 2.1 基本信息

| 项目 | 说明 |
|------|------|
| **项目名称** | llama-cpp-python |
| **项目地址** | https://github.com/abetlen/llama-cpp-python |
| **文档地址** | https://llama-cpp-python.readthedocs.io/ |
| **支持平台** | Android (Termux)、iOS (有限)、macOS、Linux、Windows |
| **后端支持** | CPU、GPU (cuBLAS、Metal、Vulkan) |
| **支持模型** | 所有GGUF格式模型 |

### 2.2 支持的模型列表

| 模型名称 | 参数量 | GGUF大小 (Q4) | Android支持 | iOS支持 | 推理速度 |
|----------|--------|---------------|-------------|---------|----------|
| Llama 3.2 1B | 1B | 0.8GB | ✅ | ✅ | 60 tok/s |
| Llama 3.2 3B | 3B | 2.0GB | ✅ | ✅ | 30 tok/s |
| Llama 3.1 8B | 8B | 5.0GB | ✅ (高端) | ❌ | 15 tok/s |
| Mistral 7B | 7B | 4.2GB | ✅ (高端) | ❌ | 18 tok/s |
| Phi 3 Mini | 3.8B | 2.3GB | ✅ | ✅ | 25 tok/s |
| Gemma 2B | 2B | 1.5GB | ✅ | ✅ | 45 tok/s |
| Qwen 2.5 7B | 7B | 4.5GB | ✅ (高端) | ❌ | 16 tok/s |
| DeepSeek-Coder 7B | 7B | 4.2GB | ✅ (高端) | ❌ | 17 tok/s |

### 2.3 设备要求

#### Android设备要求 (Termux)

| 要求 | 最低配置 | 推荐配置 |
|------|----------|----------|
| 芯片 | Snapdragon 865 | Snapdragon 8 Gen 2+ |
| 内存 | 6GB | 12GB+ |
| 存储 | 5GB可用空间 | 10GB+可用空间 |
| Android版本 | 10+ | 14+ |
| Root | 不需要 | 不需要 |

#### iOS设备要求

| 要求 | 最低配置 | 推荐配置 |
|------|----------|----------|
| 芯片 | A15 Bionic | A17 Pro |
| 内存 | 4GB | 6GB+ |
| 存储 | 5GB可用空间 | 10GB+可用空间 |
| iOS版本 | 16+ | 17+ |

**注意**: iOS支持有限，需要通过特殊方式编译。

### 2.4 部署步骤

#### 2.4.1 Android部署 (Termux)

**步骤1: 安装Termux**
```bash
# 从F-Droid下载Termux (不要用Play Store版本)
# https://f-droid.org/packages/com.termux/
```

**步骤2: 安装依赖**
```bash
# 更新包列表
pkg update && pkg upgrade

# 安装构建工具
pkg install python cmake clang make git
```

**步骤3: 安装llama-cpp-python**
```bash
# 安装 (CPU版本)
pip install llama-cpp-python

# 或从源码编译 (支持更多优化)
git clone https://github.com/abetlen/llama-cpp-python.git
cd llama-cpp-python
pip install .
```

**步骤4: 下载模型**
```bash
# 从Hugging Face下载GGUF模型
pip install huggingface-hub

python -c "
from huggingface_hub import hf_hub_download
model = hf_hub_download(
    'TheBloke/Llama-3.2-3B-Instruct-GGUF',
    'llama-3.2-3b-instruct.Q4_K_M.gguf'
)
print(f'Model downloaded to: {model}')
"
```

**步骤5: 运行**
```python
from llama_cpp import Llama

# 加载模型
llm = Llama(
    model_path="/path/to/model.gguf",
    n_ctx=2048,
    n_threads=8,
    n_gpu_layers=0  # 0=CPU, >0=GPU
)

# 生成文本
output = llm(
    "Q: 你好\nA:",
    max_tokens=100,
    temperature=0.7,
    echo=False
)

print(output["choices"][0]["text"])
```

#### 2.4.2 macOS部署

**步骤1: 安装依赖**
```bash
# 使用Homebrew
brew install cmake

# 安装llama-cpp-python (支持Metal GPU)
CMAKE_ARGS="-DGGML_METAL=on" pip install llama-cpp-python
```

**步骤2: 下载和使用模型**
```python
from llama_cpp import Llama

# 加载模型 (自动使用Metal GPU)
llm = Llama(
    model_path="/path/to/model.gguf",
    n_ctx=4096,
    n_gpu_layers=-1  # -1=所有层都用GPU
)

# 生成文本
output = llm("你好", max_tokens=100)
print(output["choices"][0]["text"])
```

#### 2.4.3 Server模式

```bash
# 启动OpenAI兼容服务器
python -m llama_cpp.server \
  --model /path/to/model.gguf \
  --host 0.0.0.0 \
  --port 8000 \
  --n_ctx 4096

# 使用curl测试
curl http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "你好"}],
    "max_tokens": 100
  }'
```

### 2.5 Python API使用

```python
from llama_cpp import Llama

# 基础使用
llm = Llama(model_path="model.gguf")

# 文本生成
output = llm(
    "Once upon a time,",
    max_tokens=100,
    temperature=0.7,
    top_p=0.9,
    repeat_penalty=1.1
)

# 聊天
messages = [
    {"role": "system", "content": "你是一个助手"},
    {"role": "user", "content": "你好"}
]
output = llm.create_chat_completion(messages=messages)

# 流式生成
for chunk in llm.create_chat_completion(
    messages=messages,
    stream=True
):
    print(chunk["choices"][0]["delta"].get("content", ""), end="")
```

### 2.6 性能优化

#### 量化选项

| 量化方法 | 质量 | 大小 | 速度 | 推荐场景 |
|----------|------|------|------|----------|
| Q2_K | 最低 | 最小 | 最快 | 资源受限 |
| Q3_K_M | 低 | 小 | 快 | 低端设备 |
| Q4_K_M | 中 | 中 | 中 | 通用场景 |
| Q5_K_M | 高 | 大 | 慢 | 高质量要求 |
| Q6_K | 很高 | 很大 | 很慢 | 服务器 |
| Q8_0 | 最高 | 最大 | 最慢 | 无损要求 |

#### 线程优化

```python
# CPU线程数
llm = Llama(
    model_path="model.gguf",
    n_threads=8,        # 物理核心数
    n_threads_batch=8,  # 批处理线程数
)

# GPU层数 (如果有GPU)
llm = Llama(
    model_path="model.gguf",
    n_gpu_layers=35,    # 卸载到GPU的层数
)
```

### 2.7 限制和注意事项

| 限制项 | 说明 |
|--------|------|
| 内存占用 | 模型大小 + 1-2GB运行时开销 |
| iOS支持 | 有限，需要特殊编译 |
| GPU支持 | Android需要Vulkan，iOS不支持 |
| 首次加载 | 模型加载需要3-8秒 |
| 上下文长度 | 受内存限制，通常2K-4K |

---

## 3. MediaPipe LLM Inference

### 3.1 基本信息

| 项目 | 说明 |
|------|------|
| **项目名称** | MediaPipe LLM Inference |
| **项目地址** | https://ai.google.dev/edge/mediapipe |
| **文档地址** | https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference |
| **支持平台** | Android、Web |
| **后端支持** | CPU、GPU (OpenGL、Vulkan) |
| **支持模型** | Gemma 2B、Phi 3等 (TFLite格式) |

### 3.2 支持的模型列表

| 模型名称 | 参数量 | TFLite大小 | Android支持 | Web支持 | 推理速度 |
|----------|--------|------------|-------------|---------|----------|
| Gemma 2B | 2B | 1.6GB | ✅ | ✅ | 40 tok/s |
| Gemma 7B | 7B | 4.5GB | ✅ (高端) | ✅ | 15 tok/s |
| Phi 3 Mini | 3.8B | 2.4GB | ✅ | ✅ | 30 tok/s |

### 3.3 设备要求

#### Android设备要求

| 要求 | 最低配置 | 推荐配置 |
|------|----------|----------|
| 芯片 | Snapdragon 8 Gen 1 | Snapdragon 8 Gen 3 |
| 内存 | 6GB | 12GB+ |
| 存储 | 5GB可用空间 | 10GB+可用空间 |
| Android版本 | 12+ | 14+ |

### 3.4 部署步骤

#### 3.4.1 Android部署

**步骤1: 添加依赖**
```gradle
// build.gradle
dependencies {
    implementation 'com.google.mediapipe:tasks-genai:0.10.14'
}
```

**步骤2: 下载模型**
```bash
# 从Google下载Gemma TFLite模型
# https://ai.google.dev/gemma/docs
```

**步骤3: 初始化LLM Inference**
```java
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

LlmInference llmInference = new LlmInference.Builder(context)
    .setModelPath("/path/to/gemma.tflite")
    .setMaxTokens(2048)
    .setTopK(40)
    .setTopP(0.95f)
    .setTemperature(0.7f)
    .build();
```

**步骤4: 生成文本**
```java
// 非流式生成
String response = llmInference.generateResponse("你好");

// 流式生成
llmInference.generateResponseAsync("你好", result -> {
    if (result.isOk()) {
        String response = result.getResultOrThrow();
        // 处理响应
    }
});
```

#### 3.4.2 Web部署

```javascript
import { LlmInference } from '@mediapipe/tasks-genai';

// 初始化
const llm = await LlmInference.create({
  model: '/path/to/gemma.tflite',
  maxTokens: 2048,
  temperature: 0.7,
});

// 生成文本
const response = await llm.generateResponse('你好');
console.log(response);

// 流式生成
await llm.generateResponse('你好', (partialResult) => {
  console.log(partialResult);
});
```

### 3.5 Python API使用

```python
import mediapipe as mp

# 初始化
BaseOptions = mp.tasks.BaseOptions
LlmInference = mp.tasks.genai.LlmInference
LlmInferenceOptions = mp.tasks.genai.LlmInferenceOptions

options = LlmInferenceOptions(
    model_path="/path/to/gemma.tflite",
    max_tokens=2048,
    temperature=0.7,
)

with LlmInference(options) as llm:
    # 生成文本
    response = llm.generate_response("你好")
    print(response)
```

### 3.6 限制和注意事项

| 限制项 | 说明 |
|--------|------|
| 模型选择 | 仅支持Gemma和少数模型 |
| 内存占用 | 模型大小 + 1-2GB运行时开销 |
| 平台支持 | 仅Android和Web |
| 首次加载 | 模型加载需要5-10秒 |

---

## 4. 其他本地LLM方案

### 4.1 Ollama

| 项目 | 说明 |
|------|------|
| **项目地址** | https://ollama.com |
| **支持平台** | macOS、Linux、Windows |
| **手机支持** | 通过Termux (Android) |
| **特点** | 简单易用，自动下载模型 |

**Android部署**:
```bash
# 在Termux中
pkg install curl
curl -fsSL https://ollama.com/install.sh | sh

# 运行
ollama run llama3.2:3b
```

### 4.2 MLC Chat (APK)

| 项目 | 说明 |
|------|------|
| **项目地址** | https://github.com/mlc-ai/mlc-llm |
| **支持平台** | Android、iOS |
| **特点** | 预编译APK，开箱即用 |

**使用**:
1. 下载APK: https://github.com/mlc-ai/mlc-llm/releases
2. 安装到手机
3. 选择模型下载
4. 开始对话

### 4.3 Layla (原PrivateLLM)

| 项目 | 说明 |
|------|------|
| **项目地址** | https://layla.app |
| **支持平台** | Android、iOS |
| **特点** | 商业应用，界面友好 |

---

## 5. 对比总结

### 5.1 功能对比

| 特性 | MLC LLM | llama-cpp | MediaPipe |
|------|---------|-----------|-----------|
| 平台支持 | 全平台 | 全平台 | Android/Web |
| 模型选择 | 多 | 最多 (GGUF) | 少 |
| GPU加速 | ✅ | ✅ (部分) | ✅ |
| 流式输出 | ✅ | ✅ | ✅ |
| OpenAI兼容 | ✅ | ✅ (Server) | ❌ |
| 易用性 | 中 | 高 | 高 |

### 5.2 性能对比

| 指标 | MLC LLM | llama-cpp | MediaPipe |
|------|---------|-----------|-----------|
| 推理速度 | 快 | 中 | 中 |
| 内存占用 | 低 | 中 | 中 |
| 模型质量 | 高 | 最高 | 中 |
| 电池消耗 | 中 | 中 | 低 |

### 5.3 推荐方案

| 场景 | 推荐方案 | 原因 |
|------|----------|------|
| Android通用 | MLC LLM | 性能好，支持多模型 |
| Android简单 | MediaPipe | 易用，Google支持 |
| iOS | MLC LLM | 唯一完整支持 |
| 开发测试 | llama-cpp | 灵活，社区大 |
| 生产部署 | MLC LLM | 性能最佳 |

---

## 6. 常见问题

### Q1: 手机能跑多大的模型？

A: 
- 6GB内存: 1-2B模型
- 8GB内存: 3-4B模型
- 12GB+内存: 7-8B模型

### Q2: 推理速度慢怎么办？

A:
- 使用更小的模型
- 使用量化模型 (Q4)
- 启用GPU加速
- 减少线程数

### Q3: 内存不足怎么办？

A:
- 使用更小的量化 (Q2/Q3)
- 关闭其他应用
- 使用更小的模型

### Q4: 电池消耗太快怎么办？

A:
- 减少使用频率
- 使用更小的模型
- 降低推理速度要求
- 连接充电器使用

### Q5: 如何选择合适的量化？

A:
- Q4_K_M: 通用场景 (推荐)
- Q5_K_M: 高质量要求
- Q2/Q3: 资源受限