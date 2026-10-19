package com.dmp.android.compatibility

import android.content.Context
import android.os.Build
import com.dmp.android.providers.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 兼容性测试运行器
 * 
 * 用于在设备上运行完整的兼容性测试套件
 * 生成测试报告供社区共享
 */
class CompatibilityTestRunner(private val context: Context) {
    
    companion object {
        private const val TAG = "CompatTestRunner"
    }
    
    /**
     * 运行完整测试套件
     */
    suspend fun runFullTestSuite(
        providers: List<LLMProvider>,
        testedBy: String
    ): CompatibilityTestReport = withContext(Dispatchers.IO) {
        val testResults = mutableListOf<CompatibilityTestReport.TestResult>()
        
        // 1. GPU 测试
        testResults.add(testGPU())
        
        // 2. NPU 测试
        testResults.add(testNPU())
        
        // 3. CPU 测试
        testResults.add(testCPU())
        
        // 4. 内存测试
        testResults.add(testMemory())
        
        // 5. Provider 测试
        for (provider in providers) {
            testResults.add(testProvider(provider))
        }
        
        // 计算总体状态
        val overallStatus = calculateOverallStatus(testResults)
        
        CompatibilityTestReport(
            deviceInfo = collectDeviceInfo(),
            testResults = testResults,
            overallStatus = overallStatus,
            testedBy = testedBy,
            testDate = java.time.LocalDate.now().toString(),
            notes = null
        )
    }
    
    /**
     * 测试 GPU
     */
    private suspend fun testGPU(): CompatibilityTestReport.TestResult {
        val startTime = System.currentTimeMillis()
        return try {
            val detector = RuntimeCapabilityDetector(context)
            val result = detector.testGPU()
            
            CompatibilityTestReport.TestResult(
                testName = "GPU Acceleration",
                status = if (result.isSupported) {
                    CompatibilityTestReport.TestStatus.PASSED
                } else {
                    CompatibilityTestReport.TestStatus.FAILED
                },
                durationMs = result.testDurationMs,
                errorMessage = result.errorMessage,
                metrics = mapOf(
                    "supported" to result.isSupported
                )
            )
        } catch (e: Exception) {
            CompatibilityTestReport.TestResult(
                testName = "GPU Acceleration",
                status = CompatibilityTestReport.TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message,
                metrics = null
            )
        }
    }
    
    /**
     * 测试 NPU
     */
    private suspend fun testNPU(): CompatibilityTestReport.TestResult {
        val startTime = System.currentTimeMillis()
        return try {
            val detector = RuntimeCapabilityDetector(context)
            val result = detector.testNPU()
            
            CompatibilityTestReport.TestResult(
                testName = "NPU/NNAPI",
                status = if (result.isSupported) {
                    CompatibilityTestReport.TestStatus.PASSED
                } else {
                    CompatibilityTestReport.TestStatus.FAILED
                },
                durationMs = result.testDurationMs,
                errorMessage = result.errorMessage,
                metrics = mapOf(
                    "supported" to result.isSupported
                )
            )
        } catch (e: Exception) {
            CompatibilityTestReport.TestResult(
                testName = "NPU/NNAPI",
                status = CompatibilityTestReport.TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message,
                metrics = null
            )
        }
    }
    
    /**
     * 测试 CPU
     */
    private suspend fun testCPU(): CompatibilityTestReport.TestResult {
        val startTime = System.currentTimeMillis()
        return try {
            val detector = RuntimeCapabilityDetector(context)
            val result = detector.testCPU()
            
            CompatibilityTestReport.TestResult(
                testName = "CPU Inference",
                status = CompatibilityTestReport.TestStatus.PASSED,
                durationMs = result.testDurationMs,
                errorMessage = null,
                metrics = mapOf(
                    "cores" to Runtime.getRuntime().availableProcessors(),
                    "performance" to (result.performance?.name ?: "UNKNOWN")
                )
            )
        } catch (e: Exception) {
            CompatibilityTestReport.TestResult(
                testName = "CPU Inference",
                status = CompatibilityTestReport.TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message,
                metrics = null
            )
        }
    }
    
    /**
     * 测试内存
     */
    private suspend fun testMemory(): CompatibilityTestReport.TestResult {
        val startTime = System.currentTimeMillis()
        return try {
            val detector = RuntimeCapabilityDetector(context)
            val memoryInfo = detector.getMemoryInfo()
            
            val status = if (memoryInfo.isLowMemoryDevice) {
                CompatibilityTestReport.TestStatus.PASSED_WITH_WARNINGS
            } else {
                CompatibilityTestReport.TestStatus.PASSED
            }
            
            CompatibilityTestReport.TestResult(
                testName = "Memory",
                status = status,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = null,
                metrics = mapOf(
                    "totalMemoryMB" to memoryInfo.totalMemoryMB,
                    "availableMemoryMB" to memoryInfo.availableMemoryMB,
                    "isLowMemory" to memoryInfo.isLowMemoryDevice
                )
            )
        } catch (e: Exception) {
            CompatibilityTestReport.TestResult(
                testName = "Memory",
                status = CompatibilityTestReport.TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message,
                metrics = null
            )
        }
    }
    
    /**
     * 测试 Provider
     */
    private suspend fun testProvider(provider: LLMProvider): CompatibilityTestReport.TestResult {
        val startTime = System.currentTimeMillis()
        return try {
            val isAvailable = provider.isAvailable()
            
            CompatibilityTestReport.TestResult(
                testName = "Provider: ${provider.name}",
                status = if (isAvailable) {
                    CompatibilityTestReport.TestStatus.PASSED
                } else {
                    CompatibilityTestReport.TestStatus.FAILED
                },
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = if (!isAvailable) "Provider 不可用" else null,
                metrics = mapOf(
                    "available" to isAvailable,
                    "priority" to provider.priority
                )
            )
        } catch (e: Exception) {
            CompatibilityTestReport.TestResult(
                testName = "Provider: ${provider.name}",
                status = CompatibilityTestReport.TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message,
                metrics = null
            )
        }
    }
    
    /**
     * 收集设备信息
     */
    private fun collectDeviceInfo(): CompatibilityTestReport.DeviceInfo {
        return CompatibilityTestReport.DeviceInfo(
            brand = Build.BRAND,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            cpuArchitecture = System.getProperty("os.arch") ?: "unknown",
            ramMB = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt(),
            gpuModel = null  // 需要通过 OpenGL/Vulkan API 获取
        )
    }
    
    /**
     * 计算总体状态
     */
    private fun calculateOverallStatus(
        results: List<CompatibilityTestReport.TestResult>
    ): CompatibilityTestReport.TestStatus {
        val failed = results.count { it.status == CompatibilityTestReport.TestStatus.FAILED }
        val warnings = results.count { it.status == CompatibilityTestReport.TestStatus.PASSED_WITH_WARNINGS }
        
        return when {
            failed > 0 -> CompatibilityTestReport.TestStatus.FAILED
            warnings > 0 -> CompatibilityTestReport.TestStatus.PASSED_WITH_WARNINGS
            else -> CompatibilityTestReport.TestStatus.PASSED
        }
    }
}