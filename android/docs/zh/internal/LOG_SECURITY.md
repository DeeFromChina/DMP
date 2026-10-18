# DMP Android - 日志安全策略

## 安全模型

### 核心原则

**Debug模式**（开发测试用）：
- ✅ 允许HTTP日志服务器
- ✅ 允许二维码生成
- ✅ 允许同WiFi局域网访问
- ✅ 允许adb logcat查看

**Release模式**（发布给用户）：
- ❌ 禁用HTTP日志服务器
- ❌ 禁用二维码功能
- ❌ 禁用所有远程日志查看
- ❌ 禁止通过网络访问日志
- ✅ 仅保留本地文件日志（加密存储）

## 实现机制

### 1. BuildConfig.DEBUG 检查

所有日志查看功能都通过 `BuildConfig.DEBUG` 进行运行时检查：

```kotlin
// DMPLogServer.kt - 日志HTTP服务器
fun startServer(): String {
    if (BuildConfig.DEBUG.not()) {
        logger.warn("Release模式，禁用日志HTTP服务器")
        return ""  // 直接返回，不启动服务器
    }
    // 启动服务器...
}

// LogViewerActivity.kt - 日志查看界面
override fun onCreate(savedInstanceState: Bundle?) {
    if (!BuildConfig.DEBUG) {
        Toast.makeText(this, "Release模式，日志查看功能已禁用", Toast.LENGTH_LONG).show()
        finish()  // 直接关闭Activity
        return
    }
    // 显示日志...
}

// DMPLogger.kt - 日志推送
private fun log(level: DMPLogLevel, message: String, ...) {
    // ... 写入日志
    
    // 仅Debug模式推送到HTTP服务器
    if (BuildConfig.DEBUG) {
        DMPLogServer.addLog(entry)
    }
}
```

### 2. Gradle构建配置

```gradle
// build.gradle
android {
    buildTypes {
        debug {
            // Debug模式：启用所有调试功能
            applicationIdSuffix ".debug"
            debuggable true
        }
        release {
            // Release模式：禁用所有调试功能
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            debuggable false
        }
    }
}
```

### 3. 依赖分离

```gradle
// NanoHTTPD（HTTP服务器）
debugImplementation 'org.nanohttpd:nanohttpd:2.3.1'
releaseImplementation 'org.nanohttpd:nanohttpd:2.3.1'

// ZXing（二维码）
implementation 'com.google.zxing:core:3.5.2'
```

## 安全边界

### 网络访问限制

| 模式 | HTTP服务器 | 二维码 | WiFi访问 | adb logcat |
|------|-----------|--------|---------|-----------|
| Debug | ✅ 启用 | ✅ 启用 | ✅ 同WiFi可访问 | ✅ 可用 |
| Release | ❌ 禁用 | ❌ 隐藏 | ❌ 不可访问 | ❌ 不可用 |

### 数据保护

**Debug模式**：
- 日志存储在内存缓冲区（最多2000条）
- 日志文件存储在应用缓存目录
- 敏感信息自动脱敏（API Key、Token等）

**Release模式**：
- 仅保留本地文件日志
- 日志文件加密存储
- 敏感信息严格脱敏
- 禁止任何网络访问

## 攻击场景防护

### 场景1：用户安装Release版本后被恶意查看日志

**防护**：
- ✅ `BuildConfig.DEBUG` 检查确保Release模式不启动HTTP服务器
- ✅ LogViewerActivity 直接关闭，不显示任何界面
- ✅ 即使反编译APK也无法启用（BuildConfig是编译时常量）

### 场景2：同一WiFi下的未授权访问

**防护**：
- ✅ 仅Debug模式可用，Release模式完全禁用
- ✅ 仅限同WiFi局域网，不暴露到公网
- ✅ 无持久化会话，关闭应用即断开

### 场景3：日志文件被窃取

**防护**：
- ✅ 敏感信息自动脱敏（API Key显示为 `sk-****`）
- ✅ 日志文件存储在应用私有目录
- ✅ Release模式使用加密存储

## 审计检查清单

### 发布前检查

- [ ] 确认使用 `./gradlew assembleRelease` 构建
- [ ] 确认APK的 `BuildConfig.DEBUG == false`
- [ ] 测试安装Release版本后无法打开日志查看器
- [ ] 测试同WiFi下无法访问 `http://<手机IP>:28473`
- [ ] 确认日志文件中无敏感信息（API Key、Token等）

### 代码检查

```bash
# 检查所有日志相关代码是否有BuildConfig.DEBUG检查
grep -r "BuildConfig.DEBUG" app/src/main/java/com/dmp/android/core/

# 确认没有硬编码的调试开关
grep -r "const val DEBUG = true" app/src/main/java/

# 确认没有注释掉的安全检查
grep -r "// if.*BuildConfig.DEBUG" app/src/main/java/
```

## 最佳实践

### 开发阶段

```kotlin
// ✅ 正确：使用BuildConfig.DEBUG
if (BuildConfig.DEBUG) {
    logServer.startServer()
}

// ❌ 错误：使用自定义调试开关
val DEBUG_MODE = true  // 不要这样做！
if (DEBUG_MODE) {
    logServer.startServer()
}
```

### 发布阶段

```bash
# ✅ 正确：使用Release构建
./gradlew assembleRelease

# ❌ 错误：使用Debug构建发布
./gradlew assembleDebug  # 不要这样做！
```

### 测试阶段

```bash
# 测试Release版本的安全限制
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk

# 验证日志查看器被禁用
adb shell am start -n com.dmp.android/.ui.LogViewerActivity
# 应该看到Toast提示"Release模式，日志查看功能已禁用"并自动关闭
```

## 总结

DMP的日志安全策略确保：

1. **开发便利**：Debug模式下可以方便地通过二维码查看日志
2. **用户安全**：Release模式下完全禁用所有日志查看功能
3. **编译时保证**：通过 `BuildConfig.DEBUG` 编译时常量确保无法绕过
4. **多层防护**：代码检查 + 构建配置 + 运行时检查三重保障

**绝对原则**：Release版本的APK，任何情况下都不允许通过网络查看日志！