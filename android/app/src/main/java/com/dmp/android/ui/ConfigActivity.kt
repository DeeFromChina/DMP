package com.dmp.android.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.RadioButton
import android.widget.LinearLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dmp.android.R
import com.dmp.android.config.DMPConfigManager
import com.dmp.android.databinding.ActivityConfigBinding
import com.dmp.android.core.DMPLogger
import com.dmp.android.core.DMPLogLevel
import android.os.Build
import com.dmp.android.device.DeviceDetector
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Headers
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 配置页面 - 简化版
 */
class ConfigActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityConfigBinding
    private lateinit var configManager: DMPConfigManager
    
    // 云端大模型厂商（需要 API Key + Base URL）
    private val cloudProviders = listOf(
        "DeepSeek", "通义千问", "智谱AI", "百度文心", "Kimi", "GLM",
        "Doubao-Seed", "腾讯混元", "MiniMax",
        "GPT", "Claude", "Gemini", "自定义"
    )
    
    // 云端厂商配置：名称 -> Base URL (基于官方文档)
    private val cloudProviderUrls = mapOf(
        "GPT" to "https://api.openai.com/v1",
        "DeepSeek" to "https://api.deepseek.com/v1",
        "通义千问" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "智谱AI" to "https://open.bigmodel.cn/api/paas/v4",
        "百度文心" to "https://qianfan.baidubce.com/v2",
        "Kimi" to "https://api.moonshot.cn/v1",
        "GLM" to "https://open.bigmodel.cn/api/paas/v4",
        "Doubao-Seed" to "https://ark.cn-beijing.volces.com/api/v3",
        "腾讯混元" to "https://api.hunyuan.cloud.tencent.com/v1",
        "MiniMax" to "https://api.minimax.chat/v1",
        "Claude" to "https://api.anthropic.com/v1",
        "Gemini" to "https://generativelanguage.googleapis.com/v1beta"
    )
    
    // API 类型选项（底层 LLM 支持的接口类型）
    private val apiTypes = listOf("Chat Completions", "Responses")
    private val apiTypePaths = mapOf(
        "Chat Completions" to "/chat/completions",
        "Responses" to "/responses"
    )
    private val apiTypeHints = mapOf(
        "Chat Completions" to "底层 LLM 支持 /chat/completions 接口（OpenAI 标准格式）",
        "Responses" to "底层 LLM 支持 /responses 接口（OpenAI Responses API）"
    )
    
    // 本地 LLM 厂商列表（这些厂商有内置本地服务）
    private val localLLMProviders = listOf(
        "Google", "三星", "一加", "OPPO"
    )
    
    // 云端厂商列表
    private lateinit var cloudProviderNames: List<String>
    // 本地厂商列表
    private lateinit var localProviderNames: List<String>
    // 模型列表（可编辑）
    private val editableModels = mutableListOf<String>()
    // 当前选中的模型
    private var selectedModel: String? = null
    // 当前厂商
    private var currentProvider: String? = null
    
    // HTTP 客户端用于获取模型列表
    private val modelsClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化日志
        DMPLogger.configure(this, DMPLogLevel.DEBUG)
        DMPLogger.info("ConfigActivity created")
        
        configManager = DMPConfigManager.getInstance(this)
        
        // 初始化厂商列表
        cloudProviderNames = cloudProviders
        localProviderNames = getLocalProviderList()
        
        setupUI()
        loadConfig()
    }
    
    private fun setupUI() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // 模式选择
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_cloud -> {
                    binding.layoutCloudConfig.visibility = View.VISIBLE
                    binding.layoutLocalConfig.visibility = View.GONE
                }
                R.id.radio_local -> {
                    binding.layoutCloudConfig.visibility = View.GONE
                    binding.layoutLocalConfig.visibility = View.VISIBLE
                }
            }
        }
        
        // 云端厂商 Spinner
        setupCloudSpinner()
        
        // API 类型 Spinner
        setupApiTypeSpinner()
        
        // 本地厂商 Spinner
        setupLocalSpinner()
        
        // 模型管理
        setupModelManagement()
        
        // 监听 Base URL 失焦事件，自动调用/models
        binding.editBaseUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // 失焦时尝试获取模型
                val baseUrl = binding.editBaseUrl.text.toString().trim()
                val apiKey = binding.editApiKey.text.toString().trim()
                val selectedPosition = binding.spinnerCloudProvider.selectedItemPosition
                
                if (selectedPosition >= 0 && selectedPosition < cloudProviderNames.size &&
                    baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val providerName = cloudProviderNames[selectedPosition]
                    fetchModels(providerName, apiKey, baseUrl)
                }
            }
        }
        
        // 监听 Base URL 变化，更新默认值显示
        binding.editBaseUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val currentUrl = s?.toString() ?: ""
                val selectedPosition = binding.spinnerCloudProvider.selectedItemPosition
                if (selectedPosition >= 0 && selectedPosition < cloudProviderNames.size) {
                    val providerName = cloudProviderNames[selectedPosition]
                    val defaultUrl = cloudProviderUrls[providerName] ?: ""
                    
                    if (currentUrl == defaultUrl) {
                        binding.textDefaultUrl.text = "默认值: $defaultUrl"
                    } else {
                        binding.textDefaultUrl.text = "默认值: $defaultUrl (已修改)"
                    }
                }
            }
        })
        
        // 手动获取模型按钮
        binding.btnFetchModels.setOnClickListener {
            val baseUrl = binding.editBaseUrl.text.toString().trim()
            val apiKey = binding.editApiKey.text.toString().trim()
            val selectedPosition = binding.spinnerCloudProvider.selectedItemPosition
            
            if (selectedPosition < 0 || selectedPosition >= cloudProviderNames.size) {
                Toast.makeText(this, "请选择模型厂商", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (baseUrl.isEmpty()) {
                Toast.makeText(this, "请输入 Base URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val providerName = cloudProviderNames[selectedPosition]
            fetchModels(providerName, apiKey, baseUrl)
        }
        
        // 保存本地配置按钮
        setupLocalSaveButton()
        
        // 保存云端配置
        binding.btnSaveCloud.setOnClickListener {
            val selectedPosition = binding.spinnerCloudProvider.selectedItemPosition
            if (selectedPosition < 0 || selectedPosition >= cloudProviderNames.size) {
                Toast.makeText(this, "请选择模型厂商", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val providerName = cloudProviderNames[selectedPosition]
            val apiKey = binding.editApiKey.text.toString()
            val baseUrl = binding.editBaseUrl.text.toString()
            
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (baseUrl.isEmpty()) {
                Toast.makeText(this, "请输入 Base URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 直接保存配置（模型列表通过失焦或手动按钮获取）
            saveCloudConfig(providerName, apiKey, baseUrl)
        }
    }
    
    /**
     * 调用/models端点获取可用模型列表
     */
    private fun fetchModels(providerName: String, apiKey: String, baseUrl: String) {
        DMPLogger.info("正在获取 $providerName 的模型列表...")
        
        // 显示加载动画
        binding.progressModels.visibility = View.VISIBLE
        binding.btnFetchModels.isEnabled = false
        binding.btnFetchModels.text = "获取中..."
        
        // 构建/models URL：从 Base URL 中提取基础路径
        val modelsUrl = buildModelsUrl(baseUrl)
        DMPLogger.info("调用 /models 接口: $modelsUrl")
        
        val request = Request.Builder()
            .url(modelsUrl)
            .get()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        
        // 使用异步请求避免 NetworkOnMainThreadException
        modelsClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    DMPLogger.error("获取模型列表异常: ${e.javaClass.simpleName} - ${e.message}")
                    DMPLogger.error("异常堆栈: ${e.stackTraceToString()}")
                    binding.textModelHint.text = "获取失败，请手动添加模型"
                    Toast.makeText(
                        this@ConfigActivity,
                        "无法获取模型列表: ${e.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                    resetFetchButton()
                }
            }
            
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                // 在后台线程读取响应体，避免 NetworkOnMainThreadException
                val responseCode = response.code
                val isSuccessful = response.isSuccessful
                val responseBody = try {
                    response.body?.string()
                } catch (e: Exception) {
                    DMPLogger.error("读取响应体失败: ${e.message}")
                    null
                }
                
                runOnUiThread {
                    try {
                        if (isSuccessful) {
                            DMPLogger.info("模型列表响应体: $responseBody")
                            
                            if (responseBody.isNullOrEmpty()) {
                                DMPLogger.error("响应体为空")
                                binding.textModelHint.text = "获取失败，响应为空，请手动添加模型"
                                Toast.makeText(this@ConfigActivity, "响应为空，请手动添加模型", Toast.LENGTH_LONG).show()
                                resetFetchButton()
                                return@runOnUiThread
                            }
                            
                            val json = JSONObject(responseBody)
                            DMPLogger.info("JSON 解析成功，keys: ${json.keys()}")
                            
                            // 解析模型列表
                            val modelsArray = json.optJSONArray("data")
                            val models = mutableListOf<String>()
                            
                            if (modelsArray != null) {
                                DMPLogger.info("data 数组长度: ${modelsArray.length()}")
                                for (i in 0 until modelsArray.length()) {
                                    val modelObj = modelsArray.getJSONObject(i)
                                    val modelId = modelObj.optString("id", "")
                                    DMPLogger.info("模型[$i]: id=$modelId, keys=${modelObj.keys()}")
                                    if (modelId.isNotEmpty()) {
                                        models.add(modelId)
                                    }
                                }
                            } else {
                                DMPLogger.warn("响应中没有 data 字段，尝试其他格式")
                                // 尝试直接解析为数组
                                if (responseBody.trim().startsWith("[")) {
                                    val directArray = JSONArray(responseBody)
                                    for (i in 0 until directArray.length()) {
                                        val item = directArray.get(i)
                                        val modelId = when (item) {
                                            is String -> item
                                            is JSONObject -> item.optString("id", "")
                                            else -> ""
                                        }
                                        if (modelId.isNotEmpty()) {
                                            models.add(modelId)
                                        }
                                    }
                                }
                            }
                            
                            DMPLogger.info("获取到 ${models.size} 个模型: ${models.joinToString(", ")}")
                            
                            // 弹出对话框让用户选择模型
                            showModelSelectionDialog(models)
                        } else {
                            DMPLogger.warn("获取模型列表失败: HTTP $responseCode, body=$responseBody")
                            binding.textModelHint.text = "获取失败，请手动添加模型"
                            Toast.makeText(
                                this@ConfigActivity,
                                "无法获取模型列表 (HTTP $responseCode)，请手动添加",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        DMPLogger.error("解析模型列表异常: ${e.javaClass.simpleName} - ${e.message}")
                        DMPLogger.error("异常堆栈: ${e.stackTraceToString()}")
                        binding.textModelHint.text = "获取失败，请手动添加模型"
                        Toast.makeText(
                            this@ConfigActivity,
                            "解析模型列表失败: ${e.javaClass.simpleName} - ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        resetFetchButton()
                    }
                }
            }
        })
    }
    
    /**
     * 弹出模型选择对话框
     */
    private fun showModelSelectionDialog(models: List<String>) {
        val items = models.toTypedArray()
        var selectedIndex = -1
        
        // 如果有已选中的模型，默认选中
        if (!selectedModel.isNullOrEmpty()) {
            selectedIndex = models.indexOf(selectedModel)
        }
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle("选择模型 (${models.size} 个)")
            .setSingleChoiceItems(items, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("确定") { _, _ ->
                if (selectedIndex >= 0) {
                    val chosenModel = items[selectedIndex]
                    DMPLogger.info("选中模型: $chosenModel")
                    
                    // 检查是否已存在该模型
                    val alreadyExists = editableModels.any { it == chosenModel }
                    if (alreadyExists) {
                        Toast.makeText(this, "模型已存在: $chosenModel", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    // 追加到列表末尾，不清空已有模型
                    editableModels.add(chosenModel)
                    addModelRow(chosenModel, autoSelect = false)
                    
                    binding.textModelHint.text = "共 ${editableModels.size} 个模型"
                    Toast.makeText(this, "已添加: $chosenModel", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 重置获取模型按钮状态
     */
    private fun resetFetchButton() {
        binding.progressModels.visibility = View.GONE
        binding.btnFetchModels.isEnabled = true
        binding.btnAddModel.isEnabled = true
        binding.btnFetchModels.text = "自动获取模型列表"
    }
    
    /**
     * 构建 /models URL
     * 从 Base URL 中提取基础路径，然后拼接 /models
     * 例如：
     * - http://localhost:8787/v1/chat/completions → http://localhost:8787/v1/models
     * - https://api.openai.com/v1 → https://api.openai.com/v1/models
     * - https://api.openai.com/v1/ → https://api.openai.com/v1/models
     * - http://39.108.62.253:8099/v1/responses → http://39.108.62.253:8099/v1/models
     */
    private fun buildModelsUrl(baseUrl: String): String {
        // 清理 URL：移除所有非法字符
        var cleanUrl = baseUrl
            .replace("`", "")
            .replace("\"", "")
            .replace("'", "")
            .replace(" ", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        
        // 移除末尾的斜杠
        cleanUrl = cleanUrl.trimEnd('/')
        
        // 如果包含路径（如 /v1/chat/completions），提取到版本号为止
        val pathSegments = listOf(
            "/chat/completions", "/completions", "/chat", "/v1beta",
            "/responses", "/responses/models"
        )
        
        for (segment in pathSegments) {
            if (cleanUrl.endsWith(segment)) {
                cleanUrl = cleanUrl.removeSuffix(segment)
                break
            }
        }
        
        // 拼接 /models
        return "$cleanUrl/models"
    }
    
    /**
     * 保存云端配置
     */
    private fun saveCloudConfig(providerName: String, apiKey: String, baseUrl: String) {
        val apiTypePosition = binding.spinnerApiType.selectedItemPosition
        val apiType = if (apiTypePosition >= 0 && apiTypePosition < apiTypes.size) apiTypes[apiTypePosition] else "Chat Completions"
        
        DMPLogger.info("========== 开始保存云端配置 ==========")
        DMPLogger.info("provider=$providerName")
        DMPLogger.info("apiKey=${apiKey.take(3)}***")
        DMPLogger.info("baseUrl=$baseUrl")
        DMPLogger.info("apiType=$apiType")
        DMPLogger.info("模型数量=${editableModels.size}")
        DMPLogger.info("模型列表=${editableModels.joinToString(", ")}")
        DMPLogger.info("选中模型=$selectedModel")
        
        // 更新模型列表
        updateModelsList()
        
        configManager.setCloudApiKey(providerName, apiKey)
        DMPLogger.info("已保存 API Key")
        
        configManager.setCloudBaseUrl(providerName, baseUrl)
        DMPLogger.info("已保存 Base URL")
        
        configManager.setCloudApiType(providerName, apiType)
        DMPLogger.info("已保存 API Type")
        
        configManager.setCloudModels(providerName, editableModels)
        DMPLogger.info("已保存模型列表")
        
        configManager.selectedProvider = providerName
        DMPLogger.info("已保存选中厂商: ${configManager.selectedProvider}")
        
        configManager.cloudEnabled = true
        configManager.localEnabled = false
        DMPLogger.info("已保存模式: cloudEnabled=${configManager.cloudEnabled}")
        
        // 保存选中的模型
        if (!selectedModel.isNullOrEmpty()) {
            configManager.setCloudSelectedModel(providerName, selectedModel!!)
            DMPLogger.info("已保存选中模型: $selectedModel")
        }
        
        DMPLogger.info("========== 云端配置保存完成 ==========")
        
        Toast.makeText(this, "云端配置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun setupLocalSaveButton() {
        // 保存本地配置
        binding.btnSaveLocal.setOnClickListener {
            val selectedPosition = binding.spinnerLocalProvider.selectedItemPosition
            val localProvider = if (selectedPosition >= 0 && selectedPosition < localProviderNames.size) {
                localProviderNames[selectedPosition]
            } else {
                null
            }
            
            // 如果列表为空，说明没有扫描到本地 LLM
            if (localProviderNames.isEmpty()) {
                Toast.makeText(this, "未检测到本地 LLM 服务，请选择云端大模型", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            DMPLogger.info("保存本地配置: provider=$localProvider")
            
            configManager.localEnabled = true
            configManager.cloudEnabled = false
            if (localProvider != null) {
                configManager.selectedProvider = localProvider
            }
            
            DMPLogger.info("本地配置已保存到 SharedPreferences")
            
            Toast.makeText(this, "本地配置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupCloudSpinner() {
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, cloudProviderNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item, parent, false)
                val textView = view.findViewById<TextView>(R.id.spinner_text)
                val item = cloudProviderNames[position]
                
                textView.text = item
                textView.setTextColor(0xFFF1F5F9.toInt())
                textView.textSize = 14f
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_dropdown_item, parent, false)
                val textView = view.findViewById<TextView>(R.id.spinner_text)
                val item = cloudProviderNames[position]
                
                textView.text = item
                textView.setTextColor(0xFF1E293B.toInt())
                textView.textSize = 14f
                textView.isEnabled = true
                
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerCloudProvider.adapter = adapter
        
        // 合并的监听器：处理厂商选择、URL更新、配置加载
        binding.spinnerCloudProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val providerName = cloudProviderNames[position]
                currentProvider = providerName
                val defaultUrl = cloudProviderUrls[providerName] ?: ""
                
                // 更新默认值显示
                binding.textDefaultUrl.text = "默认值: $defaultUrl"
                
                // 加载已保存的 Base URL
                val savedUrl = configManager.getCloudBaseUrl(providerName)
                binding.editBaseUrl.setText(if (savedUrl.isNullOrEmpty()) defaultUrl else savedUrl)
                binding.editBaseUrl.hint = "请输入 Base URL"
                
                // 加载已保存的 API Key
                val savedKey = configManager.getCloudApiKey(providerName)
                binding.editApiKey.setText(savedKey ?: "")
                binding.editApiKey.hint = "请输入 API Key"
                
                // 加载已保存的 API 类型
                val savedApiType = configManager.getCloudApiType(providerName)
                val apiTypePosition = if (savedApiType.isNullOrEmpty()) 0 else apiTypes.indexOf(savedApiType).takeIf { it >= 0 } ?: 0
                binding.spinnerApiType.setSelection(apiTypePosition)
                binding.textApiTypeHint.text = apiTypeHints[apiTypes[apiTypePosition]] ?: ""
                
                // 加载该厂商的模型列表
                loadModelsForProvider(providerName)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    
    /**
     * 设置 API 类型 Spinner
     */
    private fun setupApiTypeSpinner() {
        val apiTypeAdapter = ArrayAdapter(this, R.layout.spinner_item, apiTypes)
        apiTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerApiType.adapter = apiTypeAdapter
        
        binding.spinnerApiType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedType = apiTypes[position]
                binding.textApiTypeHint.text = apiTypeHints[selectedType] ?: ""
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    
    /**
     * 设置模型管理（增删行交互）
     */
    private fun setupModelManagement() {
        // 添加模型按钮
        binding.btnAddModel.setOnClickListener {
            addModelRow("")
        }
    }
    
    /**
     * 添加模型行
     */
    private fun addModelRow(modelName: String, autoSelect: Boolean = false) {
        val itemView = layoutInflater.inflate(R.layout.item_model, binding.layoutModelsList, false)
        val editModelName = itemView.findViewById<EditText>(R.id.edit_model_name)
        val radioModel = itemView.findViewById<RadioButton>(R.id.radio_model)
        val btnDelete = itemView.findViewById<ImageButton>(R.id.btn_delete_model)
        
        editModelName.setText(modelName)
        
        // 单选按钮点击事件
        radioModel.setOnClickListener {
            selectedModel = editModelName.text.toString().trim()
            // 更新所有单选按钮状态
            updateRadioButtons(radioModel)
        }
        
        // 输入框变化时更新选中状态
        editModelName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val name = s.toString().trim()
                if (radioModel.isChecked) {
                    selectedModel = name
                }
                // 更新模型列表
                updateModelsList()
            }
        })
        
        // 删除按钮点击事件
        btnDelete.setOnClickListener {
            binding.layoutModelsList.removeView(itemView)
            updateModelsList()
        }
        
        binding.layoutModelsList.addView(itemView)
        
        // 只在明确要求自动选中时才选中
        if (autoSelect) {
            radioModel.isChecked = true
            selectedModel = modelName
            editModelName.requestFocus()
        }
    }
    
    /**
     * 更新单选按钮状态
     */
    private fun updateRadioButtons(selectedRadio: RadioButton) {
        for (i in 0 until binding.layoutModelsList.childCount) {
            val itemView = binding.layoutModelsList.getChildAt(i)
            val radio = itemView.findViewById<RadioButton>(R.id.radio_model)
            radio.isChecked = (radio == selectedRadio)
        }
    }
    
    /**
     * 更新模型列表
     */
    private fun updateModelsList() {
        editableModels.clear()
        for (i in 0 until binding.layoutModelsList.childCount) {
            val itemView = binding.layoutModelsList.getChildAt(i)
            val editModelName = itemView.findViewById<EditText>(R.id.edit_model_name)
            val name = editModelName.text.toString().trim()
            if (name.isNotEmpty()) {
                editableModels.add(name)
            }
        }
        
        binding.textModelHint.text = "共 ${editableModels.size} 个模型"
    }
    
    /**
     * 加载指定厂商的模型列表
     */
    private fun loadModelsForProvider(providerName: String) {
        val savedModels = configManager.getCloudModels(providerName)
        val savedSelectedModel = configManager.getCloudSelectedModel(providerName)
        
        // 清空现有列表
        binding.layoutModelsList.removeAllViews()
        editableModels.clear()
        
        if (savedModels.isNotEmpty()) {
            // 添加已保存的模型
            savedModels.forEach { modelName ->
                addModelRow(modelName)
            }
            
            // 设置之前选中的模型
            selectedModel = savedSelectedModel
            if (selectedModel.isNullOrEmpty()) {
                selectedModel = savedModels.firstOrNull()
            }
            
            // 更新单选按钮状态
            updateRadioButtonsByModel(selectedModel)
            
            DMPLogger.info("加载 $providerName 的模型列表: ${savedModels.size} 个模型")
        } else {
            binding.textModelHint.text = "保存配置后自动获取可用模型，也可手动添加"
            DMPLogger.info("$providerName 暂无已保存的模型列表")
        }
    }
    
    /**
     * 根据模型名称更新单选按钮状态
     */
    private fun updateRadioButtonsByModel(modelName: String?) {
        for (i in 0 until binding.layoutModelsList.childCount) {
            val itemView = binding.layoutModelsList.getChildAt(i)
            val radio = itemView.findViewById<RadioButton>(R.id.radio_model)
            val editModelName = itemView.findViewById<EditText>(R.id.edit_model_name)
            radio.isChecked = (editModelName.text.toString().trim() == modelName)
        }
    }
    
    private fun setupLocalSpinner() {
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, localProviderNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item, parent, false)
                val textView = view.findViewById<TextView>(R.id.spinner_text)
                val item = localProviderNames[position]
                
                textView.text = item
                textView.setTextColor(0xFFF1F5F9.toInt())
                textView.textSize = 14f
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_dropdown_item, parent, false)
                val textView = view.findViewById<TextView>(R.id.spinner_text)
                val item = localProviderNames[position]
                
                textView.text = item
                textView.setTextColor(0xFF1E293B.toInt())
                textView.textSize = 14f
                textView.isEnabled = true
                
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerLocalProvider.adapter = adapter
        
        // 根据设备支持情况更新提示文本
        if (localProviderNames.isEmpty()) {
            binding.textLocalProviderTitle.text = "当前设备不支持本地 LLM"
            binding.textLocalHint.text = "未检测到本地 LLM 服务，请选择云端大模型"
            binding.textLocalHint.setTextColor(0xFFFF9800.toInt())
        }
    }
    
    // 获取本地厂商列表（根据当前设备动态生成）
    private fun getLocalProviderList(): List<String> {
        val list = mutableListOf<String>()
        val deviceType = DeviceDetector.detectDevice()
        val sdkInt = deviceType.version.sdkInt
        val brand = deviceType.brand
        
        DMPLogger.info("设备检测: brand=$brand, sdkInt=$sdkInt, model=${Build.MODEL}", 
            file = "ConfigActivity.kt", function = "getLocalProviderList", line = 0)
        
        // 根据 README 文档的设备支持表判断是否有本地 LLM 服务
        // 只有 Android 14+ (API 34+) 且特定品牌才支持本地 LLM
        val localLLMProvider = when {
            // Google Pixel: Android 14+ 支持 ML Kit GenAI (Gemini Nano)
            brand == "google" && sdkInt >= 34 -> "Google"
            // 三星: Android 14+ 通过 Google ML Kit 支持
            brand == "samsung" && sdkInt >= 34 -> "三星"
            // 一加: Android 14+ 部分机型支持 ML Kit GenAI
            brand == "oneplus" && sdkInt >= 34 -> "一加"
            // OPPO: Android 14+ 部分机型支持 ML Kit GenAI
            brand == "oppo" && sdkInt >= 34 -> "OPPO"
            else -> null
        }
        
        // 如果设备支持本地 LLM，添加到列表
        if (localLLMProvider != null) {
            list.add(localLLMProvider)
            DMPLogger.info("设备支持本地 LLM: $localLLMProvider", 
                file = "ConfigActivity.kt", function = "getLocalProviderList", line = 0)
        } else {
            DMPLogger.info("设备不支持本地 LLM", 
                file = "ConfigActivity.kt", function = "getLocalProviderList", line = 0)
        }
        
        DMPLogger.info("本地厂商列表: ${list.joinToString(", ")}", 
            file = "ConfigActivity.kt", function = "getLocalProviderList", line = 0)
        
        return list
    }
    
    private fun loadConfig() {
        DMPLogger.info("========== 开始加载配置 ==========")
        DMPLogger.info("cloudEnabled=${configManager.cloudEnabled}")
        DMPLogger.info("localEnabled=${configManager.localEnabled}")
        DMPLogger.info("selectedProvider=${configManager.selectedProvider}")
        DMPLogger.info("cloudBaseURL=${configManager.cloudBaseURL}")
        DMPLogger.info("cloudModelName=${configManager.cloudModelName}")
        
        // 检查所有云端厂商的配置
        cloudProviderNames.forEach { providerName ->
            val savedUrl = configManager.getCloudBaseUrl(providerName)
            val savedKey = configManager.getCloudApiKey(providerName)
            val savedModels = configManager.getCloudModels(providerName)
            DMPLogger.info("厂商 [$providerName]: URL=$savedUrl, APIKey=${savedKey?.take(3)}***, Models=${savedModels.size}个")
        }
        
        // 加载模式
        if (configManager.cloudEnabled) {
            binding.radioCloud.isChecked = true
            binding.layoutCloudConfig.visibility = View.VISIBLE
            binding.layoutLocalConfig.visibility = View.GONE
            
            // 加载选中的云端厂商
            val savedProvider = configManager.selectedProvider
            DMPLogger.info("准备加载云端厂商: $savedProvider")
            val savedPosition = cloudProviderNames.indexOf(savedProvider)
            DMPLogger.info("厂商位置: $savedPosition")
            if (savedPosition >= 0) {
                binding.spinnerCloudProvider.setSelection(savedPosition)
            }
        } else if (configManager.localEnabled) {
            binding.radioLocal.isChecked = true
            binding.layoutCloudConfig.visibility = View.GONE
            binding.layoutLocalConfig.visibility = View.VISIBLE
            
            // 加载选中的本地厂商
            val savedProvider = configManager.selectedProvider
            val savedPosition = localProviderNames.indexOf(savedProvider)
            if (savedPosition >= 0) {
                binding.spinnerLocalProvider.setSelection(savedPosition)
            }
        } else {
            DMPLogger.info("未找到已保存的配置")
        }
        
        DMPLogger.info("========== 配置加载完成 ==========")
    }
}