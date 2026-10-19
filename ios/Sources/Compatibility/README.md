# 设备兼容性管理模块 (iOS)

## 概述

设备兼容性管理模块用于解决 iOS 设备碎片化问题，提供：
- **设备兼容性矩阵**：维护已验证/未验证/已知问题的设备列表
- **运行时能力检测**：通过实际测试而非硬编码判断设备能力
- **优雅降级链**：当首选方案失败时自动尝试备选方案
- **社区测试报告**：支持贡献者提交设备测试报告

## 架构

```
Compatibility/
├── DeviceCompatibility.swift          # 设备兼容性矩阵和配置推荐
├── RuntimeCapabilityDetector.swift    # 运行时硬件能力检测
├── FallbackChain.swift                # 优雅降级链管理
├── CompatibilityTestRunner.swift      # 兼容性测试套件
├── CompatibilityTestReport.swift      # 测试报告数据模型
└── README.md                          # 本文档
```

## 快速开始

### 1. 查询设备兼容性信息

```swift
let info = DeviceCompatibility.getCurrentDeviceCompatibility()

print("兼容性级别: \(info.level)")
print("推荐配置:")
print("  - ANE: \(info.recommendedConfig.useANE)")
print("  - GPU: \(info.recommendedConfig.useGPU)")
print("  - 降级链: \(info.recommendedConfig.fallbackChain)")
```

### 2. 运行时能力检测

```swift
let detector = RuntimeCapabilityDetector()
let report = detector.detectAllCapabilities()

print("推荐加速器: \(report.recommendedAccelerator)")
print("ANE 支持: \(report.aneResult.isSupported)")
print("GPU 支持: \(report.gpuResult.isSupported)")
print("内存: \(report.memoryInfo.availableMemoryMB)MB")
```

### 3. 使用降级链

```swift
let chain = FallbackChain()
let config = FallbackChain.ChainConfig(
    providers: [aneProvider, gpuProvider, cloudProvider],
    maxRetries: 2,
    fallbackStrategy: .sequential
)

let result = await chain.executeWithFallback(request: request, config: config)

if result.success {
    print("成功! 使用: \(result.usedProvider)")
} else {
    print("所有 Provider 都失败了")
}
```

### 4. 运行兼容性测试

```swift
let runner = CompatibilityTestRunner()
let report = await runner.runFullTestSuite(providers: providers, testedBy: "@your_name")

print("测试状态: \(report.overallStatus)")
for result in report.testResults {
    print("\(result.testName): \(result.status)")
}
```

## 兼容性级别

| 级别 | 说明 | 策略 |
|------|------|------|
| `fullVerified` | 完全验证 | 启用 ANE+GPU，激进配置 |
| `partialVerified` | 部分验证 | 启用加速，保守配置 |
| `unverified` | 未验证 | 仅使用 CPU |
| `knownIssues` | 已知问题 | 特殊处理，降级策略 |
| `blacklisted` | 黑名单 | 禁用特定功能 |

## iOS 特定功能

### ANE (Apple Neural Engine)

iOS 设备的 Neural Engine 是首选加速器，但某些旧设备存在兼容性问题：

```swift
// 检查 ANE 是否可用
let aneSupport = DeviceCompatibility.isFeatureSupported("ane")
let aneBlacklisted = DeviceCompatibility.isBlacklisted("ane")
```

### Metal GPU

Metal GPU 在某些旧设备上可能不稳定：

```swift
// 运行时检测 GPU 能力
let detector = RuntimeCapabilityDetector()
let gpuResult = detector.testGPU()

if gpuResult.isSupported {
    // 使用 GPU 加速
}
```

## 如何贡献

### 提交设备测试报告

1. 运行测试套件
2. 检查测试结果
3. 提交报告到项目 Issue

```swift
let runner = CompatibilityTestRunner()
let report = await runner.runFullTestSuite(providers: providers, testedBy: "@your_github_name")

// 将报告内容复制到 GitHub Issue
```

### 添加新设备到兼容性矩阵

在 `DeviceCompatibility.swift` 中添加：

```swift
private static let fullVerifiedDevices: Set<String> = [
    "iPhone16,1",  // iPhone 15 Pro
    "iPhone17,1",  // iPhone 16 Pro
    // 添加新设备
]
```

### 报告设备问题

```swift
DeviceCompatibility.reportDeviceIssue(
    deviceKey: "iPhone15,3",
    issue: "Core ML 首次加载较慢",
    severity: "medium",
    workaround: "添加预加载机制"
)
```

## 最佳实践

### ✅ 推荐做法

1. **运行时检测优于硬编码**
   ```swift
   // ✅ 好
   let result = detector.testGPU()
   if result.isSupported { /* 使用 GPU */ }
   
   // ❌ 不好
   if deviceModel == "iPhone15,3" { /* 硬编码 */ }
   ```

2. **总是提供降级方案**
   ```swift
   do {
       try await loadModelWithANE()
   } catch {
       try await loadModelWithCPU()  // 降级
   }
   ```

3. **记录设备信息**
   ```swift
   print("设备: \(getDeviceKey())")
   print("兼容性: \(DeviceCompatibility.getCurrentDeviceCompatibility().level)")
   ```

### ❌ 避免的做法

1. 不要假设所有设备行为一致
2. 不要忽略异常继续执行
3. 不要在生产环境跳过兼容性检查

## 设备标识符参考

| 设备名称 | 标识符 |
|----------|--------|
| iPhone 15 Pro | iPhone16,1 |
| iPhone 15 Pro Max | iPhone16,2 |
| iPhone 16 Pro | iPhone17,1 |
| iPhone 16 Pro Max | iPhone17,2 |
| iPhone 14 Pro | iPhone15,3 |
| iPhone 14 Pro Max | iPhone15,4 |
| iPad Pro (M2) | iPad14,1 |

## 更新日志

- **v1.0.0** (2024-01-15)
  - 初始版本
  - 设备兼容性矩阵
  - 运行时能力检测
  - 优雅降级链
  - 测试报告系统