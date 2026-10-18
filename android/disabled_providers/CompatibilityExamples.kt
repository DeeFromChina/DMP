package com.dmp.android.example

import android.content.Context
import com.dmp.android.compatibility.CompatibilityTestRunner
import com.dmp.android.compatibility.DeviceCompatibility
import com.dmp.android.compatibility.FallbackChain
import com.dmp.android.compatibility.RuntimeCapabilityDetector
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.Message
import com.dmp.android.models.Role
import com.dmp.android.providers.LLMProvider
import kotlinx.coroutines.runBlocking

/**
 * 兼容性模块使用示例
 */
object CompatibilityExamples {
    
    /**
     * 示例 1: 使用运行时能力检测
     */
    fun example1_RuntimeDetection(context: Context) {
        val detector = RuntimeCapabilityDetector(context)
        
        // 检测所有能力
        val report = detector.detectAllCapabilities()
        
        println("设备: ${report.deviceInfo}")
        println("推荐加速器: ${report.recommendedAccelerator}")
        println("GPU 支持: ${report.gpuResult.isSupported}")
        println("NPU 支持: ${report.npuResult.isSupported}")
        println("内存: ${report.memoryInfo.availableMemoryMB}MB 可用")
    }
    
    /**
     * 示例 2: 查询设备兼容性信息
     */
    fun example2_CompatibilityCheck() {
        val info = DeviceCompatibility.getCurrentDeviceCompatibility()
        
        println("兼容性级别: ${info.level}")
        println("已知问题: ${info.knownIssues}")
        println("推荐配置:")
        println("  - GPU: ${info.recommendedConfig.useGPU}")
        println("  - NPU: ${info.recommendedConfig.useNPU}")
        println("  - 线程数: ${info.recommendedConfig.numThreads}")
        println("  - 降级链: ${info.recommendedConfig.fallbackChain}")
    }
    
    /**
     * 示例 3: 使用降级链
     */
    fun example3_FallbackChain(providers: List<LLMProvider>) {
        runBlocking {
            val chain = FallbackChain()
            
            val config = FallbackChain.ChainConfig(
                providers = providers,
                maxRetries = 2,
                timeoutMs = 30000,
                fallbackStrategy = FallbackChain.FallbackStrategy.SEQUENTIAL
            )
            
            val request = LLMRequest(
                messages = listOf(
                    Message(Role.USER, "你好")
                )
            )
            
            val result = chain.executeWithFallback(request, config)
            
            if (result.success) {
                println("成功! 使用 Provider: ${result.usedProvider}")
                println("降级次数: ${result.fallbackCount}")
                println("响应: ${result.response?.content}")
            } else {
                println("所有 Provider 都失败了!")
                result.errors.forEach { error ->
                    println("  - ${error.providerName}: ${error.errorMessage}")
                }
            }
        }
    }
    
    /**
     * 示例 4: 运行兼容性测试套件
     */
    fun example4_RunTestSuite(context: Context, providers: List<LLMProvider>) {
        runBlocking {
            val runner = CompatibilityTestRunner(context)
            
            val report = runner.runFullTestSuite(
                providers = providers,
                testedBy = "@developer_name"
            )
            
            println("测试报告:")
            println("设备: ${report.deviceInfo.brand}/${report.deviceInfo.model}")
            println("总体状态: ${report.overallStatus}")
            
            report.testResults.forEach { result ->
                println("  ${result.testName}: ${result.status}")
                if (result.errorMessage != null) {
                    println("    错误: ${result.errorMessage}")
                }
            }
        }
    }
    
    /**
     * 示例 5: 检查特定功能是否支持
     */
    fun example5_FeatureCheck() {
        val gpuSupport = DeviceCompatibility.isFeatureSupported("gpu")
        val npuSupport = DeviceCompatibility.isFeatureSupported("npu")
        
        println("GPU 支持级别: $gpuSupport")
        println("NPU 支持级别: $npuSupport")
        
        // 检查是否在黑名单中
        val gpuBlacklisted = DeviceCompatibility.isBlacklisted("gpu")
        println("GPU 黑名单: $gpuBlacklisted")
    }
    
    /**
     * 示例 6: 获取推荐降级链
     */
    fun example6_GetFallbackChain() {
        val chain = DeviceCompatibility.getRecommendedFallbackChain()
        println("推荐降级链: ${chain.joinToString(" -> ")}")
    }
}