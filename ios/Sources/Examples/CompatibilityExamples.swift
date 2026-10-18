import Foundation

/// 兼容性模块使用示例
public enum CompatibilityExamples {
    
    /// 示例 1: 使用运行时能力检测
    public static func example1_RuntimeDetection() async {
        let detector = RuntimeCapabilityDetector()
        let report = detector.detectAllCapabilities()
        
        print("设备: \(report.deviceInfo)")
        print("推荐加速器: \(report.recommendedAccelerator)")
        print("ANE 支持: \(report.aneResult.isSupported)")
        print("GPU 支持: \(report.gpuResult.isSupported)")
        print("内存: \(report.memoryInfo.availableMemoryMB)MB 可用")
    }
    
    /// 示例 2: 查询设备兼容性信息
    public static func example2_CompatibilityCheck() {
        let info = DeviceCompatibility.getCurrentDeviceCompatibility()
        
        print("兼容性级别: \(info.level)")
        print("已知问题: \(info.knownIssues)")
        print("推荐配置:")
        print("  - ANE: \(info.recommendedConfig.useANE)")
        print("  - GPU: \(info.recommendedConfig.useGPU)")
        print("  - 降级链: \(info.recommendedConfig.fallbackChain.joined(separator: " -> "))")
    }
    
    /// 示例 3: 使用降级链
    public static func example3_FallbackChain(providers: [LLMProvider]) async {
        let chain = FallbackChain()
        
        let config = FallbackChain.ChainConfig(
            providers: providers,
            maxRetries: 2,
            fallbackStrategy: .sequential
        )
        
        let request = LLMRequest(
            messages: [
                Message(role: .user, content: "你好")
            ]
        )
        
        let result = await chain.executeWithFallback(request: request, config: config)
        
        if result.success {
            print("成功! 使用 Provider: \(result.usedProvider ?? "unknown")")
            print("降级次数: \(result.fallbackCount)")
            print("响应: \(result.response?.content ?? "")")
        } else {
            print("所有 Provider 都失败了!")
            for error in result.errors {
                print("  - \(error.providerName): \(error.errorMessage)")
            }
        }
    }
    
    /// 示例 4: 运行兼容性测试套件
    public static func example4_RunTestSuite(providers: [LLMProvider]) async {
        let runner = CompatibilityTestRunner()
        
        let report = await runner.runFullTestSuite(
            providers: providers,
            testedBy: "@developer_name"
        )
        
        print("测试报告:")
        print("设备: \(report.deviceInfo.deviceIdentifier)")
        print("总体状态: \(report.overallStatus)")
        
        for result in report.testResults {
            print("  \(result.testName): \(result.status)")
            if let errorMessage = result.errorMessage {
                print("    错误: \(errorMessage)")
            }
        }
    }
    
    /// 示例 5: 检查特定功能是否支持
    public static func example5_FeatureCheck() {
        let aneSupport = DeviceCompatibility.isFeatureSupported("ane")
        let gpuSupport = DeviceCompatibility.isFeatureSupported("gpu")
        
        print("ANE 支持级别: \(aneSupport)")
        print("GPU 支持级别: \(gpuSupport)")
        
        // 检查是否在黑名单中
        let aneBlacklisted = DeviceCompatibility.isBlacklisted("ane")
        print("ANE 黑名单: \(aneBlacklisted)")
    }
    
    /// 示例 6: 获取推荐降级链
    public static func example6_GetFallbackChain() {
        let chain = DeviceCompatibility.getRecommendedFallbackChain()
        print("推荐降级链: \(chain.joined(separator: " -> "))")
    }
}