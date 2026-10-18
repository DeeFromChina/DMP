package com.dmp.android.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import com.dmp.android.core.DMPLogger
import com.dmp.android.service.IDMPServer
import com.dmp.android.service.IStreamCallback
import com.dmp.android.service.LLMResponse
import com.dmp.android.service.Message

/**
 * DMP 客户端 SDK
 * 封装服务发现和 AIDL 调用，提供简单易用的 API
 * 
 * 使用示例：
 * ```
 * val client = DMPClient(context)
 * client.connect { success ->
 *     if (success) {
 *         // 非流式
 *         val response = client.chat(listOf(Message("user", "你好")))
 *         
 *         // 流式
 *         client.chatStream(
 *             messages = listOf(Message("user", "讲个故事")),
 *             onChunk = { chunk -> print(chunk) },
 *             onComplete = { println("完成") },
 *             onError = { error -> println("错误: $error") }
 *         )
 *     }
 * }
 * ```
 */
class DMPClient(private val context: Context) {
    
    companion object {
        private const val TAG = "DMPClient"
        const val DEFAULT_HTTP_PORT = 28473
        const val CONTENT_PROVIDER_AUTHORITY = "com.dmp.android.service"
    }
    
    private var server: IDMPServer? = null
    private var connected = false
    private var serviceConnection: ServiceConnection? = null
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = connected
    
    /**
     * 连接 DMP 服务
     */
    fun connect(callback: (Boolean) -> Unit) {
        DMPLogger.info("DMPClient 开始连接服务")
        
        val intent = Intent().apply {
            component = ComponentName(
                "com.dmp.android",
                "com.dmp.android.service.DMPService"
            )
        }
        
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                server = IDMPServer.Stub.asInterface(service)
                connected = true
                DMPLogger.info("DMPClient 连接成功")
                callback(true)
            }
            
            override fun onServiceDisconnected(name: ComponentName) {
                server = null
                connected = false
                DMPLogger.warn("DMPClient 连接断开")
            }
        }
        
        val bound = context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        if (!bound) {
            DMPLogger.error("DMPClient 绑定服务失败")
            callback(false)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        if (connected) {
            serviceConnection?.let {
                context.unbindService(it)
            }
            server = null
            connected = false
            DMPLogger.info("DMPClient 已断开连接")
        }
    }
    
    /**
     * 非流式聊天
     */
    fun chat(
        messages: List<Message>,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): LLMResponse? {
        if (!connected || server == null) {
            DMPLogger.error("未连接到 DMP 服务")
            return null
        }
        
        return try {
            server?.chat(messages, temperature, maxTokens)
        } catch (e: Exception) {
            DMPLogger.error("聊天调用失败: ${e.message}")
            null
        }
    }
    
    /**
     * 流式聊天
     */
    fun chatStream(
        messages: List<Message>,
        temperature: Double = 0.7,
        maxTokens: Int = 2048,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!connected || server == null) {
            onError("未连接到 DMP 服务")
            return
        }
        
        try {
            server?.chatStream(
                messages,
                temperature,
                maxTokens,
                object : IStreamCallback.Stub() {
                    override fun onChunk(content: String) {
                        onChunk(content)
                    }
                    
                    override fun onComplete() {
                        onComplete()
                    }
                    
                    override fun onError(error: String) {
                        onError(error)
                    }
                }
            )
        } catch (e: Exception) {
            DMPLogger.error("流式聊天调用失败: ${e.message}")
            onError(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 获取服务状态
     */
    fun getProviderStatus(): ProviderStatus? {
        return try {
            if (!connected || server == null) {
                return null
            }
            
            ProviderStatus(
                isRunning = server!!.isRunning,
                activeProvider = server!!.activeProvider,
                currentModel = server!!.currentModel
            )
        } catch (e: Exception) {
            DMPLogger.error("获取状态失败: ${e.message}")
            null
        }
    }
    
    /**
     * 通过 ContentProvider 查询 HTTP 服务地址
     */
    fun getHttpBaseUrl(): String {
        val uri = Uri.parse("content://$CONTENT_PROVIDER_AUTHORITY/config")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val portIndex = it.getColumnIndex("http_port")
                if (portIndex >= 0) {
                    val port = it.getInt(portIndex)
                    return "http://localhost:$port"
                }
            }
        }
        
        return "http://localhost:$DEFAULT_HTTP_PORT"
    }
    
    /**
     * 服务状态数据类
     */
    data class ProviderStatus(
        val isRunning: Boolean,
        val activeProvider: String,
        val currentModel: String
    )
}