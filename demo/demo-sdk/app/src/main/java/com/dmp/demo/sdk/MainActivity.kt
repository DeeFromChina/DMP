package com.dmp.demo.sdk

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dmp.demo.sdk.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DMP SDK Demo - 通过 AIDL 绑定 DMP 服务
 * 
 * 特点：
 * - 通过 ContentProvider 自动发现 DMP 服务
 * - 使用 AIDL 绑定服务（高性能 IPC）
 * - 支持非流式和流式调用
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<ChatMessage>()
    
    // AIDL 服务连接
    private var aidlService: com.dmp.android.service.IDMPServer? = null
    private var serviceConnected = false
    private var serviceConnection: ServiceConnection? = null
    
    // HTTP 客户端（备用）
    private var httpBaseUrl: String = "http://localhost:28473"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(this)
        Logger.info("onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化 RecyclerView
        adapter = MessageAdapter()
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
        
        // 自动发现并连接 DMP 服务
        connectDMPService()
        
        // 发送按钮
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendMessage(content)
            binding.etMessage.text.clear()
        }
        
        // 清空按钮
        binding.btnClear.setOnClickListener {
            messages.clear()
            adapter.messages.clear()
            adapter.notifyDataSetChanged()
        }
        
        // 健康检查
        binding.btnHealth.setOnClickListener {
            checkHealth()
        }
        
        // 切换流式/非流式
        binding.switchStream.setOnCheckedChangeListener { _, isChecked ->
            val modeText = if (isChecked) "流式模式" else "非流式模式"
            Toast.makeText(this, "已切换到${modeText}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 连接 DMP 服务
     */
    private fun connectDMPService() {
        Logger.info("connectDMPService: starting")
        binding.tvStatus.text = "正在连接 DMP 服务..."
        
        // 1. 先尝试通过 ContentProvider 获取 HTTP 端口
        try {
            Logger.info("Querying ContentProvider: content://com.dmp.android.service/config")
            val uri = Uri.parse("content://com.dmp.android.service/config")
            val cursor = contentResolver.query(uri, null, null, null, null)
            Logger.info("ContentProvider query result: cursor=${cursor != null}")
            cursor?.use {
                Logger.info("Cursor column count: ${it.columnCount}")
                Logger.info("Cursor column names: ${it.columnNames.joinToString(", ")}")
                if (it.moveToFirst()) {
                    val portIndex = it.getColumnIndex("http_port")
                    val ipIndex = it.getColumnIndex("http_ip")
                    Logger.info("http_port column index: $portIndex")
                    Logger.info("http_ip column index: $ipIndex")
                    if (portIndex >= 0) {
                        val port = it.getInt(portIndex)
                        val ip = if (ipIndex >= 0) it.getString(ipIndex) else "127.0.0.1"
                        httpBaseUrl = "http://$ip:$port"
                        Logger.info("Found HTTP port: $port, ip=$ip, httpBaseUrl=$httpBaseUrl")
                    }
                } else {
                    Logger.warn("Cursor is empty, no data returned")
                }
            }
        } catch (e: Exception) {
            Logger.error("ContentProvider query failed", e)
        }
        
        // 2. 尝试绑定 AIDL 服务
        Logger.info("Attempting to bind AIDL service: com.dmp.android.service.DMPService")
        val intent = Intent().apply {
            component = ComponentName(
                "com.dmp.android",
                "com.dmp.android.service.DMPService"
            )
        }
        
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Logger.info("AIDL service connected")
                aidlService = com.dmp.android.service.IDMPServer.Stub.asInterface(service)
                serviceConnected = true
                binding.tvStatus.text = "已连接 (AIDL): $httpBaseUrl"
                Toast.makeText(this@MainActivity, "DMP 服务已连接 (AIDL)", Toast.LENGTH_SHORT).show()
            }
            
            override fun onServiceDisconnected(name: ComponentName) {
                Logger.warn("AIDL service disconnected")
                aidlService = null
                serviceConnected = false
                binding.tvStatus.text = "服务已断开，使用 HTTP 备用"
            }
        }
        
        val bound = bindService(intent, serviceConnection!!, BIND_AUTO_CREATE)
        Logger.info("bindService result: bound=$bound")
        if (!bound) {
            Logger.warn("AIDL bind failed, fallback to HTTP: $httpBaseUrl")
            binding.tvStatus.text = "AIDL 绑定失败，使用 HTTP: $httpBaseUrl"
            Toast.makeText(this, "AIDL 不可用，将使用 HTTP 调用", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 检查服务健康状态
     */
    private fun checkHealth() {
        if (serviceConnected && aidlService != null) {
            val running = try {
                aidlService!!.isRunning
            } catch (e: Exception) {
                false
            }
            Toast.makeText(this, if (running) "AIDL 服务运行中" else "AIDL 服务未运行", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "使用 HTTP 备用: $httpBaseUrl", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 发送消息
     */
    private fun sendMessage(content: String) {
        val userMessage = ChatMessage("user", content)
        messages.add(userMessage)
        adapter.addMessage(userMessage)
        
        val assistantMessage = ChatMessage("assistant", "⏳ 正在处理...")
        messages.add(assistantMessage)
        adapter.addMessage(assistantMessage)
        
        binding.layoutLoading.visibility = View.VISIBLE
        
        if (binding.switchStream.isChecked) {
            sendStreamMessage(content, assistantMessage)
        } else {
            sendNormalMessage(content, assistantMessage)
        }
    }
    
    /**
     * 非流式发送
     */
    private fun sendNormalMessage(content: String, assistantMessage: ChatMessage) {
        lifecycleScope.launch {
            try {
                if (serviceConnected && aidlService != null) {
                    // 使用 AIDL 调用
                    val aidlMessages = messages
                        .filter { it.role != "assistant" || it.content != "⏳ 正在处理..." }
                        .map { com.dmp.android.service.Message(it.role, it.content) }
                    
                    val response = withContext(Dispatchers.IO) {
                        aidlService!!.chat(aidlMessages, 0.7, 2048)
                    }
                    
                    withContext(Dispatchers.Main) {
                        binding.layoutLoading.visibility = View.GONE
                        assistantMessage.content = response?.content ?: "无响应"
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    // 使用 HTTP 调用
                    val jsonBody = JSONObject().apply {
                        put("messages", JSONArray().apply {
                            messages.filter { it.role != "assistant" || it.content != "⏳ 正在处理..." }.forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("content", msg.content)
                                })
                            }
                        })
                        put("temperature", 0.7)
                        put("max_tokens", 2048)
                    }.toString()
                    
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    
                    val request = okhttp3.Request.Builder()
                        .url("$httpBaseUrl/v1/chat/completions")
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()
                    
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    
                    withContext(Dispatchers.Main) {
                        binding.layoutLoading.visibility = View.GONE
                        
                        if (response.isSuccessful) {
                            val json = JSONObject(body)
                            val choices = json.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val message = choices.getJSONObject(0).getJSONObject("message")
                                assistantMessage.content = message.getString("content")
                                adapter.notifyDataSetChanged()
                            }
                        } else {
                            assistantMessage.content = "❌ 请求失败: ${response.code}"
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    assistantMessage.content = "❌ 错误: ${e.message}"
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
    
    /**
     * 流式发送
     */
    private fun sendStreamMessage(content: String, assistantMessage: ChatMessage) {
        lifecycleScope.launch {
            try {
                if (serviceConnected && aidlService != null) {
                    // 使用 AIDL 流式调用
                    val aidlMessages = messages
                        .filter { it.role != "assistant" || it.content != "⏳ 正在处理..." }
                        .map { com.dmp.android.service.Message(it.role, it.content) }
                    
                    val fullContent = StringBuilder()
                    
                    withContext(Dispatchers.IO) {
                        aidlService!!.chatStream(aidlMessages, 0.7, 2048, object : com.dmp.android.service.IStreamCallback.Stub() {
                            override fun onChunk(chunk: String) {
                                fullContent.append(chunk)
                                lifecycleScope.launch(Dispatchers.Main) {
                                    assistantMessage.content = fullContent.toString()
                                    adapter.notifyDataSetChanged()
                                    binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                                }
                            }
                            
                            override fun onComplete() {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    binding.layoutLoading.visibility = View.GONE
                                }
                            }
                            
                            override fun onError(error: String) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    binding.layoutLoading.visibility = View.GONE
                                    assistantMessage.content = "❌ 错误: $error"
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        })
                    }
                } else {
                    // HTTP 流式调用（简化版，暂不支持真正的 SSE）
                    sendNormalMessage(content, assistantMessage)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    assistantMessage.content = "❌ 错误: ${e.message}"
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.let {
            try {
                unbindService(it)
            } catch (e: Exception) {
                // 忽略
            }
        }
    }
}