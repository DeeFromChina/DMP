# DMP (Device Model Protocol) 配置文档

## 概述

本文档详细说明DMP的所有配置项，包括环境变量、配置文件格式等。

---

## 1. 配置方式

### 1.1 环境变量

所有配置都可以通过环境变量设置：

```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY=sk-xxx
```

### 1.2 .env文件

在项目根目录创建`.env`文件：

```bash
# .env
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-xxx
```

### 1.3 代码中配置

```python
from dmp.config import Settings

settings = Settings(
    openai={
        "enabled": True,
        "api_key": "sk-xxx",
    }
)
```

**优先级**: 环境变量 > .env文件 > 代码默认值

---

## 2. 全局配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| SERVICE_NAME | str | dmp | 服务名称 |
| LOG_LEVEL | str | INFO | 日志级别 (DEBUG/INFO/WARNING/ERROR) |
| FALLBACK_ENABLED | bool | true | 是否启用回退机制 |
| MAX_RETRIES | int | 3 | 最大重试次数 |
| RETRY_DELAY | float | 1.0 | 重试延迟（秒） |

---

## 3. 手机厂商配置

### 3.1 华为 HMS Core AI

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| HUAWEI_ENABLED | bool | false | 否 | 是否启用 |
| HUAWEI_API_KEY | str | "" | 是 | API密钥 |
| HUAWEI_APP_ID | str | "" | 是 | 应用ID |
| HUAWEI_PROJECT_ID | str | "" | 是 | 项目ID |
| HUAWEI_ENDPOINT | str | https://ai.myhuaweicloud.com | 否 | API端点 |
| HUAWEI_MODEL | str | pangu-7b | 否 | 模型名称 |

**示例**:
```bash
HUAWEI_ENABLED=true
HUAWEI_API_KEY=your_api_key
HUAWEI_APP_ID=your_app_id
HUAWEI_PROJECT_ID=your_project_id
HUAWEI_MODEL=pangu-nlp-n1-32k
```

### 3.2 小米 MiMo

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| XIAOMI_ENABLED | bool | false | 否 | 是否启用 |
| XIAOMI_API_KEY | str | "" | 是 | API密钥 |
| XIAOMI_SECRET_KEY | str | "" | 是 | 密钥 |
| XIAOMI_ENDPOINT | str | https://ai.api.xiaomi.com | 否 | API端点 |
| XIAOMI_MODEL | str | xiaomi-lm | 否 | 模型名称 |

**示例**:
```bash
XIAOMI_ENABLED=true
XIAOMI_API_KEY=your_api_key
XIAOMI_SECRET_KEY=your_secret_key
XIAOMI_MODEL=mimo
```

### 3.3 Apple Core ML

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| APPLE_ENABLED | bool | false | 否 | 是否启用 |
| APPLE_MODEL_PATH | str | "" | 是 | 模型文件路径 |
| APPLE_MODEL_NAME | str | apple-lm | 否 | 模型名称 |

**示例**:
```bash
APPLE_ENABLED=true
APPLE_MODEL_PATH=/path/to/Llama3_8B.mlmodelc
APPLE_MODEL_NAME=llama-3.1-8b
```

### 3.4 OPPO AI

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| OPPO_ENABLED | bool | false | 否 | 是否启用 |
| OPPO_API_KEY | str | "" | 是 | API密钥 |
| OPPO_APP_ID | str | "" | 是 | 应用ID |
| OPPO_ENDPOINT | str | https://api.openai.heytap.com | 否 | API端点 |
| OPPO_MODEL | str | oppo-lm | 否 | 模型名称 |

**示例**:
```bash
OPPO_ENABLED=true
OPPO_API_KEY=your_api_key
OPPO_APP_ID=your_app_id
OPPO_MODEL=oppo-lm
```

### 3.5 vivo 蓝心大模型

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| VIVO_ENABLED | bool | false | 否 | 是否启用 |
| VIVO_API_KEY | str | "" | 是 | API密钥 |
| VIVO_APP_ID | str | "" | 是 | 应用ID |
| VIVO_ENDPOINT | str | https://ai.vivo.com.cn | 否 | API端点 |
| VIVO_MODEL | str | vivo-lm | 否 | 模型名称 |

**示例**:
```bash
VIVO_ENABLED=true
VIVO_API_KEY=your_api_key
VIVO_APP_ID=your_app_id
VIVO_MODEL=bluelm-7b
```

---

## 4. 云端LLM配置

### 4.1 OpenAI

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| OPENAI_ENABLED | bool | false | 否 | 是否启用 |
| OPENAI_API_KEY | str | "" | 是 | API密钥 |
| OPENAI_BASE_URL | str | https://api.openai.com/v1 | 否 | API端点 |
| OPENAI_MODEL | str | gpt-3.5-turbo | 否 | 模型名称 |
| OPENAI_TIMEOUT | int | 30 | 否 | 超时时间（秒） |

