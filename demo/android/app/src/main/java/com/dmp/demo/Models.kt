package com.dmp.demo

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 2048,
    val model: String = "",
    val preferred_provider: String = "any"
)

@Serializable
data class ChatResponse(
    val content: String,
    val provider: String,
    val model: String,
    val usage: TokenUsage? = null,
    val finish_reason: String = "stop"
)

@Serializable
data class TokenUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

@Serializable
data class StreamChunk(
    val content: String,
    val provider: String,
    val model: String,
    val finish_reason: String? = null
)

@Serializable
data class HealthResponse(
    val status: String,
    val server_version: String,
    val supported_protocols: List<String>,
    val providers_count: Int
)