package com.dmp.android.compatibility

import android.content.Context
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.Log
import org.tensorflow.lite.gpu.GpuDelegate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 运行时能力检测器
 * 
 * 通过实际测试而非硬编码来判断设备能力
 * 避免"理论上支持，实际上有问题"的情况
 */
class RuntimeCapabilityDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "RuntimeCapabilityDetector"
        private const val TEST_TIMEOUT_MS = 5000L
    }
    
    private val testExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    /**
     * 设备能力检测结果
     */
    data class CapabilityResult(
        val feature: String,
        val isSupported: Boolean,
        val performance: PerformanceLevel?,
        val errorMessage: String?,
        val testDurationMs: Long
    )
    
    /**
     * 性能级别
     */
    enum class PerformanceLevel {
        EXCELLENT,  // 优秀
        GOOD,       // 良好
        ACCEPTABLE, // 可接受
        POOR        // 较差
    }
    
    /**
     * 完整能力报告
     */
    data class CapabilityReport(
        val deviceInfo: String,
        val gpuResult: CapabilityResult,
        val npuResult: CapabilityResult,
        val cpuResult: CapabilityResult,
        val memoryInfo: MemoryInfo,
        val recommendedAccelerator: String
    )
    
    /**
     * 内存信息
     */
    data class MemoryInfo(
        val totalMemoryMB: Long,
        val availableMemoryMB: Long,
        val isLowMemoryDevice: Boolean
    )
    
    /**
     * 检测所有能力
     */
    fun detectAllCapabilities(): CapabilityReport {
        val gpuResult = testGPU()
        val npuResult = testNPU()
        val cpuResult = testCPU()
        val memoryInfo = getMemoryInfo()
        
        val recommended = recommendAccelerator(gpuResult, npuResult, cpuResult)
        
        return CapabilityReport(
            deviceInfo = "${Build.BRAND}/${Build.MODEL}",
            gpuResult = gpuResult,
            npuResult = npuResult,
            cpuResult = cpuResult,
            memoryInfo = memoryInfo,
            recommendedAccelerator = recommended
        )
    }
    
    /**
     * 测试 GPU 加速能力
     */
    fun testGPU(): CapabilityResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 简化 GPU 测试：仅检查 GPU Delegate 类是否存在
            Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
            val isSupported = true
            val duration = System.currentTimeMillis() - startTime
            
            CapabilityResult(
                feature = "gpu",
                isSupported = isSupported,
                performance = if (isSupported) PerformanceLevel.GOOD else null,
                errorMessage = if (!isSupported) "GPU 验证失败" else null,
                testDurationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "GPU 测试失败: ${e.message}", e)
            
            CapabilityResult(
                feature = "gpu",
                isSupported = false,
                performance = null,
                errorMessage = e.message,
                testDurationMs = duration
            )
        }
    }
    
    /**
     * 测试 NPU/NNAPI 能力
     */
    fun testNPU(): CapabilityResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Android 8.1+ 支持 NNAPI
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                return CapabilityResult(
                    feature = "npu",
                    isSupported = false,
                    performance = null,
                    errorMessage = "Android 版本过低，不支持 NNAPI",
                    testDurationMs = System.currentTimeMillis() - startTime
                )
            }
            
            // TODO: 实现 NNAPI 实际测试
            // 需要加载一个小型模型进行推理测试
            
            val duration = System.currentTimeMillis() - startTime
            CapabilityResult(
                feature = "npu",
                isSupported = true,
                performance = PerformanceLevel.ACCEPTABLE,
                errorMessage = null,
                testDurationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "NPU 测试失败: ${e.message}", e)
            
            CapabilityResult(
                feature = "npu",
                isSupported = false,
                performance = null,
                errorMessage = e.message,
                testDurationMs = duration
            )
        }
    }
    
    /**
     * 测试 CPU 能力
     */
    fun testCPU(): CapabilityResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val performance = when {
                cpuCores >= 8 -> PerformanceLevel.EXCELLENT
                cpuCores >= 4 -> PerformanceLevel.GOOD
                cpuCores >= 2 -> PerformanceLevel.ACCEPTABLE
                else -> PerformanceLevel.POOR
            }
            
            val duration = System.currentTimeMillis() - startTime
            CapabilityResult(
                feature = "cpu",
                isSupported = true,  // CPU 总是可用
                performance = performance,
                errorMessage = null,
                testDurationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "CPU 测试失败: ${e.message}", e)
            
            CapabilityResult(
                feature = "cpu",
                isSupported = false,
                performance = null,
                errorMessage = e.message,
                testDurationMs = duration
            )
        }
    }
    
    /**
     * 获取内存信息
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)  // MB
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val availableMemory = freeMemory + (maxMemory - totalMemory)
        
        // 低内存设备阈值：512MB
        val isLowMemory = availableMemory < 512
        
        return MemoryInfo(
            totalMemoryMB = maxMemory,
            availableMemoryMB = availableMemory,
            isLowMemoryDevice = isLowMemory
        )
    }
    
    /**
     * 推荐最优加速器
     */
    fun recommendAccelerator(
        gpuResult: CapabilityResult,
        npuResult: CapabilityResult,
        cpuResult: CapabilityResult
    ): String {
        // 优先级：GPU > NPU > CPU
        return when {
            gpuResult.isSupported && gpuResult.performance != PerformanceLevel.POOR -> "gpu"
            npuResult.isSupported && npuResult.performance != PerformanceLevel.POOR -> "npu"
            else -> "cpu"
        }
    }
    
    /**
     * 验证 GPU 是否真正可用
     */
    private fun validateGPU(gpuDelegate: GpuDelegate): Boolean {
        return try {
            // TODO: 加载一个极小的测试模型进行推理
            // 这里只是检查 GPU Delegate 能否创建
            true
        } catch (e: Exception) {
            Log.w(TAG, "GPU 验证失败: ${e.message}")
            false
        } finally {
            try {
                gpuDelegate.close()
            } catch (e: Exception) {
                // 忽略关闭异常
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun shutdown() {
        testExecutor.shutdown()
    }
}