**示例**:
```bash
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-xxx
OPENAI_MODEL=gpt-4o
OPENAI_TIMEOUT=30
```

### 4.2 Anthropic Claude

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| CLAUDE_ENABLED | bool | false | 否 | 是否启用 |
| CLAUDE_API_KEY | str | "" | 是 | API密钥 |
| CLAUDE_BASE_URL | str | https://api.anthropic.com | 否 | API端点 |
| CLAUDE_MODEL | str | claude-3-haiku-20240307 | 否 | 模型名称 |
| CLAUDE_TIMEOUT | int | 30 | 否 | 超时时间（秒） |

**示例**:
```bash
CLAUDE_ENABLED=true
CLAUDE_API_KEY=sk-ant-xxx
CLAUDE_MODEL=claude-3-5-sonnet-20241022
CLAUDE_TIMEOUT=30
```

### 4.3 通义千问

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| TONGYI_ENABLED | bool | false | 否 | 是否启用 |
| TONGYI_API_KEY | str | "" | 是 | API密钥 |
| TONGYI_ENDPOINT | str | https://dashscope.aliyuncs.com | 否 | API端点 |
| TONGYI_MODEL | str | qwen-turbo | 否 | 模型名称 |
| TONGYI_TIMEOUT | int | 30 | 否 | 超时时间（秒） |

**示例**:
```bash
TONGYI_ENABLED=true
TONGYI_API_KEY=sk-xxx
TONGYI_MODEL=qwen-plus
TONGYI_TIMEOUT=30
```

---

## 5. 本地LLM配置

### 5.1 通用配置

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| LOCAL_LLM_ENABLED | bool | false | 否 | 是否启用 |
| LOCAL_LLM_ENGINE | str | mlc | 是 | 引擎类型 (mlc/llama_cpp/mediapipe) |
| LOCAL_LLM_MODEL_PATH | str | "" | 是 | 模型文件路径 |
| LOCAL_LLM_MAX_TOKENS | int | 2048 | 否 | 最大生成token数 |
| LOCAL_LLM_TEMPERATURE | float | 0.7 | 否 | 温度参数 |

### 5.2 MLC LLM

**示例**:
```bash
LOCAL_LLM_ENABLED=true
LOCAL_LLM_ENGINE=mlc
LOCAL_LLM_MODEL_PATH=/path/to/Llama-3.2-3B-Instruct-q4f16_1
LOCAL_LLM_MAX_TOKENS=2048
LOCAL_LLM_TEMPERATURE=0.7
```

### 5.3 llama-cpp-python

**示例**:
```bash
LOCAL_LLM_ENABLED=true
LOCAL_LLM_ENGINE=llama_cpp
LOCAL_LLM_MODEL_PATH=/path/to/llama-3.2-3b.Q4_K_M.gguf
LOCAL_LLM_MAX_TOKENS=2048
LOCAL_LLM_TEMPERATURE=0.7
```

### 5.4 MediaPipe

**示例**:
```bash
LOCAL_LLM_ENABLED=true
LOCAL_LLM_ENGINE=mediapipe
LOCAL_LLM_MODEL_PATH=/path/to/gemma-2b.tflite
LOCAL_LLM_MAX_TOKENS=2048
LOCAL_LLM_TEMPERATURE=0.7
```

---

## 6. 完整配置示例

### 6.1 开发环境 (.env.development)

```bash
# 服务配置
SERVICE_NAME=dmp-dev
LOG_LEVEL=DEBUG

# 手机厂商 (开发时禁用)
HUAWEI_ENABLED=false
XIAOMI_ENABLED=false
APPLE_ENABLED=false
OPPO_ENABLED=false
VIVO_ENABLED=false

# 云端LLM (使用测试密钥)
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-test-xxx
OPENAI_MODEL=gpt-3.5-turbo

CLAUDE_ENABLED=false
TONGYI_ENABLED=false

# 本地LLM
LOCAL_LLM_ENABLED=false

# 回退策略
FALLBACK_ENABLED=true
MAX_RETRIES=1
RETRY_DELAY=0.5
```

### 6.2 生产环境 (.env.production)

```bash
# 服务配置
SERVICE_NAME=dmp
LOG_LEVEL=INFO

# 手机厂商 (启用)
HUAWEI_ENABLED=true
HUAWEI_API_KEY=your_huawei_key
HUAWEI_APP_ID=your_huawei_app_id
HUAWEI_PROJECT_ID=your_huawei_project_id
HUAWEI_MODEL=pangu-nlp-n1-32k

XIAOMI_ENABLED=true
XIAOMI_API_KEY=your_xiaomi_key
XIAOMI_SECRET_KEY=your_xiaomi_secret
XIAOMI_MODEL=mimo

OPPO_ENABLED=true
OPPO_API_KEY=your_oppo_key
OPPO_APP_ID=your_oppo_app_id

VIVO_ENABLED=true
VIVO_API_KEY=your_vivo_key
VIVO_APP_ID=your_vivo_app_id

# 云端LLM (回退)
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-prod-xxx
OPENAI_MODEL=gpt-4o

CLAUDE_ENABLED=true
CLAUDE_API_KEY=sk-ant-xxx
CLAUDE_MODEL=claude-3-5-sonnet-20241022

TONGYI_ENABLED=true
TONGYI_API_KEY=sk-xxx
TONGYI_MODEL=qwen-plus

# 本地LLM (最后回退)
LOCAL_LLM_ENABLED=true
LOCAL_LLM_ENGINE=mlc
LOCAL_LLM_MODEL_PATH=/data/models/Llama-3.2-3B-Instruct-q4f16_1

# 回退策略
FALLBACK_ENABLED=true
MAX_RETRIES=3
RETRY_DELAY=1.0
```

