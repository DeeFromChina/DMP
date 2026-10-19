# DMP Android - 安装和调试指南

## 一、通过开发者模式安装（红魔10 Pro）

### 1. 手机准备

#### 开启开发者模式
1. 打开 **设置** → **关于手机**
2. 连续点击 **版本号** 7次，直到提示"您已处于开发者模式"
3. 返回 **设置** → **系统** → **开发者选项**
4. 开启以下选项：
   - ✅ **USB调试**
   - ✅ **USB安装**（如果有）
   - ✅ **USB调试（安全设置）**（红魔手机特有）

#### 连接电脑
1. 使用USB数据线连接手机和电脑
2. 手机上弹出"允许USB调试"对话框时，点击 **允许**
3. 验证连接：
   ```bash
   adb devices
   ```
   应该看到您的设备列表，状态为 `device`

### 2. 构建APK

#### 方式一：使用Gradle命令行（推荐）

在项目根目录执行：

```bash
cd /path/to/DMP/android

# 构建Debug版本
./gradlew assembleDebug

# 或者构建Release版本
./gradlew assembleRelease
```

构建成功后，APK位置：
- **Debug版本**：`app/build/outputs/apk/debug/app-debug.apk`
- **Release版本**：`app/build/outputs/apk/release/app-release-unsigned.apk`

#### 方式二：使用Android Studio

1. 打开Android Studio
2. 导入项目：`File` → `Open` → 选择 `android` 文件夹
3. 等待Gradle同步完成
4. 点击菜单：`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
5. 构建成功后，点击通知中的 `locate` 打开APK所在目录

### 3. 安装APK

```bash
# 安装Debug版本
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 如果提示INSTALL_FAILED_TEST_ONLY，添加-t参数
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# 安装到指定设备（如果连接了多个设备）
adb -s <设备序列号> install -r app/build/outputs/apk/debug/app-debug.apk
```

**红魔手机特殊处理**：
如果安装失败，可能需要在手机上手动允许安装：
- 设置 → 应用设置 → 安装识别 → 允许USB安装

### 4. 启动应用

```bash
# 启动应用
adb shell am start -n com.dmp.android/.ui.ChatActivity

# 或者从手机桌面直接点击DMP图标
```

---

## 二、获取运行日志

### 方式1：HTTP日志服务器（推荐，仅Debug模式）

**特点**：
- ✅ 手机生成二维码，电脑扫码即可查看
- ✅ 实时Web界面，自动刷新
- ✅ 支持过滤：全部/错误/警告/信息
- ✅ 仅限同WiFi局域网访问
- ⚠️ **Release模式自动禁用**（安全保护）

#### 使用步骤

1. **安装Debug版本到手机**
   ```bash
   cd /path/to/DMP/android
   ./gradlew assembleDebug
   adb install -r -t app/build/outputs/apk/debug/app-debug.apk
   ```

2. **打开日志查看器**
   - 在DMP应用中点击"日志查看"按钮
   - 或在聊天界面菜单中选择"日志查看器"

3. **电脑扫码**
   - 确保电脑和手机在同一WiFi网络
   - 使用浏览器扫描屏幕上的二维码
   - 自动打开日志查看页面：`http://192.168.x.x:28473`

4. **实时查看日志**
   - Web界面每2秒自动刷新
   - 支持按级别过滤（全部/错误/警告/信息）
   - 支持自动滚动
   - 显示统计信息（总日志数、错误数、警告数）

#### 安全保护

**Debug模式**：
- ✅ HTTP日志服务器可用
- ✅ 二维码生成可用
- ✅ 同WiFi设备可访问

**Release模式**：
- ❌ HTTP日志服务器自动禁用
- ❌ 二维码功能隐藏
- ❌ 即使安装Release版本也无法查看日志
- ✅ 保护用户隐私和安全

**代码实现**：
```kotlin
// DMPLogServer.kt
fun startServer(): String {
    if (BuildConfig.DEBUG.not()) {
        logger.warn("Release模式，禁用日志HTTP服务器")
        return ""  // 直接返回空，不启动服务器
    }
    // ... 启动服务器
}

// LogViewerActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    if (!BuildConfig.DEBUG) {
        Toast.makeText(this, "Release模式，日志查看功能已禁用", Toast.LENGTH_LONG).show()
        finish()  // 直接关闭Activity
        return
    }
    // ... 显示日志
}
```

### 方式2：adb logcat（开发者模式）

#### 使用adb logcat（推荐）

```bash
# 查看所有日志
adb logcat

# 只看DMP相关日志（过滤标签）
adb logcat -s DMPLogger:D DMP:*

# 查看包含"DMP"关键字的日志
adb logcat | grep -i "dmp"

# 查看错误级别日志
adb logcat *:E

# 查看最近1000行日志
adb logcat -t 1000

# 保存日志到文件
adb logcat -d > dmp_logs_$(date +%Y%m%d_%H%M%S).txt
```

#### 日志输出示例

