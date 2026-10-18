package com.dmp.android.router

import android.content.Context
import com.dmp.android.core.*
import com.dmp.android.device.DeviceDetector
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import com.dmp.android.providers.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * LLM路由器 - 根据设备型号自动选择最优LLM提供者
 */
class LLMRouter(private val context: Context) {
    
    internal val providers = mutableListOf<LLMProvider>()
    
    init {
        DMPLogger.info("LLMRouter initialized", file = "LLMRouter.kt", function = "init", line = 0)
    }
    
    /**
     * 注册提供者
     */
    fun register(provider: LLMProvider) {
        providers.add(provider)
        providers.sortBy { it.priority }
        DMPLogger.info("注册LLM提供者: ${provider.name} (优先级: ${provider.priority})", 
                      file = "LLMRouter.kt", function = "register", line = 0)
    }
    
    /**
     * 根据设备类型自动注册推荐的提供者
     */
    fun autoRegister(
        tfliteModelPath: String? = null,
        huaweiApiKey: String? = null,
        xiaomiApiKey: String? = null,
        oppoApiKey: String? = null,
        vivoApiKey: String? = null,
        samsungApiKey: String? = null,
        googleApiKey: String? = null,
        sonyApiKey: String? = null,
        lgApiKey: String? = null,
        motorolaApiKey: String? = null,
        nokiaApiKey: String? = null,
        oneplusApiKey: String? = null,
        realmeApiKey: String? = null,
        cloudApiKey: String? = null,
        cloudBaseURL: String? = null,
        cloudModelName: String? = null,
        cloudApiType: String? = null
    ) {
        val deviceType = DeviceDetector.detectDevice()
        val recommendedProviders = DeviceDetector.getRecommendedProviders(deviceType)
        val deviceInfo = DeviceDetector.getDeviceInfo()
        
        DMPLogger.info("设备: ${deviceInfo["brand"]} ${deviceInfo["model"]}, 系统: Android ${deviceInfo["release"]} (API ${deviceInfo["sdk"]}), 推荐提供者: $recommendedProviders", 
                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
        
        // 清空现有提供者
        providers.clear()
        
        for (providerName in recommendedProviders) {
            when (providerName) {
                // 本地LLM服务（最高优先级）
                "local" -> {
                    register(LocalLLMProvider())
                }
                
                // 国内品牌
                "huawei" -> {
                    if (!huaweiApiKey.isNullOrEmpty()) {
                        register(HuaweiProvider(context, huaweiApiKey))
                    } else {
                        DMPLogger.info("Huawei API Key未提供,使用模拟模式注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                        register(HuaweiProvider(context, ""))
                    }
                }
                "xiaomi" -> {
                    if (!xiaomiApiKey.isNullOrEmpty()) {
                        register(XiaomiProvider(context, xiaomiApiKey))
                    } else {
                        DMPLogger.info("Xiaomi API Key未提供,使用模拟模式注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                        register(XiaomiProvider(context, ""))
                    }
                }
                "oppo" -> {
                    if (!oppoApiKey.isNullOrEmpty()) {
                        register(OPPOProvider(context, oppoApiKey))
                    } else {
                        DMPLogger.info("OPPO API Key未提供,使用模拟模式注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                        register(OPPOProvider(context, ""))
                    }
                }
                "vivo" -> {
                    if (!vivoApiKey.isNullOrEmpty()) {
                        register(VivoProvider(context, vivoApiKey))
                    } else {
                        DMPLogger.info("Vivo API Key未提供,使用模拟模式注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                        register(VivoProvider(context, ""))
                    }
                }
                "oneplus" -> {
                    if (!oneplusApiKey.isNullOrEmpty()) {
                        register(OnePlusProvider(context, oneplusApiKey))
                    } else {
                        DMPLogger.info("OnePlus API Key未提供,使用模拟模式注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                        register(OnePlusProvider(context, ""))
                    }
                }
                "realme" -> {
                    if (!realmeApiKey.isNullOrEmpty()) {
                        register(RealmeProvider(context, realmeApiKey))
                    } else {
                        DMPLogger.info("Realme API Key未提供,使用模拟模式注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                        register(RealmeProvider(context, ""))
                    }
                }
                
                // 海外品牌
                "samsung" -> {
                    register(SamsungProvider())
                }
                "google" -> {
                    register(GoogleProvider())
                }
                "sony" -> {
                    DMPLogger.warn("Sony提供者暂未实现,跳过注册", 
                                  file = "LLMRouter.kt", function = "autoRegister", line = 0)
                }
                "lg" -> {
                    DMPLogger.warn("LG提供者暂未实现,跳过注册", 
                                  file = "LLMRouter.kt", function = "autoRegister", line = 0)
                }
                "motorola" -> {
                    DMPLogger.warn("Motorola提供者暂未实现,跳过注册", 
                                  file = "LLMRouter.kt", function = "autoRegister", line = 0)
                }
                "nokia" -> {
                    DMPLogger.warn("Nokia提供者暂未实现,跳过注册", 
                                  file = "LLMRouter.kt", function = "autoRegister", line = 0)
                }
                
                // 通用提供者
                "tflite" -> {
                    register(TFLiteProvider(context, tfliteModelPath))
                }
                "cloud" -> {
                    if (!cloudApiKey.isNullOrEmpty()) {
                        register(CloudProvider(
                            apiKey = cloudApiKey,
                            baseURL = cloudBaseURL ?: "https://api.openai.com/v1",
                            modelName = cloudModelName ?: "gpt-3.5-turbo"
                        ))
                    } else {
                        DMPLogger.warn("云端API密钥未提供,跳过注册", 
                                      file = "LLMRouter.kt", function = "autoRegister", line = 0)
                    }
                }
            }
        }
        
        // 如果配置了云端 API Key，把 cloud 放到最前面优先尝试
        if (!cloudApiKey.isNullOrEmpty()) {
            val cloudIndex = providers.indexOfFirst { it.name == "cloud" }
            if (cloudIndex >= 0) {
                val cloudProvider = providers.removeAt(cloudIndex)
                // 创建一个临时的 wrapper 让 cloud 优先级最高
                // 直接修改排序：将 cloud 插到最前面
                providers.add(0, cloudProvider)
                DMPLogger.info("云端已配置，cloud provider 已设为最高优先级", 
                              file = "LLMRouter.kt", function = "autoRegister", line = 0)
            }
        }
        
        // 确保至少有一个Provider被注册
        if (providers.isEmpty()) {
            DMPLogger.warn("没有Provider被注册,添加FallbackProvider", 
                          file = "LLMRouter.kt", function = "autoRegister", line = 0)
            register(FallbackProvider())
        } else {
            DMPLogger.info("已注册${providers.size}个Provider: ${providers.map { it.name }}", 
                          file = "LLMRouter.kt", function = "autoRegister", line = 0)
        }
    }
    
    /**
     * 聊天请求 (自动回退)
     */
    suspend fun chat(request: LLMRequest): LLMResponse {
        val traceID = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        DMPLogger.info("开始处理聊天请求 (TraceID: $traceID)", 
                      file = "LLMRouter.kt", function = "chat", line = 0)
        
        return DMPLogger.withTraceIDSuspend(traceID) {
            var lastError: Throwable? = null
            val attemptedProviders = mutableListOf<String>()
            
            for (provider in providers) {
                try {
                    DMPLogger.debug("尝试提供者: ${provider.name} (TraceID: $traceID)", 
                                   file = "LLMRouter.kt", function = "chat", line = 0)
                    attemptedProviders.add(provider.name)
                    
                    if (!provider.isAvailable()) {
                        DMPLogger.warn("提供者不可用: ${provider.name} (TraceID: $traceID)", 
                                      file = "LLMRouter.kt", function = "chat", line = 0)
                        continue
                    }
                    
                    val response = provider.chat(request)
                    val duration = System.currentTimeMillis() - startTime
                    
                    DMPLogger.info("成功使用提供者: ${provider.name}, 耗时: ${duration}ms (TraceID: $traceID)", 
                                  file = "LLMRouter.kt", function = "chat", line = 0)
                    
                    return@withTraceIDSuspend response
                    
                } catch (e: Exception) {
                    DMPLogger.error("提供者失败: ${provider.name}, 错误: ${e.message} (TraceID: $traceID)", 
                                   file = "LLMRouter.kt", function = "chat", line = 0, throwable = e)
                    lastError = e
                    
                    // 追踪错误
                    if (e is DMPError) {
                        DMPErrorTracker.trackError(e)
                    }
                    
                    continue
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            DMPLogger.error("所有提供者均失败, 尝试: ${attemptedProviders.joinToString(", ")}, 耗时: ${duration}ms (TraceID: $traceID)", 
                           file = "LLMRouter.kt", function = "chat", line = 0)
            
            throw DMPError(
                code = DMPErrorCode.NoProviderAvailable,
                message = "所有LLM提供者均失败"
            )
        }
    }
    
    /**
     * 流式聊天请求 (自动回退)
     */
    fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        val traceID = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        DMPLogger.info("========== 开始流式聊天请求 ==========")
        DMPLogger.info("TraceID: $traceID")
        DMPLogger.info("已注册Provider: ${providers.map { it.name }}")
        DMPLogger.info("======================================")
        
        var lastError: Throwable? = null
        val errorDetails = mutableListOf<String>()
        var successProvider: String? = null
        
        for (provider in providers) {
            try {
                DMPLogger.info("---------- 尝试 Provider: ${provider.name} ----------")
                
                val available = provider.isAvailable()
                DMPLogger.info("${provider.name}.isAvailable() = $available")
                
                if (!available) {
                    DMPLogger.warn("跳过 ${provider.name}: 不可用")
                    errorDetails.add("❌ ${provider.name}: 不可用")
                    continue
                }
                
                DMPLogger.info("开始调用 ${provider.name}.chatStream()")
                
                var fullContent = ""
                var finalModel = ""
                var finalUsage: LLMResponse.UsageInfo? = null
                
                provider.chatStream(request).collect { chunk ->
                    fullContent += chunk.content
                    finalModel = chunk.model
                    finalUsage = chunk.usage
                    
                    // 实时发送带警告的响应
                    emit(chunk.copy(
                        warnings = if (errorDetails.isNotEmpty()) {
                            listOf("⚠️ 前面的Provider失败详情:", *errorDetails.toTypedArray())
                        } else emptyList()
                    ))
                }
                
                successProvider = provider.name
                val duration = System.currentTimeMillis() - startTime
                DMPLogger.info("✅ 流式成功: ${provider.name}, 耗时: ${duration}ms")
                
                // 如果有失败记录，发送最终警告信息
                if (errorDetails.isNotEmpty()) {
                    val warningMessage = buildString {
                        append("⚠️ 前面的Provider失败详情:\n")
                        errorDetails.forEach { detail ->
                            append("$detail\n")
                        }
                    }
                    emit(LLMResponse(
                        content = "",
                        provider = provider.name,
                        model = finalModel,
                        usage = finalUsage,
                        warnings = listOf(warningMessage)
                    ))
                }
                
                return@flow
                
            } catch (e: Exception) {
                DMPLogger.error("❌ ${provider.name} 失败: ${e.javaClass.simpleName} - ${e.message}")
                lastError = e
                errorDetails.add("❌ ${provider.name}: ${e.message ?: e.javaClass.simpleName}")
                
                if (e is DMPError) {
                    DMPErrorTracker.trackError(e)
                }
                
                DMPLogger.info("继续尝试下一个 Provider...")
                continue
            }
        }
        
        DMPLogger.error("========== 所有流式提供者均失败 ==========")
        DMPLogger.error("尝试的Provider: ${providers.map { it.name }}")
        DMPLogger.error("最后错误: ${lastError?.javaClass?.simpleName} - ${lastError?.message}")
        DMPLogger.error("==========================================")
        
        // 构建详细错误信息
        val detailedMessage = buildString {
            append("所有LLM提供者均失败\n\n")
            append("📋 各Provider失败详情:\n")
            errorDetails.forEach { detail ->
                append("$detail\n")
            }
            if (lastError != null) {
                append("\n🔍 最后错误详情:\n")
                append("${lastError?.javaClass?.simpleName}: ${lastError?.message}")
            }
        }
        
        throw DMPError(
            code = DMPErrorCode.NoProviderAvailable,
            message = detailedMessage
        )
    }
    
    /**
     * 获取提供者状态
     */
    suspend fun getProviderStatus(): Map<String, Boolean> {
        val status = mutableMapOf<String, Boolean>()
        
        for (provider in providers) {
            val isAvailable = provider.isAvailable()
            status[provider.name] = isAvailable
            
            DMPLogger.debug("提供者状态: ${provider.name} = ${if (isAvailable) "可用" else "不可用"}", 
                           file = "LLMRouter.kt", function = "getProviderStatus", line = 0)
        }
        
        return status
    }
}