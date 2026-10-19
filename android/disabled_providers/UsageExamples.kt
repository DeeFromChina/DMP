package com.dmp.android.example

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dmp.android.server.DMPServer

/**
 * DMP Android 使用示例
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var server: DMPServer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建MCP服务器
        server = DMPServer(this)
        
        // 启动服务
        server.start(
            tfliteModelPath = "models/llm-quantized.tflite",
            cloudApiKey = "sk-xxx"
        )
        
        Log.d("DMP", "服务器已启动")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}

// MARK: - 基础使用示例

fun basicUsageExample(context: android.content.Context) {
    // 创建MCP服务器
    val server = DMPServer(context)
    
    // 启动服务
    server.start(
        tfliteModelPath = "models/llm.tflite",
        cloudApiKey = "sk-xxx"
    )
    
    // 查看提供者状态
    kotlinx.coroutines.GlobalScope.launch {
        val status = server.getProviderStatus()
        status.forEach { (name, available) ->
            println("$name: ${if (available) "✅" else "❌"}")
        }
    }
}

// MARK: - 直接使用路由器

fun useRouterDirectly(context: android.content.Context) {
    import com.dmp.android.router.LLMRouter
    import com.dmp.android.models.*
    
    val router = LLMRouter(context)
    
    // 自动注册（根据设备型号）
    router.autoRegister(
        tfliteModelPath = "models/llm.tflite",
        cloudApiKey = "sk-xxx"
    )
    
    // 非流式聊天
    val request = LLMRequest(
        messages = listOf(
            Message(role = "system", content = "你是一个有帮助的助手"),
            Message(role = "user", content = "你好，请介绍一下你自己")
        )
    )
    
    kotlinx.coroutines.GlobalScope.launch {
        try {
            val response = router.chat(request)
            println("回复: ${response.content}")
            println("提供者: ${response.provider}")
            println("模型: ${response.model}")
        } catch (e: Exception) {
            println("错误: ${e.message}")
        }
    }
}

// MARK: - 流式输出

fun streamChatExample(context: android.content.Context) {
    import com.dmp.android.router.LLMRouter
    import com.dmp.android.models.*
    
    val router = LLMRouter(context)
    router.autoRegister(cloudApiKey = "sk-xxx")
    
    val request = LLMRequest(
        messages = listOf(Message(role = "user", content = "请讲一个简短的故事")),
        stream = true
    )
    
    kotlinx.coroutines.GlobalScope.launch {
        println("开始流式输出:")
        router.chatStream(request).collect { chunk ->
            print(chunk.content)
        }
        println("\n输出完成")
    }
}

// MARK: - ViewModel示例

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmp.android.models.*
import com.dmp.android.router.LLMRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(context: android.content.Context) : ViewModel() {
    
    private val router = LLMRouter(context)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        // 自动注册
        router.autoRegister(
            tfliteModelPath = "models/llm.tflite",
            cloudApiKey = "sk-xxx"
        )
    }
    
    fun sendMessage(content: String) {
        viewModelScope.launch {
            // 添加用户消息
            val userMessage = ChatMessage(MessageRole.USER, content)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userMessage,
                isStreaming = true
            )
            
            // 添加助手消息占位
            val assistantMessage = ChatMessage(MessageRole.ASSISTANT, "")
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage
            )
            
            // 流式输出
            try {
                val messages = _uiState.value.messages
                    .filter { it.role != MessageRole.SYSTEM }
                    .map { Message(it.role.name.lowercase(), it.content) }
                
                val request = LLMRequest(messages = messages)
                
                router.chatStream(request).collect { chunk ->
                    val messages = _uiState.value.messages.toMutableList()
                    val lastIndex = messages.lastIndex
                    messages[lastIndex] = messages[lastIndex].copy(
                        content = messages[lastIndex].content + chunk.content
                    )
                    _uiState.value = _uiState.value.copy(messages = messages)
                }
            } catch (e: Exception) {
                val messages = _uiState.value.messages.toMutableList()
                val lastIndex = messages.lastIndex
                messages[lastIndex] = messages[lastIndex].copy(
                    content = "错误: ${e.message}"
                )
                _uiState.value = _uiState.value.copy(messages = messages)
            } finally {
                _uiState.value = _uiState.value.copy(isStreaming = false)
            }
        }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false
)

enum class MessageRole {
    SYSTEM, USER, ASSISTANT
}

data class ChatMessage(
    val role: MessageRole,
    val content: String
)

// MARK: - 检查设备信息

fun checkDeviceInfo() {
    import com.dmp.android.device.DeviceDetector
    
    val deviceType = DeviceDetector.detectDeviceBrand()
    println("设备类型: $deviceType")
    
    val recommendedProviders = DeviceDetector.getRecommendedProviders(deviceType)
    println("推荐提供者: $recommendedProviders")
    
    val deviceInfo = DeviceDetector.getDeviceInfo()
    println("设备信息: $deviceInfo")
}

// MARK: - 手动注册提供者

fun manualProviderRegistration(context: android.content.Context) {
    import com.dmp.android.router.LLMRouter
    import com.dmp.android.providers.*
    
    val router = LLMRouter(context)
    
    // 手动注册特定提供者
    router.register(HuaweiProvider(context, "api-key"))
    router.register(TFLiteProvider(context, "models/llm.tflite"))
    router.register(CloudProvider(
        apiKey = "sk-xxx",
        baseURL = "https://api.openai.com/v1",
        modelName = "gpt-4"
    ))
}