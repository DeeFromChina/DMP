# 设备兼容性管理模块

## 概述

设备兼容性管理模块用于解决 Android 多设备碎片化问题，提供：
- **设备兼容性矩阵**：维护已验证/未验证/已知问题的设备列表
- **运行时能力检测**：通过实际测试而非硬编码判断设备能力
- **优雅降级链**：当首选方案失败时自动尝试备选方案
- **社区测试报告**：支持贡献者提交设备测试报告

## 架构

```
compatibility/
├── DeviceCompatibility.kt          # 设备兼容性矩阵和配置推荐
├── RuntimeCapabilityDetector.kt    # 运行时硬件能力检测
├── FallbackChain.kt                # 优雅降级链管理
├── CompatibilityTestRunner.kt      # 兼容性测试套件
├── CompatibilityTestReport.kt      # 测试报告数据模型
└── README.md                       # 本文档
```

## 快速开始

### 1. 查询设备兼容性信息

```kotlin
val info = DeviceCompatibility.getCurrentDeviceCompatibility()

println("兼容性级别: ${info.level}")
println("推荐配置:")
println("  - GPU: ${info.recommendedConfig.useGPU}")
println("  - NPU: ${info.recommendedConfig.useNPU}")
println("  - 降级链: ${info.recommendedConfig.fallbackChain}")
```

### 2. 运行时能力检测

```kotlin
val detector = RuntimeCapabilityDetector(context)
val report = detector.detectAllCapabilities()

println("推荐加速器: ${report.recommendedAccelerator}")
println("GPU 支持: ${report.gpuResult.isSupported}")
println("内存: ${report.memoryInfo.availableMemoryMB}MB")
```

### 3. 使用降级链

```kotlin
val chain = FallbackChain()
val config = FallbackChain.ChainConfig(
    providers = listOf(gpuProvider, cpuProvider, cloudProvider),
    maxRetries = 2,
    fallbackStrategy = FallbackChain.FallbackStrategy.SEQUENTIAL
)

val result = chain.executeWithFallback(request, config)

if (result.success) {
    println("成功! 使用: ${result.usedProvider}")
} else {
    println("所有 Provider 都失败了")
}
```

### 4. 运行兼容性测试

```kotlin
val runner = CompatibilityTestRunner(context)
val report = runner.runFullTestSuite(providers, "@your_name")

println("测试状态: ${report.overallStatus}")
report.testResults.forEach {
    println("${it.testName}: ${it.status}")
}
```

## 兼容性级别

| 级别 | 说明 | 策略 |
|------|------|------|
| `FULL_VERIFIED` | 完全验证 | 启用所有加速，激进配置 |
| `PARTIAL_VERIFIED` | 部分验证 | 启用加速，保守配置 |
| `UNVERIFIED` | 未验证 | 仅使用 CPU/XNNPACK |
| `KNOWN_ISSUES` | 已知问题 | 特殊处理，降级策略 |
| `BLACKLISTED` | 黑名单 | 禁用特定功能 |

## 如何贡献

### 提交设备测试报告

1. 运行测试套件
2. 检查测试结果
3. 提交报告到项目 Issue

```kotlin
val runner = CompatibilityTestRunner(context)
val report = runner.runFullTestSuite(providers, "@your_github_name")

// 将报告内容复制到 GitHub Issue
```

### 添加新设备到兼容性矩阵

在 `DeviceCompatibility.kt` 中添加：

```kotlin
private val FULL_VERIFIED_DEVICES = setOf(
    "Huawei/ELS-AN00",          // 添加新设备
    // ...
)
```

### 报告设备问题

```kotlin
DeviceCompatibility.reportDeviceIssue(
    deviceKey = "Xiaomi/Mi 11",
    issue = "GPU 偶发崩溃",
    severity = "medium",
    workaround = "添加异常捕获"
)
```

## 最佳实践

### ✅ 推荐做法

1. **运行时检测优于硬编码**
   ```kotlin
   // ✅ 好
   val result = detector.testGPU()
   if (result.isSupported) { /* 使用 GPU */ }
   
   // ❌ 不好
   if (Build.BRAND == "xiaomi") { /* 硬编码 */ }
   ```

2. **总是提供降级方案**
   ```kotlin
   try {
       loadModelWithGPU()
   } catch (e: Exception) {
       loadModelWithCPU()  // 降级
   }
   ```

3. **记录设备信息**
   ```kotlin
   Log.d("App", "设备: ${Build.BRAND}/${Build.MODEL}")
   Log.d("App", "兼容性: ${DeviceCompatibility.getCurrentDeviceCompatibility().level}")
   ```

### ❌ 避免的做法

1. 不要假设所有设备行为一致
2. 不要忽略异常继续执行
3. 不要在生产环境跳过兼容性检查

## 云测试平台推荐

| 平台 | 免费额度 | 支持设备 |
|------|----------|----------|
| 华为 AGC | 有 | 华为全系 |
| Samsung RTL | 有 | 三星全系 |
| WeTest | 有 | 国内主流 |
| Firebase Test Lab | 有 | Pixel 等 |

## 更新日志

- **v1.0.0** (2024-01-15)
  - 初始版本
  - 设备兼容性矩阵
  - 运行时能力检测
  - 优雅降级链
  - 测试报告系统