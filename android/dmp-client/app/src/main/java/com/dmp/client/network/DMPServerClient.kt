package com.dmp.client.network

import com.dmp.client.models.DMPError
import com.dmp.client.models.LLMRequest
import com.dmp.client.models.LLMResponse
import com.dmp.client.models.Message
import com.dmp.client.models.ProviderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * DMP服务端通信客户端
 */
class DMPServerClient(
    baseURL: String,
    timeout: Long = 30000
) {
    private val baseUrl = baseURL.removeSuffix("/")
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeout, TimeUnit.MILLISECONDS)
        .readTimeout(timeout, TimeUnit.MILLISECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    /**
     * 非流式聊天
     */
    suspend fun chat(messages: List<Message>): LLMResponse {
        val request = LLMRequest(messages = messages, stream = false)
        return sendMCPRequest(request)
    }
    
    /**
     * 流式聊天
     */
    fun chatStream(messages: List<Message>): Flow<LLMResponse> = flow {
        val request = LLMRequest(messages = messages, stream = true)
        sendSSEStream(request).collect { chunk ->
            emit(chunk)
        }
    }
    
    /**
     * 获取提供者状态
     */
    suspend fun getProviderStatus(): List<ProviderStatus> {
        val request = Request.Builder()
            .url("$baseUrl/status")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DMPError.ServerError("HTTP ${response.code}")
            }
            
            val body = response.body?.string() ?: throw DMPError.InvalidResponse("Empty response")
            return json.decodeFromString<List<ProviderStatus>>(body)
        }
    }
    
    private suspend fun sendMCPRequest(request: LLMRequest): LLMResponse {
        val jsonBody = json.encodeToString(LLMRequest.serializer(), request)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        
        val httpReq = Request.Builder()
            .url("$baseUrl/v1/chat")
            .post(requestBody)
            .build()
        
        client.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                throw DMPError.ServerError("HTTP ${response.code}")
            }
            
            val body = response.body?.string() ?: throw DMPError.InvalidResponse("Empty response")
            return json.decodeFromString(LLMResponse.serializer(), body)
        }
    }
    
    private fun sendSSEStream(request: LLMRequest): Flow<LLMResponse> = flow {
        val jsonBody = json.encodeToString(LLMRequest.serializer(), request)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        
        val httpReq = Request.Builder()
            .url("$baseUrl/v1/chat/stream")
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .build()
        
        client.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                throw DMPError.ServerError("Failed to connect to stream")
            }
            
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    
                    if (line.startsWith("data: ")) {
                        val jsonData = line.substring(6)
                        if (jsonData == "[DONE]") {
                            break
                        }
                        
                        try {
                            val chunk = json.decodeFromString(LLMResponse.serializer(), jsonData)
                            emit(chunk)
                        } catch (e: Exception) {
                            // 忽略解析错误,继续处理
                        }
                    }
                }
            }
        }
    }
}