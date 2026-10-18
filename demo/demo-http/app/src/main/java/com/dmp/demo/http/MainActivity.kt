package com.dmp.demo.http

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dmp.demo.http.databinding.ActivityMainBinding
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
 * DMP HTTP Demo - 通过 HTTP API 调用 DMP 服务
 * 
 * 特点：
 * - 通过 ContentProvider 自动发现 DMP 服务端口
 * - 使用 OkHttp 发送 HTTP 请求
 * - 支持非流式和流式调用
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private var baseUrl: String = "http://localhost:28473"
    private val messages = mutableListOf<ChatMessage>()
    private val modelList = mutableListOf<String>()
    private var selectedModel: String = ""
    private val modelAdapter by lazy {
        ArrayAdapter(this, android.R.layout.simple_spinner_item, modelList)
    }

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
        
        // 自动发现 DMP 服务
        discoverDMPService()
        
        // 初始化模型选择器
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerModel.adapter = modelAdapter
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedModel = modelList[position]
                Logger.info("Selected model: $selectedModel")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        // 刷新模型按钮
        binding.btnRefreshModels.setOnClickListener {
            fetchModels()
        }
        
        // 自动获取模型列表
        fetchModels()
        
        // 发送按钮
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendMessage(content)
            binding.etMessage.text?.clear()
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
     * 通过 ContentProvider 自动发现 DMP 服务
     */
    private fun discoverDMPService() {
        Logger.info("discoverDMPService: querying content://com.dmp.android.service/config")
        try {
            val uri = android.net.Uri.parse("content://com.dmp.android.service/config")
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
                        val isRunningIndex = it.getColumnIndex("is_running")
                        val isRunning = if (isRunningIndex >= 0) it.getInt(isRunningIndex) == 1 else false
                        baseUrl = "http://$ip:$port"
                        Logger.info("Found DMP service: ip=$ip, port=$port, isRunning=$isRunning, baseUrl=$baseUrl")
                        val statusText = if (isRunning) "已连接" else "服务未运行"
                        Toast.makeText(this, "已发现 DMP 服务: $baseUrl ($statusText)", Toast.LENGTH_LONG).show()
                        binding.tvStatus.text = "$statusText: $baseUrl"
                        return
                    }
                } else {
                    Logger.warn("Cursor is empty, no data returned")
                }
            }
        } catch (e: Exception) {
            Logger.error("ContentProvider query failed", e)
        }
        
        Logger.warn("DMP service not found, using default port 28473")
        binding.tvStatus.text = "未找到 DMP 服务: $baseUrl"
        Toast.makeText(this, "未找到 DMP 服务，使用默认端口 28473", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 获取可用模型列表
     */
    private fun fetchModels() {
        Logger.info("fetchModels: url=$baseUrl/v1/models")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/models")
                    .get()
                    .build()
                
                Logger.info("fetchModels: sending request")
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Logger.info("fetchModels: response code=${response.code}, body=$body")
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val data = json.getJSONArray("data")
                        modelList.clear()
                        var selectedIndex = 0
                        
                        for (i in 0 until data.length()) {
                            val model = data.getJSONObject(i)
                            val modelId = model.getString("id")
                            val isSelected = model.optBoolean("is_selected", false)
                            modelList.add(modelId)
                            if (isSelected) {
                                selectedIndex = i
                            }
                        }
                        
                        if (modelList.isEmpty()) {
                            modelList.add("gpt-3.5-turbo")
                        }
                        
                        selectedModel = modelList[selectedIndex]
                        modelAdapter.notifyDataSetChanged()
                        binding.spinnerModel.setSelection(selectedIndex)
                        
                        Logger.info("fetchModels: success, models=${modelList.joinToString(", ")}")
                        Toast.makeText(this@MainActivity, "已获取 ${modelList.size} 个模型", Toast.LENGTH_SHORT).show()
                    } else {
                        Logger.warn("fetchModels: failed with code ${response.code}")
                        modelList.clear()
                        modelList.add("gpt-3.5-turbo")
                        selectedModel = "gpt-3.5-turbo"
                        modelAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                Logger.error("fetchModels: exception", e)
                withContext(Dispatchers.Main) {
                    modelList.clear()
                    modelList.add("gpt-3.5-turbo")
                    selectedModel = "gpt-3.5-turbo"
                    modelAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    
    /**
     * 检查 DMP 服务健康状态
     */
    private fun checkHealth() {
        Logger.info("checkHealth: url=$baseUrl/health")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/health")
                    .get()
                    .build()
                
                Logger.info("checkHealth: sending request")
                val response = client.newCall(request).execute()
                Logger.info("checkHealth: response code=${response.code}, body=${response.body?.string()}")
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "DMP 服务正常", Toast.LENGTH_SHORT).show()
                        binding.tvStatus.text = "服务正常: $baseUrl"
                        Logger.info("checkHealth: success")
                    } else {
                        Toast.makeText(this@MainActivity, "服务异常: ${response.code}", Toast.LENGTH_SHORT).show()
                        Logger.warn("checkHealth: failed with code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Logger.error("checkHealth: exception", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "无法连接到 DMP 服务: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.tvStatus.text = "连接失败"
                }
            }
        }
    }
    
    /**
     * 发送消息
     */
    private fun sendMessage(content: String) {
        Logger.info("sendMessage: content length=${content.length}, stream=${binding.switchStream.isChecked}")
        // 添加用户消息
        val userMessage = ChatMessage("user", content)
        messages.add(userMessage)
        adapter.addMessage(userMessage)
        
        // 添加助手消息占位
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
        Logger.info("sendNormalMessage: url=$baseUrl/v1/chat/completions")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("model", selectedModel)
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
                
                Logger.info("sendNormalMessage: model=$selectedModel, sending request, body length=${jsonBody.length}")
                
                val request = Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                Logger.info("sendNormalMessage: response code=${response.code}")
                
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val choices = json.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val message = choices.getJSONObject(0).getJSONObject("message")
                            val responseContent = message.getString("content")
                            assistantMessage.content = responseContent
                            adapter.notifyDataSetChanged()
                            Logger.info("sendNormalMessage: success, response length=${responseContent.length}")
                        }
                    } else {
                        assistantMessage.content = "❌ 请求失败: ${response.code}"
                        adapter.notifyDataSetChanged()
                        Logger.error("sendNormalMessage: failed with code ${response.code}, body=$body")
                    }
                }
            } catch (e: Exception) {
                Logger.error("sendNormalMessage: exception", e)
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    assistantMessage.content = "❌ 网络错误: ${e.message}"
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
    
    /**
     * 流式发送
     */
    private fun sendStreamMessage(content: String, assistantMessage: ChatMessage) {
        Logger.info("sendStreamMessage: url=$baseUrl/v1/chat/completions/stream")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("model", selectedModel)
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
                    put("stream", true)
                }.toString()
                
                Logger.info("sendStreamMessage: model=$selectedModel, sending request, body length=${jsonBody.length}")
                
                val fullContent = StringBuilder()
                
                val request = Request.Builder()
                    .url("$baseUrl/v1/chat/completions/stream")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                Logger.info("sendStreamMessage: response code=${response.code}")
                
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        binding.layoutLoading.visibility = View.GONE
                        assistantMessage.content = "❌ 请求失败: ${response.code}"
                        adapter.notifyDataSetChanged()
                    }
                    Logger.error("sendStreamMessage: failed with code ${response.code}")
                    return@launch
                }
                
                // 读取 SSE 流式响应
                response.body?.source()?.use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6).trim()
                            if (data == "[DONE]") {
                                Logger.info("sendStreamMessage: stream complete, total length=${fullContent.length}")
                                break
                            }
                            try {
                                val json = JSONObject(data)
                                val choices = json.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val chunkContent = delta?.optString("content") ?: ""
                                    if (chunkContent.isNotEmpty()) {
                                        fullContent.append(chunkContent)
                                        withContext(Dispatchers.Main) {
                                            assistantMessage.content = fullContent.toString()
                                            adapter.notifyDataSetChanged()
                                            binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误
                            }
                        }
                    }
                }
                
                Logger.info("sendStreamMessage: success, total content length=${fullContent.length}")
                
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                }
            } catch (e: Exception) {
                Logger.error("sendStreamMessage: exception", e)
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    assistantMessage.content = "❌ 网络错误: ${e.message}"
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
}