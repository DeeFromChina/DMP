# DMP (Device Model Protocol)

DMP is a local MCP service running on mobile devices, providing intelligent LLM routing functionality.

## Quick Start

### Install Dependencies

```bash
pip install -r requirements.txt
```

### Configuration

Copy `.env.example` to `.env` and configure your API keys:

```bash
cp .env.example .env
```

### Start Service

```bash
# HTTP service (default port 28473)
python main.py

# Specify port
python main.py --port 9090

# MCP service
python main.py --mcp
```

### Verify Service

```bash
curl http://localhost:28473/health
```

## Documentation

- [Public Documentation (English)](./docs/en/public/INDEX.md) - API documentation, configuration guide
- [Public Documentation (Chinese)](./docs/zh/public/INDEX.md) - API documentation, configuration guide
- [Internal Documentation (English)](./docs/en/internal/INDEX.md) - Architecture design, version management (internal only)
- [Internal Documentation (Chinese)](./docs/zh/internal/INDEX.md) - Architecture design, version management (internal only)

## Architecture

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

## Features

- Intelligent LLM routing: Automatically matches the optimal LLM interface based on device model
- Multi-provider support: Huawei, Xiaomi, OPPO, vivo, Apple, OpenAI, Claude, Qwen, etc.
- Automatic fallback: Tries providers in priority order, automatically switches on failure
- Protocol version management: Supports multi-version protocol coexistence, backward compatible