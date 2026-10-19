package com.dmp.client.core

import android.content.Context
import com.dmp.client.local.LocalLLMEngine
import com.dmp.client.local.TFLiteLocalLLM
import com.dmp.client.models.DMPConfiguration
import com.dmp.client.models.DMPError
import com.dmp.client.models.LLMResponse
import com.dmp.client.models.Message
import com.dmp.client.models.ProviderStatus
import com.dmp.client.network.DMPServerClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Log

/**
 * DMP主客户端
 */
class DMPClient(
    private val context: Context,
    private val config: DMPConfiguration
) {
    private val serverClient = DMPServerClient(
        baseURL = config.serverURL,
        timeout = config.fallback.timeout
    )
    
    private var localEngine: LocalLLMEngine? = null
    
    init {
        // 初始化本地LLM引擎
        if (config.localLLM.enabled && config.localLLM.modelPath != null) {
            localEngine = TFLiteLocalLLM(context)
            
            // 异步加载模型
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    localEngine?.loadModel(config.localLLM.modelPath!!)
                } catch (e: Exception) {
                    Log.w("DMPClient", "Failed to load local model", e)
                }
            }
        }
    }
    
    /**
     * 非流式聊天(自动回退)
     */
    suspend fun chat(messages: List<Message>): LLMResponse {
        // 1. 优先尝试本地LLM
        localEngine?.let { engine ->
            if (engine.isModelLoaded) {
                try {
                    Log.d("DMPClient", "📱 Trying local LLM...")
                    val response = engine.chat(messages)
                    Log.d("DMPClient", "✅ Got response from local LLM")
                    return response
                } catch (e: Exception) {
                    Log.w("DMPClient", "⚠️ Local LLM failed, falling back to server", e)
                }
            }
        }
        
        // 2. 回退到服务端
        Log.d("DMPClient", "🌐 Trying server...")
        val response = serverClient.chat(messages)
        Log.d("DMPClient", "✅ Got response from server")
        return response
    }
    
    /**
     * 流式聊天(自动回退)
     */
    fun chatStream(messages: List<Message>): Flow<LLMResponse> = flow {
        // 1. 优先尝试本地LLM
        localEngine?.let { engine ->
            if (engine.isModelLoaded) {
                Log.d("DMPClient", "📱 Streaming from local LLM...")
                engine.chatStream(messages).collect { chunk ->
                    emit(chunk)
                }
                return@flow
            }
        }
        
        // 2. 回退到服务端
        Log.d("DMPClient", "🌐 Streaming from server...")
        serverClient.chatStream(messages).collect { chunk ->
            emit(chunk)
        }
    }
    
    /**
     * 获取提供者状态
     */
    suspend fun getProviderStatus(): List<ProviderStatus> {
        return serverClient.getProviderStatus()
    }
    
    /**
     * 健康检查
     */
    suspend fun healthCheck(): Boolean {
        // 检查本地模型
        if (config.localLLM.enabled) {
            localEngine?.let { engine ->
                if (engine.isModelLoaded) {
                    return true
                }
            }
        }
        
        // 检查服务端
        return try {
            serverClient.getProviderStatus()
            true
        } catch (e: Exception) {
            false
        }
    }
}