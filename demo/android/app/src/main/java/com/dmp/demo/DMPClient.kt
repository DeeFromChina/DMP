package com.dmp.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DMPClient(
    private val baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun chat(request: ChatRequest): Result<ChatResult> {
        return try {
            val json = JSONObject().apply {
                put("messages", org.json.JSONArray().apply {
                    request.messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("temperature", request.temperature)
                put("max_tokens", request.max_tokens)
                put("model", request.model)
                put("preferred_provider", request.preferred_provider)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBody)
                val requestJson = json.toString(2)

                if (response.isSuccessful) {
                    Result.success(ChatResult(
                        request = requestJson,
                        response = responseJson.toString(2),
                        content = responseJson.getString("content"),
                        provider = responseJson.optString("provider", ""),
                        model = responseJson.optString("model", ""),
                        statusCode = response.code,
                        success = true
                    ))
                } else {
                    Result.success(ChatResult(
                        request = requestJson,
                        response = responseBody,
                        content = "",
                        provider = "",
                        model = "",
                        statusCode = response.code,
                        success = false,
                        error = "HTTP ${response.code}: $responseBody"
                    ))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun chatStream(request: ChatRequest): Flow<StreamEvent> = flow {
        try {
            val json = JSONObject().apply {
                put("messages", org.json.JSONArray().apply {
                    request.messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("temperature", request.temperature)
                put("max_tokens", request.max_tokens)
                put("model", request.model)
                put("preferred_provider", request.preferred_provider)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/stream")
                .post(requestBody)
                .header("Accept", "text/event-stream")
                .build()

            emit(StreamEvent.RequestSent(json.toString(2)))

            client.newCall(httpRequest).execute().use { response ->
                emit(StreamEvent.StatusReceived(response.code))

                response.body?.source()?.use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        if (line.startsWith("data: ")) {
                            val data = line.substring(6)
                            if (data == "[DONE]") {
                                emit(StreamEvent.Done)
                                break
                            }

                            try {
                                val chunkJson = JSONObject(data)
                                emit(StreamEvent.ChunkReceived(
                                    content = chunkJson.optString("content", ""),
                                    provider = chunkJson.optString("provider", ""),
                                    model = chunkJson.optString("model", ""),
                                    finishReason = if (chunkJson.has("finish_reason") && !chunkJson.isNull("finish_reason"))
                                        chunkJson.getString("finish_reason") else null
                                ))
                            } catch (e: Exception) {
                                // 忽略解析错误
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamEvent.Error(e.message ?: "Unknown error"))
        }
    }

    suspend fun checkHealth(): Result<String> {
        return try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ChatResult(
    val request: String,
    val response: String,
    val content: String,
    val provider: String,
    val model: String,
    val statusCode: Int,
    val success: Boolean,
    val error: String? = null
)

sealed class StreamEvent {
    data class RequestSent(val requestJson: String) : StreamEvent()
    data class StatusReceived(val statusCode: Int) : StreamEvent()
    data class ChunkReceived(
        val content: String,
        val provider: String,
        val model: String,
        val finishReason: String?
    ) : StreamEvent()
    object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}