# DMP Demo 项目

用于测试 DMP 服务 HTTP API 接口的示例项目，支持 Android 和 iOS 平台。

## 功能特性

- 聊天界面，支持普通模式和流式模式
- 实时显示请求体和响应体（JSON 格式）
- 可配置 DMP 服务地址
- 健康检查和状态查询

## 项目结构

```
demo/
├── android/          # Android Demo 项目
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/dmp/demo/
│   │       │   ├── MainActivity.kt      # 主界面
│   │       │   ├── DMPClient.kt         # DMP API 客户端
│   │       │   └── MessageAdapter.kt    # 消息适配器
│   │       ├── res/
│   │       │   └── layout/
│   │       │       ├── activity_main.xml
│   │       │       └── item_message.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── ios/              # iOS Demo 项目
│   ├── DMPDemo/
│   │   ├── ContentView.swift           # 主界面
│   │   ├── DMPClient.swift             # DMP API 客户端
│   │   └── DMPDemoApp.swift            # 应用入口
│   └── DMPDemo.xcodeproj/
└── README.md
```

## 使用说明

### 前置条件

1. 确保 DMP 服务已启动（默认端口 28473）
2. 手机和运行 DMP 服务的设备在同一网络

### Android Demo

```bash
cd demo/android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### iOS Demo

1. 用 Xcode 打开 `demo/ios/DMPDemo.xcodeproj`
2. 选择目标设备，点击 Run