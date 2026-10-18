package com.dmp.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dmp.android.BuildConfig
import com.dmp.android.R
import com.dmp.android.config.DMPConfigManager
import com.dmp.android.databinding.ActivityChatBinding
import com.dmp.android.core.DMPLogger
import com.dmp.android.core.DMPLogLevel
import com.dmp.android.device.DeviceDetector
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.Message
import com.dmp.android.providers.LocalLLMProvider
import com.dmp.android.router.LLMRouter
import com.dmp.android.server.DMPServer
import com.dmp.android.server.DMPServerHTTP
import com.dmp.android.service.DMPService
import com.dmp.android.service.DMPServiceManager
import kotlinx.coroutines.launch

/**
 * 聊天页面
 */
class ChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var server: DMPServer
    private var httpServer: DMPServerHTTP? = null
    private lateinit var configManager: DMPConfigManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化日志系统
        DMPLogger.configure(this, DMPLogLevel.DEBUG)
        DMPLogger.info("ChatActivity created, logging initialized")
        
        configManager = DMPConfigManager.getInstance(this)
        
        // 显示版本信息（最可靠的版本验证方式）
        val versionInfo = "版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        DMPLogger.info(versionInfo)
        
        // 显示设备信息
        val deviceType = DeviceDetector.detectDevice()
        val deviceInfo = DeviceDetector.getDeviceInfo()
        val recommendedProviders = DeviceDetector.getRecommendedProviders(deviceType)
        val toastMsg = "$versionInfo\n设备: ${deviceInfo["brand"]} ${deviceInfo["model"]}\n" +
                "系统: Android ${deviceInfo["release"]} (API ${deviceInfo["sdk"]})\n" +
                "推荐: ${recommendedProviders.joinToString(", ")}"
        Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
        
        DMPLogger.info("Device: ${deviceInfo["brand"]} ${deviceInfo["model"]}, Android ${deviceInfo["release"]}")
        DMPLogger.info("Recommended providers: ${recommendedProviders.joinToString(", ")}")
        
        setupRecyclerView()
        setupUI()
        initializeServer()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次返回页面时重新初始化服务器，确保使用最新配置
        // 但如果已经初始化过，只更新配置
        if (::server.isInitialized) {
            initializeServer()
        } else {
            initializeServer()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
    }
    
    private fun setupUI() {
        // 配置按钮
        binding.btnConfig.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
        
        // 确保输入框可以获取焦点并弹出键盘
        binding.editMessage.isFocusable = true
        binding.editMessage.isFocusableInTouchMode = true
        binding.editMessage.requestFocus()
        
        // 点击输入框时确保键盘弹出
        binding.editMessage.setOnClickListener {
            binding.editMessage.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.editMessage, InputMethodManager.SHOW_IMPLICIT)
        }
        
        // 发送按钮
        binding.btnSend.setOnClickListener {
            val message = binding.editMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.editMessage.text?.clear()
            }
        }
    }
    
    private fun initializeServer() {
        server = DMPServer(this)
        
        // 获取当前选中的云端厂商和模型
        val selectedProvider = configManager.selectedProvider
        val cloudEnabled = configManager.cloudEnabled
        
        DMPLogger.info("========== 初始化服务器配置 ==========")
        DMPLogger.info("cloudEnabled=$cloudEnabled")
        DMPLogger.info("selectedProvider=$selectedProvider")
        
        val cloudApiKey = if (cloudEnabled) {
            val key = configManager.getCloudApiKey(selectedProvider)
            DMPLogger.info("尝试获取 API Key: cloud_api_key_$selectedProvider")
            DMPLogger.info("获取结果: ${key?.take(3)}***")
            key
        } else {
            null
        }
        
        val cloudBaseUrl = configManager.getCloudBaseUrl(selectedProvider)
        val cloudModelName = configManager.getCloudSelectedModel(selectedProvider)
        val cloudApiType = configManager.getCloudApiType(selectedProvider)
        
        DMPLogger.info("cloudApiKey=${cloudApiKey?.take(3)}***")
        DMPLogger.info("cloudBaseUrl=$cloudBaseUrl")
        DMPLogger.info("cloudModelName=$cloudModelName")
        DMPLogger.info("cloudApiType=$cloudApiType")
        DMPLogger.info("======================================")
        
        server.start(
            tfliteModelPath = if (configManager.tfliteEnabled) configManager.tfliteModelPath else null,
            huaweiApiKey = if (configManager.huaweiEnabled) configManager.huaweiApiKey else null,
            xiaomiApiKey = if (configManager.xiaomiEnabled) configManager.xiaomiApiKey else null,
            oppoApiKey = if (configManager.oppoEnabled) configManager.oppoApiKey else null,
            vivoApiKey = if (configManager.vivoEnabled) configManager.vivoApiKey else null,
            cloudApiKey = cloudApiKey,
            cloudBaseURL = cloudBaseUrl,
            cloudModelName = cloudModelName,
            cloudApiType = cloudApiType
        )
        
        // 启动 HTTP 服务器，对外提供 OpenAI 兼容的 API
        httpServer?.stopServer()
        httpServer = DMPServerHTTP(
            context = this,
            router = server.getRouter()
        )
        val httpUrl = httpServer?.startServer()
        if (!httpUrl.isNullOrEmpty()) {
            DMPLogger.info("对外 HTTP 服务已启动: $httpUrl")
            DMPLogger.info("可用端点: /v1/chat/completions, /v1/chat/completions/stream, /v1/responses, /v1/responses/stream")
            
            // 注册到 ContentProvider，供第三方应用查询
            com.dmp.android.service.DMPServiceProvider.serverInstance = httpServer
            
            // 注册 Router 到 DMPServiceManager，供 AIDL 服务使用
            DMPServiceManager.setRouter(server.getRouter())
        }
    }
    
    private fun sendMessage(content: String) {
        // 添加用户消息
        adapter.addMessage(ChatMessage("user", content))
        binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
        
        // 添加助手消息占位
        adapter.addMessage(ChatMessage("assistant", "⏳ 正在处理..."))
        binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
        
        // 显示加载指示器
        binding.layoutLoading.visibility = View.VISIBLE
        
        // 发送请求
        lifecycleScope.launch {
            try {
                val messages = getChatHistory()
                val request = LLMRequest(
                    messages = messages,
                    temperature = configManager.temperature.toDouble(),
                    maxTokens = configManager.maxTokens
                )
                
                // 流式输出
                var fullResponse = ""
                server.getRouter().chatStream(request).collect { chunk ->
                    fullResponse += chunk.content
                    adapter.updateLastMessage(fullResponse)
                    binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                }
            } catch (e: Exception) {
                val errorMsg = "❌ 请求失败: ${e.message}"
                adapter.updateLastMessage(errorMsg)
                binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
            } finally {
                binding.layoutLoading.visibility = View.GONE
            }
        }
    }
    
    private fun getChatHistory(): List<Message> {
        // 获取聊天历史（这里简化处理，实际应该维护完整的对话历史）
        return listOf(
            Message(
                role = "user",
                content = adapter.messages.lastOrNull { it.role == "user" }?.content ?: ""
            )
        )
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // 打开配置页面
                startActivity(Intent(this, ConfigActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}