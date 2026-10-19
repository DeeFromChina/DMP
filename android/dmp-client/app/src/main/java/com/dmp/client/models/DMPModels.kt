package com.dmp.client.models

import kotlinx.serialization.Serializable

/**
 * 消息角色
 */
@Serializable
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * 对话消息
 */
@Serializable
data class Message(
    val role: MessageRole,
    val content: String,
    val name: String? = null
)

/**
 * LLM请求
 */
@Serializable
data class LLMRequest(
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val stream: Boolean = false,
    val model: String? = null,
    val preferredProvider: ProviderType? = null
)

/**
 * LLM响应
 */
@Serializable
data class LLMResponse(
    val content: String,
    val provider: String,
    val model: String,
    val usage: UsageInfo? = null,
    val finishReason: String = "stop"
) {
    @Serializable
    data class UsageInfo(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
}

/**
 * 提供者类型
 */
@Serializable
enum class ProviderType {
    LOCAL,
    MOBILE,
    CLOUD,
    ANY
}

/**
 * 提供者状态
 */
@Serializable
data class ProviderStatus(
    val name: String,
    val priority: Int,
    val available: Boolean,
    val type: ProviderType
)

/**
 * DMP错误
 */
sealed class DMPError : Exception(message) {
    data class NetworkError(val cause: Throwable) : DMPError("Network error: ${cause.message}")
    data class ServerError(val message: String) : DMPError("Server error: $message")
    data class ProviderNotAvailable(val name: String) : DMPError("Provider not available: $name")
    object Timeout : DMPError("Request timeout")
    object ModelNotLoaded : DMPError("Model not loaded")
    data class InvalidResponse(val message: String) : DMPError("Invalid response: $message")
    object AuthenticationFailed : DMPError("Authentication failed")
    object AllProvidersFailed : DMPError("All providers failed")
}

/**
 * DMP配置
 */
data class DMPConfiguration(
    val serverURL: String,
    val localLLM: LocalLLMConfig = LocalLLMConfig(),
    val fallback: FallbackConfig = FallbackConfig()
) {
    data class LocalLLMConfig(
        val enabled: Boolean = true,
        val modelPath: String? = null,
        val maxTokens: Int = 2048,
        val temperature: Double = 0.7
    )
    
    data class FallbackConfig(
        val enabled: Boolean = true,
        val strategy: FallbackStrategy = FallbackStrategy.PRIORITY,
        val timeout: Long = 30000,
        val maxRetries: Int = 3
    ) {
        enum class FallbackStrategy {
            PRIORITY,
            ROUND_ROBIN,
            RANDOM
        }
    }
}