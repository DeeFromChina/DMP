package com.dmp.android.server

import android.content.Context
import android.util.Log
import com.dmp.android.models.*
import com.dmp.android.router.LLMRouter
import kotlinx.coroutines.runBlocking

/**
 * DMP MCP服务器
 */
class DMPServer(private val context: Context) {
    
    private val router = LLMRouter(context)
    private var isRunning = false
    
    companion object {
        private const val TAG = "DMPServer"
    }
    
    /**
     * 启动MCP服务器
     */
    fun start(
        tfliteModelPath: String? = null,
        huaweiApiKey: String? = null,
        xiaomiApiKey: String? = null,
        oppoApiKey: String? = null,
        vivoApiKey: String? = null,
        cloudApiKey: String? = null,
        cloudBaseURL: String? = null,
        cloudModelName: String? = null,
        cloudApiType: String? = null
    ) {
        Log.d(TAG, "🚀 启动DMP MCP服务器...")
        
        // 自动注册提供者
        router.autoRegister(
            tfliteModelPath = tfliteModelPath,
            huaweiApiKey = huaweiApiKey,
            xiaomiApiKey = xiaomiApiKey,
            oppoApiKey = oppoApiKey,
            vivoApiKey = vivoApiKey,
            cloudApiKey = cloudApiKey,
            cloudBaseURL = cloudBaseURL,
            cloudModelName = cloudModelName,
            cloudApiType = cloudApiType
        )
        
        isRunning = true
        Log.d(TAG, "✅ DMP MCP服务器已启动")
    }
    
    /**
     * 停止MCP服务器
     */
    fun stop() {
        isRunning = false
        Log.d(TAG, "🛑 DMP MCP服务器已停止")
    }
    
    /**
     * 处理MCP请求
     */
    fun handleMCPRequest(request: MCPMessage): MCPMessage {
        return runBlocking {
            when (request.method) {
                "tools/call" -> handleToolCall(request)
                "tools/list" -> handleToolsList(request)
                "initialize" -> handleInitialize(request)
                else -> MCPMessage(
                    method = request.method,
                    id = request.id,
                    error = MCPError(code = -32601, message = "Method not found: ${request.method}")
                )
            }
        }
    }
    
    /**
     * 获取路由器实例
     */
    fun getRouter(): LLMRouter {
        return router
    }
    
    /**
     * 获取提供者状态
     */
    suspend fun getProviderStatus(): Map<String, Boolean> {
        return router.getProviderStatus()
    }
    
    // MARK: - Private Methods
    
    private suspend fun handleToolCall(request: MCPMessage): MCPMessage {
        val params = request.params
        if (params?.arguments == null) {
            return MCPMessage(
                method = request.method,
                id = request.id,
                error = MCPError(code = -32602, message = "Invalid params")
            )
        }
        
        return try {
            val response = router.chat(params.arguments!!)
            
            MCPMessage(
                method = request.method,
                id = request.id,
                result = MCPResult(
                    content = response.content,
                    provider = response.provider,
                    model = response.model
                )
            )
        } catch (e: Exception) {
            MCPMessage(
                method = request.method,
                id = request.id,
                error = MCPError(code = -1, message = e.message ?: "Unknown error")
            )
        }
    }
    
    private fun handleToolsList(request: MCPMessage): MCPMessage {
        return MCPMessage(
            method = request.method,
            id = request.id,
            result = MCPResult(
                content = "",
                provider = "dmp",
                model = "dmp-server"
            )
        )
    }
    
    private fun handleInitialize(request: MCPMessage): MCPMessage {
        return MCPMessage(
            method = request.method,
            id = request.id,
            result = MCPResult(
                content = "DMP MCP Server initialized",
                provider = "dmp",
                model = "dmp-server"
            )
        )
    }
}