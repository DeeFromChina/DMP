package com.dmp.android.providers

import com.dmp.android.core.DMPLogger
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fallback提供者 - 当所有其他提供者都不可用时使用
 */
class FallbackProvider : LLMProvider {
    override val name: String = "fallback"
    override val priority: Int = 999
    
    private val logger = DMPLogger
    
    override suspend fun isAvailable(): Boolean {
        logger.info("FallbackProvider始终可用", file = "FallbackProvider.kt", function = "isAvailable", line = 0)
        return true
    }
    
    override suspend fun chat(request: LLMRequest): LLMResponse {
        logger.info("使用FallbackProvider处理请求", file = "FallbackProvider.kt", function = "chat", line = 0)
        
        return LLMResponse(
            content = "您好！我是DMP助手。当前正在使用本地模拟模式，所有厂商AI SDK均未配置。如需使用真实AI服务，请在设置中配置相应的API Key。",
            provider = name,
            model = "fallback-mock"
        )
    }
    
    override fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        logger.info("使用FallbackProvider处理流式请求", file = "FallbackProvider.kt", function = "chatStream", line = 0)
        
        emit(
            LLMResponse(
                content = "您好！我是DMP助手。当前正在使用本地模拟模式，所有厂商AI SDK均未配置。如需使用真实AI服务，请在设置中配置相应的API Key。",
                provider = name,
                model = "fallback-mock"
            )
        )
    }
}