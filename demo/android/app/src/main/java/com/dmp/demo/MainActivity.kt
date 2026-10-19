package com.dmp.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dmp.demo.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var client: DMPClient

    private var currentTab = Tab.REQUEST
    private var lastRequestJson = ""
    private var lastResponseJson = ""

    enum class Tab {
        REQUEST, RESPONSE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从资源文件读取默认服务器地址
        val defaultHost = getString(R.string.server_host)
        val defaultPort = getString(R.string.server_port)
        val defaultServerUrl = "http://$defaultHost:$defaultPort"
        
        // 如果 EditText 为空则使用默认地址
        val serverUrl = if (binding.etServerUrl.text.toString().isNotEmpty()) {
            binding.etServerUrl.text.toString()
        } else {
            defaultServerUrl
        }
        client = DMPClient(serverUrl)

        setupRecyclerView()
        setupUI()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
    }

    private fun setupUI() {
        binding.tabRequest.setOnClickListener {
            currentTab = Tab.REQUEST
            binding.tabRequest.backgroundTintList = getColorStateList(android.R.color.holo_blue_dark)
            binding.tabResponse.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            updateDetailPanel()
        }

        binding.tabResponse.setOnClickListener {
            currentTab = Tab.RESPONSE
            binding.tabResponse.backgroundTintList = getColorStateList(android.R.color.holo_blue_dark)
            binding.tabRequest.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            updateDetailPanel()
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.etMessage.text?.clear()
            }
        }

        binding.btnHealth.setOnClickListener {
            checkHealth()
        }

        binding.btnClear.setOnClickListener {
            adapter.messages.clear()
            adapter.notifyDataSetChanged()
            lastRequestJson = ""
            lastResponseJson = ""
            updateDetailPanel()
        }
    }

    private fun sendMessage(content: String) {
        val isStream = binding.rbStream.isChecked

        adapter.addMessage(MessageItem("user", content))
        binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)

        if (isStream) {
            sendStreamMessage(content)
        } else {
            sendNormalMessage(content)
        }
    }

    private fun sendNormalMessage(content: String) {
        val messages = adapter.messages.filterIsInstance<MessageItem>()
            .map { Message(it.role, it.content) }

        val request = ChatRequest(messages = messages)

        lifecycleScope.launch {
            binding.btnSend.isEnabled = false

            val result = client.chat(request)
            result.fold(
                onSuccess = { chatResult ->
                    lastRequestJson = chatResult.request
                    lastResponseJson = chatResult.response

                    if (chatResult.success) {
                        adapter.addMessage(MessageItem("assistant", chatResult.content))
                    } else {
                        adapter.addMessage(MessageItem("error", chatResult.error ?: "请求失败"))
                    }

                    binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                    updateDetailPanel()
                },
                onFailure = { error ->
                    lastRequestJson = request.toString()
                    lastResponseJson = "错误: ${error.message}"
                    adapter.addMessage(MessageItem("error", "请求失败: ${error.message}"))
                    binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                    updateDetailPanel()
                }
            )

            binding.btnSend.isEnabled = true
        }
    }

    private fun sendStreamMessage(content: String) {
        val messages = adapter.messages.filterIsInstance<MessageItem>()
            .map { Message(it.role, it.content) }

        val request = ChatRequest(messages = messages)

        val assistantIndex = adapter.messages.size
        adapter.addMessage(MessageItem("assistant", "正在处理..."))
        binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)

        lifecycleScope.launch {
            binding.btnSend.isEnabled = false

            var fullContent = ""
            var provider = ""
            var model = ""

            client.chatStream(request).collect { event ->
                when (event) {
                    is StreamEvent.RequestSent -> {
                        lastRequestJson = event.requestJson
                    }
                    is StreamEvent.StatusReceived -> {
                        // 状态码
                    }
                    is StreamEvent.ChunkReceived -> {
                        fullContent += event.content
                        provider = event.provider
                        model = event.model
                        adapter.updateMessage(assistantIndex, fullContent)
                        binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                    }
                    is StreamEvent.Done -> {
                        lastResponseJson = buildResponseJson(fullContent, provider, model)
                        updateDetailPanel()
                    }
                    is StreamEvent.Error -> {
                        adapter.updateMessage(assistantIndex, "错误: ${event.message}")
                        lastResponseJson = "错误: ${event.message}"
                        updateDetailPanel()
                    }
                }
            }

            binding.btnSend.isEnabled = true
        }
    }

    private fun buildResponseJson(content: String, provider: String, model: String): String {
        return """
            {
              "content": "$content",
              "provider": "$provider",
              "model": "$model",
              "finish_reason": "stop"
            }
        """.trimIndent()
    }

    private fun checkHealth() {
        lifecycleScope.launch {
            val result = client.checkHealth()
            result.fold(
                onSuccess = { body ->
                    Toast.makeText(this@MainActivity, "服务正常\n$body", Toast.LENGTH_LONG).show()
                },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, "健康检查失败: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun updateDetailPanel() {
        val text = when (currentTab) {
            Tab.REQUEST -> if (lastRequestJson.isNotEmpty()) lastRequestJson else "发送消息后查看请求体"
            Tab.RESPONSE -> if (lastResponseJson.isNotEmpty()) lastResponseJson else "收到响应后查看响应体"
        }
        binding.tvDetail.text = text
    }
}

data class MessageItem(val role: String, var content: String)

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    val messages = mutableListOf<MessageItem>()

    fun addMessage(message: MessageItem) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessage(index: Int, content: String) {
        if (index >= 0 && index < messages.size) {
            messages[index].content = content
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)

        fun bind(message: MessageItem) {
            tvMessage.text = message.content
            tvMessage.setBackgroundResource(
                when (message.role) {
                    "user" -> android.R.color.holo_blue_light
                    "assistant" -> android.R.color.white
                    "error" -> android.R.color.holo_red_light
                    else -> android.R.color.white
                }
            )
            tvMessage.setTextColor(
                when (message.role) {
                    "user" -> 0xFFFFFFFF.toInt()
                    "assistant" -> 0xFF000000.toInt()
                    "error" -> 0xFFFFFFFF.toInt()
                    else -> 0xFF000000.toInt()
                }
            )
        }
    }
}