```
10-15 14:23:45.123 12345 12345 I DMPLogger: 应用启动
10-15 14:23:45.234 12345 12345 I DMPLogger: DMPVersionManager initialized
10-15 14:23:45.345 12345 12345 I DMPLogger: 设备: nubia NX711J, 系统: Android 14 (API 34), 推荐提供者: [cloud]
10-15 14:23:45.456 12345 12345 I DMPLogger: 注册LLM提供者: cloud (优先级: 3)
10-15 14:23:46.567 12345 12345 I DMPLogger: 开始处理聊天请求 (TraceID: abc123...)
10-15 14:23:46.678 12345 12345 D DMPLogger: 尝试提供者: cloud (TraceID: abc123...)
10-15 14:23:47.789 12345 12345 I DMPLogger: 成功使用提供者: cloud, 耗时: 1234ms (TraceID: abc123...)
```

### 2. 日志文件导出

DMP应用内置了日志文件输出功能，日志保存在：

```
手机内部存储/Android/data/com.dmp.android/files/logs/
```

#### 导出日志文件

```bash
# 导出所有日志文件
adb pull /sdcard/Android/data/com.dmp.android/files/logs/ ./dmp_logs/

# 导出特定日志文件
adb pull /sdcard/Android/data/com.dmp.android/files/logs/dmp_2024-10-15.log ./

# 实时查看日志文件内容
adb shell "tail -f /sdcard/Android/data/com.dmp.android/files/logs/dmp_$(date +%Y-%m-%d).log"
```

### 3. 应用内日志查看

在应用内，您可以通过以下方式查看日志：

1. 打开DMP应用
2. 进入 **设置** → **日志查看**（如果已实现）
3. 或者在聊天界面长按消息查看调试信息

### 4. 高级日志命令

```bash
# 清空日志缓冲区
adb logcat -c

# 查看崩溃日志
adb logcat | grep -i "FATAL\|CRASH\|Exception"

# 查看网络请求日志
adb logcat | grep -i "OkHttp\|network\|http"

# 查看设备检测日志
adb logcat | grep -i "DeviceDetector\|设备"

# 查看LLM提供者日志
adb logcat | grep -i "LLMRouter\|Provider\|提供者"

# 查看错误追踪日志
adb logcat | grep -i "DMPError\|ErrorTracker\|错误"

# 持续监控并保存
adb logcat | tee dmp_live.log
```

---

## 三、常见问题

### Q1: 安装失败 "INSTALL_FAILED_TEST_ONLY"

**解决方案**：
```bash
# 添加-t参数
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

### Q2: 安装失败 "INSTALL_FAILED_USER_RESTRICTED"

**解决方案**（红魔手机）：
1. 设置 → 应用设置 → 安装识别 → 允许USB安装
2. 或者：设置 → 开发者选项 → USB安装 → 允许

### Q3: 设备未识别 "unauthorized"

**解决方案**：
1. 断开USB连接
2. 在手机上撤销USB调试授权：设置 → 开发者选项 → 撤销USB调试授权
3. 重新连接USB
4. 在手机上点击"允许USB调试"

### Q4: 日志为空或很少

**解决方案**：
1. 确保应用已启动：`adb shell ps | grep dmp`
2. 确保日志级别设置为DEBUG：在应用中设置或代码中设置
3. 使用更宽的过滤条件：`adb logcat | grep -i "dmp\|llm\|ai"`

### Q5: 红魔手机特殊权限

红魔手机（努比亚）可能需要额外权限：
1. 设置 → 应用管理 → DMP → 权限 → 允许所有必要权限
2. 设置 → 电池 → DMP → 允许后台运行
3. 设置 → 开发者选项 → 关闭MIUI优化（如果有）

---

## 四、快速命令参考

```bash
# 一键构建、安装、启动
cd /path/to/DMP/android
./gradlew assembleDebug && \
adb install -r -t app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.dmp.android/.ui.ChatActivity

# 一键查看日志
adb logcat -c && adb logcat | grep -i "dmp\|llm"

# 一键导出日志
adb pull /sdcard/Android/data/com.dmp.android/files/logs/ ./logs/

# 卸载应用
adb uninstall com.dmp.android

# 清除应用数据
adb shell pm clear com.dmp.android
```

---

## 五、红魔10 Pro特定信息

### 设备信息
- **品牌**：nubia / 红魔
- **型号**：NX711J（红魔10 Pro）
- **系统**：Android 14（API 34）
- **处理器**：骁龙8 Gen 3

### DMP识别
根据设备检测器，红魔10 Pro会被识别为：
- **品牌**：Other（其他品牌）
- **系统版本**：Android 14 (API 34)
- **推荐提供者**：`["tflite", "cloud"]`

因为红魔目前没有专门的AI SDK集成，所以会优先使用TFLite本地模型，回退到云端API。

### 性能优化建议
红魔10 Pro性能强劲，建议：
1. 优先使用TFLite本地模型（如果有的话）
2. 配置云端API作为备用
3. 可以利用骁龙8 Gen 3的GPU加速TFLite推理