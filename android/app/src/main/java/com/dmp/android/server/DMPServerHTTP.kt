package com.dmp.android.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.dmp.android.core.DMPLogger
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import com.dmp.android.models.Message
import com.dmp.android.router.LLMRouter
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

/**
 * DMP HTTP 服务器
 * 对外提供 OpenAI 兼容的 API 端点，让其他 App 可以调用 LLM
 * 
 * 端点列表：
 * - POST /v1/chat/completions（非流式）
 * - POST /v1/chat/completions/stream（流式 SSE）
 * - POST /v1/responses（非流式）
 * - POST /v1/responses/stream（流式 SSE）
 * - GET /health（健康检查）
 */
class DMPServerHTTP(
    private val context: Context,
    val router: LLMRouter,
    private val port: Int = 28473
) : NanoHTTPD("0.0.0.0", port) {

    companion object {
        private const val TAG = "DMPServerHTTP"
    }

    private var isRunning = false
    private var serverIp = "127.0.0.1"

    /**
     * 获取实际运行的端口
     */
    fun getActualPort(): Int = port

    /**
     * 获取服务器 IP 地址
     */
    fun getServerIp(): String = serverIp

    /**
     * 检查服务器是否运行中
     */
    fun isServerRunning(): Boolean = isRunning

    /**
     * 获取局域网 IP 地址
     */
    private fun getLocalIpAddress(): String {
        try {
            // 尝试获取 WiFi IP
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress
            if (ipInt != null && ipInt != 0) {
                val ip = InetAddress.getByName(
                    String.format(
                        "%d.%d.%d.%d",
                        (ipInt and 0xff),
                        (ipInt shr 8 and 0xff),
                        (ipInt shr 16 and 0xff),
                        (ipInt shr 24 and 0xff)
                    )
                ).hostAddress
                if (!ip.isNullOrEmpty()) {
                    return ip
                }
            }
        } catch (e: Exception) {
            DMPLogger.warn("获取 WiFi IP 失败: ${e.message}")
        }

        // 备用方案：遍历网络接口
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            DMPLogger.warn("获取网络接口失败: ${e.message}")
        }

        return "127.0.0.1"
    }

    /**
     * 启动 HTTP 服务器
     */
    fun startServer(): String {
        return try {
            // 获取局域网 IP
            serverIp = getLocalIpAddress()
            DMPLogger.info("服务器 IP: $serverIp")
            DMPLogger.info("绑定地址: 0.0.0.0 (IPv4)")

            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            isRunning = true
            val url = "http://$serverIp:$port"
            DMPLogger.info("DMP HTTP 服务器已启动: $url")
            DMPLogger.info("监听端口: $port, 协议: IPv4 + IPv6")
            url
        } catch (e: Exception) {
            DMPLogger.error("DMP HTTP 服务器启动失败: ${e.message}")
            ""
        }
    }

    /**
     * 停止 HTTP 服务器
     */
    fun stopServer() {
        if (isRunning) {
            super.stop()
            isRunning = false
            DMPLogger.info("DMP HTTP 服务器已停止")
        }
    }

    /**
     * 处理 HTTP 请求
     */
    override fun serve(session: IHTTPSession): Response {
        DMPLogger.info("=== serve() called ===")
        return try {
            val uri = session.uri
            val method = session.method
            val headers = session.headers
            DMPLogger.info("HTTP request received: $method $uri from ${session.remoteIpAddress}")
            DMPLogger.info("HTTP headers: $headers")

            val response = when {
                uri == "/v1/chat/completions" && method == Method.POST -> handleChatCompletions(session, stream = false)
                uri == "/v1/chat/completions/stream" && method == Method.POST -> handleChatCompletions(session, stream = true)
                uri == "/v1/responses" && method == Method.POST -> handleResponses(session, stream = false)
                uri == "/v1/responses/stream" && method == Method.POST -> handleResponses(session, stream = true)
                uri == "/v1/models" && method == Method.GET -> handleModels()
                uri == "/health" && method == Method.GET -> handleHealth()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("code", 404)
                            put("message", "Endpoint not found: $method $uri")
                        })
                    }.toString()
                )
            }
            DMPLogger.info("HTTP response sent: ${response.status} for $method $uri")
            response
        } catch (e: Exception) {
            DMPLogger.error("HTTP serve error: ${e.message}", throwable = e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("error", JSONObject().apply {
                        put("code", 500)
                        put("message", "Internal server error: ${e.message}")
                    })
                }.toString()
            )
        }
    }

    /**
     * 处理 /v1/chat/completions 和 /v1/chat/completions/stream
     */
    private fun handleChatCompletions(session: IHTTPSession, stream: Boolean): Response {
        return handleLLMRequest(session, apiType = "chat_completions", stream = stream)
    }

    /**
     * 处理 /v1/responses 和 /v1/responses/stream
     */
    private fun handleResponses(session: IHTTPSession, stream: Boolean): Response {
        return handleLLMRequest(session, apiType = "responses", stream = stream)
    }

    /**
     * 通用 LLM 请求处理
     */
    private fun handleLLMRequest(session: IHTTPSession, apiType: String, stream: Boolean): Response {
        DMPLogger.info("handleLLMRequest: starting, apiType=$apiType, stream=$stream")
        return try {
            DMPLogger.info("handleLLMRequest: calling parseRequestBody")
            val body = parseRequestBody(session)
            DMPLogger.info("handleLLMRequest: parseRequestBody success, calling buildLLMRequest")
            val request = buildLLMRequest(body, apiType)
            DMPLogger.info("handleLLMRequest: buildLLMRequest success, calling handler")

            if (stream) {
                handleStreamRequest(request, apiType)
            } else {
                handleNonStreamRequest(request, apiType)
            }
        } catch (e: Exception) {
            DMPLogger.error("handleLLMRequest: exception caught: ${e.message}", throwable = e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("error", JSONObject().apply {
                        put("code", 500)
                        put("message", e.message ?: "Unknown error")
                        put("api_type", apiType)
                    })
                }.toString()
            )
        }
    }

    /**
     * 非流式请求处理
     */
    private fun handleNonStreamRequest(request: LLMRequest, apiType: String): Response {
        return runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val response = router.chat(request)

                    // 根据 apiType 构建对应格式的响应
                    val openAIResponse = when (apiType) {
                        "chat_completions" -> buildChatCompletionsResponse(response)
                        "responses" -> buildResponsesResponse(response)
                        else -> buildChatCompletionsResponse(response)
                    }

                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        openAIResponse.toString()
                    )
                } catch (e: Exception) {
                    DMPLogger.error("LLM 调用失败: ${e.message}")
                    newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        JSONObject().apply {
                            put("error", JSONObject().apply {
                                put("code", 500)
                                put("message", e.message ?: "LLM request failed")
                                put("api_type", apiType)
                            })
                        }.toString()
                    )
                }
            }
        }
    }

    /**
     * 构建 Chat Completions 格式响应
     */
    private fun buildChatCompletionsResponse(response: LLMResponse): JSONObject {
        return JSONObject().apply {
            put("id", "dmp-${System.currentTimeMillis()}")
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000)
            put("model", response.model)
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", response.content)
                    })
                    put("finish_reason", response.finishReason)
                })
            })
            put("usage", JSONObject().apply {
                put("prompt_tokens", response.usage?.promptTokens ?: 0)
                put("completion_tokens", response.usage?.completionTokens ?: 0)
                put("total_tokens", response.usage?.totalTokens ?: 0)
            })
        }
    }

    /**
     * 构建 Responses API 格式响应
     */
    private fun buildResponsesResponse(response: LLMResponse): JSONObject {
        return JSONObject().apply {
            put("id", "resp_dmp_${System.currentTimeMillis()}")
            put("object", "response")
            put("created_at", System.currentTimeMillis() / 1000)
            put("model", response.model)
            put("output", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "message")
                    put("role", "assistant")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "output_text")
                            put("text", response.content)
                        })
                    })
                })
            })
            put("usage", JSONObject().apply {
                put("input_tokens", response.usage?.promptTokens ?: 0)
                put("output_tokens", response.usage?.completionTokens ?: 0)
                put("total_tokens", response.usage?.totalTokens ?: 0)
            })
            put("status", "completed")
        }
    }

    /**
     * 流式请求处理（真正的 SSE 流式）
     */
    private fun handleStreamRequest(request: LLMRequest, apiType: String): Response {
        val responseId = when (apiType) {
            "responses" -> "resp_dmp_${System.currentTimeMillis()}"
            else -> "dmp-${System.currentTimeMillis()}"
        }
        
        DMPLogger.info("handleStreamRequest: creating chunked stream response, apiType=$apiType")
        
        // 使用 BlockingQueue 实现真正的边生成边发送
        val queue = LinkedBlockingQueue<ByteArray?>()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        // 启动后台协程生成数据
        scope.launch {
            try {
                streamSSEData(request, apiType, responseId) { sseBytes ->
                    queue.offer(sseBytes)
                }
                // 发送结束标记
                queue.offer(null)
            } catch (e: Exception) {
                DMPLogger.error("handleStreamRequest: streaming error: ${e.message}")
                val errorSSE = "data: {\"error\":\"${e.message}\"}\n\n".toByteArray(Charsets.UTF_8)
                queue.offer(errorSSE)
                queue.offer(null)
            }
        }
        
        // 创建自定义 InputStream，从队列读取数据
        val streamInputStream = object : InputStream() {
            private var currentBuffer: ByteArray? = null
            private var currentPos = 0
            private var isClosed = false
            
            override fun read(): Int {
                if (isClosed) return -1
                
                while (true) {
                    // 如果当前缓冲区有数据，返回一个字节
                    if (currentBuffer != null && currentPos < currentBuffer!!.size) {
                        return currentBuffer!![currentPos++].toInt() and 0xFF
                    }
                    
                    // 当前缓冲区用完，从队列获取下一个
                    currentBuffer = queue.take()
                    currentPos = 0
                    
                    // null 表示结束
                    if (currentBuffer == null) {
                        isClosed = true
                        return -1
                    }
                    
                    // 如果获取到的缓冲区为空数组，也继续等待
                    if (currentBuffer!!.isEmpty()) continue
                }
            }
            
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (isClosed) return -1
                
                while (true) {
                    // 如果当前缓冲区有数据，拷贝到目标缓冲区
                    if (currentBuffer != null && currentPos < currentBuffer!!.size) {
                        val remaining = currentBuffer!!.size - currentPos
                        val toRead = minOf(length, remaining)
                        System.arraycopy(currentBuffer, currentPos, buffer, offset, toRead)
                        currentPos += toRead
                        return toRead
                    }
                    
                    // 当前缓冲区用完，从队列获取下一个
                    currentBuffer = queue.take()
                    currentPos = 0
                    
                    // null 表示结束
                    if (currentBuffer == null) {
                        isClosed = true
                        return -1
                    }
                    
                    // 如果获取到的缓冲区为空数组，也继续等待
                    if (currentBuffer!!.isEmpty()) continue
                }
            }
            
            override fun close() {
                isClosed = true
                scope.cancel()
                super.close()
            }
        }
        
        val response = newChunkedResponse(Status.OK, "text/event-stream", streamInputStream)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        return response
    }
    
    /**
     * 流式生成 SSE 数据，通过回调逐块返回
     */
    private fun streamSSEData(
        request: LLMRequest,
        apiType: String,
        responseId: String,
        onChunk: (ByteArray) -> Unit
    ) = runBlocking {
        withContext(Dispatchers.IO) {
            var finalModel = ""
            var finalUsage: LLMResponse.UsageInfo? = null
            
            // 发送 response.created 事件（仅 responses 类型）
            if (apiType == "responses") {
                val createdEvent = JSONObject().apply {
                    put("type", "response.created")
                    put("response", JSONObject().apply {
                        put("id", responseId)
                        put("object", "response")
                        put("created_at", System.currentTimeMillis() / 1000)
                        put("model", request.model)
                        put("status", "in_progress")
                    })
                }
                val sseHeader = "event: response.created\ndata: ${createdEvent.toString()}\n\n"
                onChunk(sseHeader.toByteArray(Charsets.UTF_8))
                
                // 发送 output_item.added
                val outputItemAdded = JSONObject().apply {
                    put("type", "response.output_item.added")
                    put("output_index", 0)
                    put("item", JSONObject().apply {
                        put("type", "message")
                        put("role", "assistant")
                        put("content", JSONArray())
                    })
                }
                val outputItemSSE = "event: response.output_item.added\ndata: ${outputItemAdded.toString()}\n\n"
                onChunk(outputItemSSE.toByteArray(Charsets.UTF_8))
                
                // 发送 content_part.added
                val contentPartAdded = JSONObject().apply {
                    put("type", "response.content_part.added")
                    put("output_index", 0)
                    put("content_index", 0)
                    put("part", JSONObject().apply {
                        put("type", "output_text")
                        put("text", "")
                    })
                }
                val contentPartSSE = "event: response.content_part.added\ndata: ${contentPartAdded.toString()}\n\n"
                onChunk(contentPartSSE.toByteArray(Charsets.UTF_8))
            }
            
            // 收集流式数据
            router.chatStream(request).collect { chunk ->
                finalModel = chunk.model
                finalUsage = chunk.usage
                
                val sseChunk = when (apiType) {
                    "chat_completions" -> {
                        val chunkJson = JSONObject().apply {
                            put("id", responseId)
                            put("object", "chat.completion.chunk")
                            put("created", System.currentTimeMillis() / 1000)
                            put("model", finalModel)
                            put("choices", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("index", 0)
                                    put("delta", JSONObject().apply {
                                        put("content", chunk.content)
                                    })
                                })
                            })
                        }
                        "data: ${chunkJson.toString()}\n\n"
                    }
                    "responses" -> {
                        val textDelta = JSONObject().apply {
                            put("type", "response.content_text.delta")
                            put("delta", chunk.content)
                            put("output_index", 0)
                            put("content_index", 0)
                        }
                        "event: response.content_text.delta\ndata: ${textDelta.toString()}\n\n"
                    }
                    else -> {
                        val chunkJson = JSONObject().apply {
                            put("id", responseId)
                            put("object", "chat.completion.chunk")
                            put("created", System.currentTimeMillis() / 1000)
                            put("model", finalModel)
                            put("choices", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("index", 0)
                                    put("delta", JSONObject().apply {
                                        put("content", chunk.content)
                                    })
                                })
                            })
                        }
                        "data: ${chunkJson.toString()}\n\n"
                    }
                }
                onChunk(sseChunk.toByteArray(Charsets.UTF_8))
            }
            
            // 发送完成事件（仅 responses 类型）
            if (apiType == "responses") {
                val completedEvent = JSONObject().apply {
                    put("type", "response.completed")
                    put("response", JSONObject().apply {
                        put("id", responseId)
                        put("object", "response")
                        put("created_at", System.currentTimeMillis() / 1000)
                        put("model", finalModel)
                        put("status", "completed")
                        put("usage", JSONObject().apply {
                            put("input_tokens", finalUsage?.promptTokens ?: 0)
                            put("output_tokens", finalUsage?.completionTokens ?: 0)
                            put("total_tokens", finalUsage?.totalTokens ?: 0)
                        })
                    })
                }
                val completedSSE = "event: response.completed\ndata: ${completedEvent.toString()}\n\n"
                onChunk(completedSSE.toByteArray(Charsets.UTF_8))
            }
            
            // 添加 [DONE] 标记
            onChunk("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
            
            DMPLogger.info("streamSSEData: stream complete for apiType=$apiType")
        }
    }

    /**
     * 获取可用模型列表
     */
    private fun handleModels(): Response {
        val modelsArray = JSONArray()
        
        // 获取云端配置的模型
        val configManager = com.dmp.android.config.DMPConfigManager.getInstance(context)
        val selectedProvider = configManager.selectedProvider
        val configuredModels = configManager.getCloudModels(selectedProvider)
        val selectedModel = configManager.getCloudSelectedModel(selectedProvider)
        
        // 如果有配置的模型，添加到列表
        if (configuredModels.isNotEmpty()) {
            configuredModels.forEach { modelName ->
                modelsArray.put(JSONObject().apply {
                    put("id", modelName)
                    put("object", "model")
                    put("created", System.currentTimeMillis() / 1000)
                    put("owned_by", selectedProvider.lowercase())
                    put("is_selected", modelName == selectedModel)
                })
            }
        } else {
            // 没有配置模型，使用默认
            modelsArray.put(JSONObject().apply {
                put("id", configManager.cloudModelName)
                put("object", "model")
                put("created", System.currentTimeMillis() / 1000)
                put("owned_by", "openai")
                put("is_selected", true)
            })
        }
        
        // 添加 tflite 本地模型（如果启用）
        if (configManager.tfliteEnabled) {
            modelsArray.put(JSONObject().apply {
                put("id", "tflite-local")
                put("object", "model")
                put("created", System.currentTimeMillis() / 1000)
                put("owned_by", "local")
                put("is_selected", false)
            })
        }
        
        val response = JSONObject().apply {
            put("object", "list")
            put("data", modelsArray)
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    /**
     * 健康检查
     */
    private fun handleHealth(): Response {
        val status = JSONObject().apply {
            put("status", "ok")
            put("service", "DMP HTTP Server")
            put("endpoints", JSONArray().apply {
                put("/v1/chat/completions")
                put("/v1/chat/completions/stream")
                put("/v1/responses")
                put("/v1/responses/stream")
                put("/v1/models")
                put("/health")
            })
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            status.toString()
        )
    }

    /**
     * 解析请求体
     */
    private fun parseRequestBody(session: IHTTPSession): JSONObject {
        DMPLogger.info("parseRequestBody: starting to read body")
        val contentLength = session.headers["content-length"]
        DMPLogger.info("parseRequestBody: content-length=$contentLength")
        DMPLogger.info("parseRequestBody: content-type=${session.headers["content-type"]}")
        
        // 尝试 parseBody 方法
        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
            DMPLogger.info("parseRequestBody: parseBody success, files keys=${files.keys}")
        } catch (e: Exception) {
            DMPLogger.warn("parseRequestBody: parseBody failed: ${e.message}")
        }
        
        // 如果 parseBody 返回了 postData，使用它
        val bodyFromParseBody = files["postData"]
        if (!bodyFromParseBody.isNullOrEmpty()) {
            DMPLogger.info("parseRequestBody: using body from parseBody, length=${bodyFromParseBody.length}")
            return JSONObject(bodyFromParseBody)
        }
        
        // 否则直接从 inputStream 读取
        DMPLogger.info("parseRequestBody: reading from inputStream directly")
        val contentLengthInt = contentLength?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLengthInt)
        var bytesRead = 0
        var totalBytesRead = 0
        val inputStream = session.inputStream
        
        while (totalBytesRead < contentLengthInt && bytesRead != -1) {
            bytesRead = inputStream.read(buffer, totalBytesRead, contentLengthInt - totalBytesRead)
            if (bytesRead > 0) {
                totalBytesRead += bytesRead
            }
        }
        
        val body = String(buffer, 0, totalBytesRead, Charsets.UTF_8)
        DMPLogger.info("parseRequestBody: body length=${body.length}, totalBytesRead=$totalBytesRead")
        DMPLogger.info("parseRequestBody: body preview=${body.take(100)}")
        return JSONObject(body)
    }

    /**
     * 构建 LLMRequest
     */
    private fun buildLLMRequest(json: JSONObject, apiType: String): LLMRequest {
        val messagesArray = json.getJSONArray("messages")
        val messages = mutableListOf<Message>()
        for (i in 0 until messagesArray.length()) {
            val msg = messagesArray.getJSONObject(i)
            messages.add(Message(
                role = msg.getString("role"),
                content = msg.getString("content")
            ))
        }

        return LLMRequest(
            messages = messages,
            temperature = json.optDouble("temperature", 0.7),
            maxTokens = json.optInt("max_tokens", 2048),
            model = if (json.has("model")) json.getString("model") else null,
            apiType = apiType
        )
    }
}