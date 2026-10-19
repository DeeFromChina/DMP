package com.dmp.android.providers

import com.dmp.android.core.DMPError
import com.dmp.android.core.DMPErrorCode
import com.dmp.android.core.DMPLogger
import com.dmp.android.device.DeviceDetector
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
 * 本地LLM提供者
 * 
 * 根据手机型号和系统版本自动扫描本地LLM服务端口
 * 兼容 OpenAI API 格式
 */
class LocalLLMProvider(
    private val modelName: String = "local-model"
) : LLMProvider {
    
    override val name: String = "local"
    override val priority: Int = 1  // 最高优先级，优先使用本地服务
    
    private val logger = DMPLogger
    
    // 各品牌常见本地LLM服务端口
    private val brandPorts = mapOf(
        "huawei" to listOf(8080, 8081, 11434),      // 华为小艺
        "xiaomi" to listOf(8080, 8082, 11434),      // 小米小爱
        "oppo" to listOf(8080, 8083, 11434),        // OPPO 小布
        "vivo" to listOf(8080, 8084, 11434),        // vivo Jovi
        "samsung" to listOf(8080, 8085, 11434),     // 三星 Galaxy AI
        "google" to listOf(8080, 8086, 11434),      // Google Gemini Local
        "oneplus" to listOf(8080, 8087, 11434),     // 一加
        "realme" to listOf(8080, 8088, 11434),      // 真我
        "sony" to listOf(8080, 11434),              // Sony
        "lg" to listOf(8080, 11434),                // LG
        "motorola" to listOf(8080, 11434),          // Motorola
        "nokia" to listOf(8080, 11434)              // Nokia
    )
    
    // 通用端口列表
    private val commonPorts = listOf(8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 11434, 3000, 5000)
    
    private var discoveredURL: String? = null
    var scanLog: String = ""  // 扫描日志，供UI显示
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    override suspend fun isAvailable(): Boolean {
        return try {
            // 获取设备品牌
            val deviceType = DeviceDetector.detectDevice()
            val brand = deviceType.brand.lowercase()
            
            // 获取该品牌的端口列表
            val portsToScan = brandPorts[brand] ?: commonPorts
            
            scanLog += "  📡 扫描品牌: $brand\n"
            scanLog += "  🔍 扫描端口: ${portsToScan.joinToString(", ")}\n"
            
            logger.info("扫描本地LLM服务端口 (品牌: $brand)...", file = "LocalLLMProvider.kt", function = "isAvailable", line = 0)
            
            // 扫描端口
            for (port in portsToScan) {
                val url = "http://localhost:$port/v1/models"
                scanLog += "    - 检查端口 $port... "
                if (checkService(url)) {
                    discoveredURL = "http://localhost:$port"
                    scanLog += "✅ 发现服务!\n"
                    logger.info("发现本地LLM服务: $discoveredURL", file = "LocalLLMProvider.kt", function = "isAvailable", line = 0)
                    return true
                } else {
                    scanLog += "❌ 无响应\n"
                }
            }
            
            scanLog += "  ⚠️ 未发现可用的本地LLM服务\n"
            logger.warn("未发现可用的本地LLM服务", file = "LocalLLMProvider.kt", function = "isAvailable", line = 0)
            false
        } catch (e: Exception) {
            scanLog += "  ❌ 扫描失败: ${e.message}\n"
            logger.warn("扫描本地LLM服务失败: ${e.message}", file = "LocalLLMProvider.kt", function = "isAvailable", line = 0)
            false
        }
    }
    
    private suspend fun checkService(url: String): Boolean {
        return try {
            val httpReq = Request.Builder()
                .url(url)
                .get()
                .build()
            
            client.newCall(httpReq).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getBaseURL(): String {
        return discoveredURL ?: throw DMPError(
            code = DMPErrorCode.NoProviderAvailable,
            message = "本地LLM服务未运行，请先启动本地LLM服务"
        )
    }
    
    override suspend fun chat(request: LLMRequest): LLMResponse {
        val url = "${getBaseURL()}/v1/chat/completions"
        val jsonBody = JSONObject().apply {
            put("model", request.model ?: modelName)
            put("messages", request.messages.map { 
                JSONObject().apply {
                    put("role", it.role)
                    put("content", it.content)
                }
            })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
        }
        
        logger.info("请求本地LLM: $url, 模型: ${request.model ?: modelName}", file = "LocalLLMProvider.kt", function = "chat", line = 0)
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val httpReq = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        client.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                throw DMPError(
                    code = DMPErrorCode.NetworkTimeout,
                    message = "本地LLM服务返回错误: HTTP ${response.code}"
                )
            }
            
            val responseBody = response.body?.string() ?: throw DMPError(
                code = DMPErrorCode.InvalidResponse,
                message = "本地LLM服务返回空响应"
            )
            
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.getString("content")
            
            logger.info("本地LLM响应成功, 内容长度: ${content.length}", file = "LocalLLMProvider.kt", function = "chat", line = 0)
            
            return LLMResponse(
                content = content,
                provider = name,
                model = request.model ?: modelName
            )
        }
    }
    
    override fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        val url = "${getBaseURL()}/v1/chat/completions"
        val jsonBody = JSONObject().apply {
            put("model", request.model ?: modelName)
            put("messages", request.messages.map { 
                JSONObject().apply {
                    put("role", it.role)
                    put("content", it.content)
                }
            })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("stream", true)
        }
        
        logger.info("流式请求本地LLM: $url", file = "LocalLLMProvider.kt", function = "chatStream", line = 0)
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val httpReq = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        client.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                throw DMPError(
                    code = DMPErrorCode.NetworkTimeout,
                    message = "本地LLM服务返回错误: HTTP ${response.code}"
                )
            }
            
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") {
                            break
                        }
                        try {
                            val json = JSONObject(data)
                            val choices = json.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).getJSONObject("delta")
                                val content = delta.optString("content", "")
                                if (content.isNotEmpty()) {
                                    emit(LLMResponse(
                                        content = content,
                                        provider = name,
                                        model = request.model ?: modelName
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("解析流式响应失败: ${e.message}", file = "LocalLLMProvider.kt", function = "chatStream", line = 0)
                        }
                    }
                }
            }
        }
    }
}