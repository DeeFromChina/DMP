package com.dmp.android.providers

import com.dmp.android.core.DMPError
import com.dmp.android.core.DMPErrorCode
import com.dmp.android.core.DMPLogger
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云端LLM提供者
 * 作为中转站，根据请求的 apiType 动态选择端点
 */
class CloudProvider(
    private val apiKey: String,
    private val baseURL: String = "https://api.openai.com/v1",
    private val modelName: String = "gpt-3.5-turbo"
) : LLMProvider {
    
    override val name: String = "cloud"
    override val priority: Int = 3
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    override suspend fun isAvailable(): Boolean {
        return apiKey.isNotEmpty()
    }
    
    override suspend fun chat(request: LLMRequest): LLMResponse {
        if (!isAvailable()) {
            throw DMPError(
                code = DMPErrorCode.NoProviderAvailable,
                message = "Cloud provider not available"
            )
        }
        
        // 根据请求中的 apiType 决定端点，默认 chat_completions
        val requestedApiType = request.apiType ?: "chat_completions"
        val endpoint = when (requestedApiType) {
            "chat_completions" -> "/chat/completions"
            "responses" -> "/responses"
            else -> "/chat/completions"
        }
        
        // 清理 baseURL：移除末尾的斜杠和任何已有的路径后缀
        val cleanBaseURL = baseURL
            .trim()
            .removeSuffix("/chat/completions")
            .removeSuffix("/responses")
            .removeSuffix("/chat")
            .removeSuffix("/")
        
        val url = "$cleanBaseURL$endpoint"
        
        DMPLogger.info("========== CloudProvider 调用 ==========")
        DMPLogger.info("baseURL=$baseURL")
        DMPLogger.info("finalUrl=$url")
        DMPLogger.info("apiKey=${apiKey.take(3)}***")
        DMPLogger.info("model=${request.model ?: modelName}")
        DMPLogger.info("messages=${request.messages.size}条")
        DMPLogger.info("========================================")
        
        val messagesArray = org.json.JSONArray()
        request.messages.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }
        
        val jsonBody = JSONObject().apply {
            put("model", request.model ?: modelName)
            put("messages", messagesArray)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
        }
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val httpReq = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        
        DMPLogger.info("发送请求: $url")
        DMPLogger.info("请求体: ${jsonBody.toString()}")
        
        client.newCall(httpReq).execute().use { response ->
            DMPLogger.info("HTTP 响应码: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                DMPLogger.error("HTTP 错误: ${response.code}, body=$errorBody")
                throw DMPError(
                    code = DMPErrorCode.NetworkTimeout,
                    message = "HTTP ${response.code}: $errorBody"
                )
            }
            
            val responseBody = response.body?.string() ?: throw DMPError(
                    code = DMPErrorCode.InvalidResponse,
                    message = "Empty response"
                )
            
            DMPLogger.info("响应内容长度: ${responseBody.length} 字符")
            DMPLogger.info("响应体内容: $responseBody")
            
            val json = JSONObject(responseBody)
            DMPLogger.info("响应 JSON keys: ${json.keys().asSequence().toList()}")
            
            // 尝试多种响应格式
            val content = when {
                // 格式 1: OpenAI Chat Completions - {"choices": [{"message": {"content": "..."}}]}
                json.has("choices") -> {
                    val choices = json.getJSONArray("choices")
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    message.getString("content")
                }
                // 格式 2: OpenAI Responses API - {"output": [{"content": [{"text": "..."}]}]}
                json.has("output") -> {
                    val output = json.getJSONArray("output")
                    if (output.length() > 0) {
                        val firstItem = output.getJSONObject(0)
                        if (firstItem.has("content")) {
                            val contentArray = firstItem.getJSONArray("content")
                            if (contentArray.length() > 0) {
                                contentArray.getJSONObject(0).getString("text")
                            } else {
                                throw DMPError(code = DMPErrorCode.InvalidResponse, message = "output.content 数组为空")
                            }
                        } else {
                            throw DMPError(code = DMPErrorCode.InvalidResponse, message = "output[0] 没有 content 字段")
                        }
                    } else {
                        throw DMPError(code = DMPErrorCode.InvalidResponse, message = "output 数组为空")
                    }
                }
                // 格式 3: 直接返回文本 - {"content": "..."}
                json.has("content") -> {
                    json.getString("content")
                }
                // 格式 4: 未知格式，打印所有 keys
                else -> {
                    DMPLogger.error("未知响应格式，所有 keys: ${json.keys().asSequence().toList()}")
                    throw DMPError(code = DMPErrorCode.InvalidResponse, message = "无法解析响应格式: ${json.keys().asSequence().toList()}")
                }
            }
            
            DMPLogger.info("解析成功: content=${content.take(50)}...")
            
            return LLMResponse(
                content = content,
                provider = name,
                model = request.model ?: modelName
            )
        }
    }
    
    override fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        // 在 IO 线程执行网络请求，避免 NetworkOnMainThreadException
        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            chat(request)
        }
        response.content.split(" ").forEach { word ->
            emit(LLMResponse(
                content = "$word ",
                provider = name,
                model = modelName
            ))
            delay(50)
        }
    }
}