### 6.3 仅手机厂商 (.env.mobile-only)

```bash
# 仅启用手机厂商
HUAWEI_ENABLED=true
HUAWEI_API_KEY=your_key

XIAOMI_ENABLED=true
XIAOMI_API_KEY=your_key
XIAOMI_SECRET_KEY=your_secret

OPPO_ENABLED=true
OPPO_API_KEY=your_key

VIVO_ENABLED=true
VIVO_API_KEY=your_key

# 禁用云端和本地
OPENAI_ENABLED=false
CLAUDE_ENABLED=false
TONGYI_ENABLED=false
LOCAL_LLM_ENABLED=false
```

### 6.4 仅云端LLM (.env.cloud-only)

```bash
# 禁用手机厂商
HUAWEI_ENABLED=false
XIAOMI_ENABLED=false
APPLE_ENABLED=false
OPPO_ENABLED=false
VIVO_ENABLED=false

# 启用云端
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-xxx

CLAUDE_ENABLED=true
CLAUDE_API_KEY=sk-ant-xxx

TONGYI_ENABLED=true
TONGYI_API_KEY=sk-xxx

# 禁用本地
LOCAL_LLM_ENABLED=false
```

### 6.5 仅本地LLM (.env.local-only)

```bash
# 禁用所有远程
HUAWEI_ENABLED=false
XIAOMI_ENABLED=false
APPLE_ENABLED=false
OPPO_ENABLED=false
VIVO_ENABLED=false
OPENAI_ENABLED=false
CLAUDE_ENABLED=false
TONGYI_ENABLED=false

# 仅启用本地
LOCAL_LLM_ENABLED=true
LOCAL_LLM_ENGINE=llama_cpp
LOCAL_LLM_MODEL_PATH=/path/to/model.gguf
```

---

## 7. 配置验证

### 7.1 自动验证

配置加载时会自动验证：

```python
from dmp.config import Settings

try:
    settings = Settings()
    print("配置加载成功")
except Exception as e:
    print(f"配置加载失败: {e}")
```

### 7.2 手动验证

```python
from dmp.config import Settings

settings = Settings()

# 检查必需字段
if settings.openai.enabled and not settings.openai.api_key:
    print("警告: OpenAI已启用但未设置API密钥")
```

---

## 8. 配置最佳实践

### 8.1 安全建议

1. **不要提交.env文件到Git**
   ```bash
   # .gitignore
   .env
   .env.*
   !.env.example
   ```

2. **使用环境变量管理密钥**
   ```bash
   # 在CI/CD中
   export OPENAI_API_KEY=${SECRET_OPENAI_API_KEY}
   ```

3. **定期轮换API密钥**
   - 每3个月更换一次
   - 使用密钥管理服务

### 8.2 性能建议

1. **根据使用场景选择模型**
   - 简单任务: 小模型 (1-3B)
   - 通用任务: 中模型 (7B)
   - 复杂任务: 大模型 (70B+)

2. **调整超时时间**
   - 本地模型: 10秒
   - 云端模型: 30秒
   - 大模型: 60秒

3. **合理设置重试次数**
   - 开发环境: 1次
   - 生产环境: 3次
   - 关键任务: 5次

### 8.3 开发建议

1. **使用不同的.env文件**
   ```
   .env.development    # 开发环境
   .env.staging        # 测试环境
   .env.production     # 生产环境
   ```

2. **记录配置变更**
   - 使用版本控制管理.env.example
   - 记录配置变更原因

3. **测试配置**
   - 启动时验证配置
   - 提供配置检查工具

---

## 9. 故障排查

### Q1: 配置未生效？

A: 检查：
1. .env文件是否在正确位置
2. 环境变量是否覆盖
3. 配置名称是否正确

### Q2: API密钥错误？

A: 检查：
1. 密钥是否正确
2. 是否有空格或换行
3. 是否已过期

### Q3: 模型未找到？

A: 检查：
1. 模型名称是否正确
2. 模型是否可用
3. 是否有访问权限

### Q4: 超时错误？

A: 检查：
1. 网络是否正常
2. 超时时间是否太短
3. 模型是否过大