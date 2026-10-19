package com.dmp.android.providers

import android.content.Context
import com.dmp.android.compatibility.DeviceCompatibility
import com.dmp.android.compatibility.RuntimeCapabilityDetector
import com.dmp.android.core.DMPError
import com.dmp.android.core.DMPErrorCode
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite本地模型提供者
 * 
 * 已集成设备兼容性管理：
 * 1. 根据设备兼容性自动选择最优配置
 * 2. 运行时检测硬件加速能力
 * 3. 优雅降级（GPU -> NPU -> CPU）
 */
class TFLiteProvider(
    private val context: Context,
    private val modelPath: String? = null
) : LLMProvider {
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val capabilityDetector = RuntimeCapabilityDetector(context)
    
    override val name: String = "tflite"
    override val priority: Int = 2
    
    override suspend fun isAvailable(): Boolean {
        if (modelPath.isNullOrEmpty()) {
            // 没有模型路径时使用模拟模式
            return true
        }
        
        // 检查模型文件是否存在
        return try {
            val assetManager = context.assets
            assetManager.openFd(modelPath)
            
            // 尝试加载模型
            if (interpreter == null) {
                loadModel()
            }
            interpreter != null
        } catch (e: Exception) {
            // 模型加载失败时使用模拟模式
            true
        }
    }
    
    override suspend fun chat(request: LLMRequest): LLMResponse {
        val prompt = request.messages.joinToString("\n") { "${it.role}: ${it.content}" }
        
        // 如果没有加载模型,使用模拟响应
        if (interpreter == null) {
            return LLMResponse(
                content = "TFLite本地模型未加载,当前使用模拟模式。请配置模型文件以启用真实推理。",
                provider = name,
                model = "tflite-mock"
            )
        }
        
        // TODO: 实现实际的TFLite推理
        val content = runInference(prompt)
        
        return LLMResponse(
            content = content,
            provider = name,
            model = "tflite-local"
        )
    }
    
    override fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        val response = chat(request)
        response.content.split(" ").forEach { word ->
            emit(LLMResponse(
                content = "$word ",
                provider = name,
                model = "tflite-local"
            ))
            delay(50)
        }
    }
    
    /**
     * 加载模型（使用兼容性感知配置）
     */
    private fun loadModel() {
        try {
            val assetManager = context.assets
            val fileDescriptor = assetManager.openFd(modelPath!!)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            
            // 获取设备兼容性配置
            val compatInfo = DeviceCompatibility.getCurrentDeviceCompatibility()
            val config = compatInfo.recommendedConfig
            
            android.util.Log.d("TFLiteProvider", "使用兼容性配置: GPU=${config.useGPU}, NPU=${config.useNPU}, 线程=${config.numThreads}")
            
            val options = Interpreter.Options().apply {
                // 根据兼容性配置设置线程数
                setNumThreads(config.numThreads)
                
                // 根据兼容性配置启用 XNNPACK
                setUseXNNPACK(config.useXNNPACK)
                
                // 根据兼容性配置启用 GPU
                if (config.useGPU && !DeviceCompatibility.isBlacklisted("gpu")) {
                    try {
                        gpuDelegate = GpuDelegate()
                        addDelegate(gpuDelegate!!)
                        android.util.Log.d("TFLiteProvider", "GPU 加速已启用")
                    } catch (e: Exception) {
                        android.util.Log.w("TFLiteProvider", "GPU 启用失败，降级到 CPU: ${e.message}")
                        gpuDelegate = null
                    }
                }
                
                // 根据兼容性配置启用 NNAPI
                if (config.useNPU && !DeviceCompatibility.isBlacklisted("npu")) {
                    try {
                        setUseNNAPI(true)
                        android.util.Log.d("TFLiteProvider", "NNAPI 已启用")
                    } catch (e: Exception) {
                        android.util.Log.w("TFLiteProvider", "NNAPI 启用失败: ${e.message}")
                    }
                }
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            android.util.Log.i("TFLiteProvider", "模型加载成功: $modelPath")
        } catch (e: Exception) {
            android.util.Log.e("TFLiteProvider", "模型加载失败", e)
            
            // 尝试使用最保守的配置重新加载
            try {
                android.util.Log.w("TFLiteProvider", "尝试使用保守配置重新加载...")
                loadModelWithFallbackConfig()
            } catch (e2: Exception) {
                android.util.Log.e("TFLiteProvider", "保守配置加载也失败了", e2)
                throw e2
            }
        }
    }
    
    /**
     * 使用保守配置加载模型（降级策略）
     */
    private fun loadModelWithFallbackConfig() {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelPath!!)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        
        val modelBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
        
        // 最保守的配置：仅使用 CPU，单线程
        val options = Interpreter.Options().apply {
            setNumThreads(1)
            setUseXNNPACK(false)
            setUseNNAPI(false)
        }
        
        interpreter = Interpreter(modelBuffer, options)
        android.util.Log.w("TFLiteProvider", "使用保守配置加载成功（仅 CPU）")
    }
    
    private fun runInference(prompt: String): String {
        // TODO: 实现实际的推理逻辑
        return "[TFLite本地模型] $prompt"
    }
    
    /**
     * 释放资源
     */
    fun release() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}