package com.dmp.android.models

import com.dmp.android.core.DMPError
import com.dmp.android.core.DMPErrorCode
import kotlinx.serialization.Serializable

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
    val apiType: String? = null  // 调用方指定接口类型：chat_completions / responses
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
    val finishReason: String = "stop",
    val warnings: List<String> = emptyList()
) {
    @Serializable
    data class UsageInfo(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
}

/**
 * 消息
 */
@Serializable
data class Message(
    val role: String,
    val content: String
)

/**
 * MCP协议消息
 */
@Serializable
data class MCPMessage(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: MCPParams? = null,
    val id: Int? = null,
    val result: MCPResult? = null,
    val error: MCPError? = null
)

@Serializable
data class MCPParams(
    val name: String? = null,
    val arguments: LLMRequest? = null
)

@Serializable
data class MCPResult(
    val content: String,
    val provider: String,
    val model: String
)

@Serializable
data class MCPError(
    val code: Int,
    val message: String
)