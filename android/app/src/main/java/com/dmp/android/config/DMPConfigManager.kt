package com.dmp.android.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.dmp.android.core.DMPLogger
import com.dmp.android.core.DMPSecureStorage
import com.dmp.android.core.maskAPIKey

/**
 * DMP配置管理器
 */
class DMPConfigManager(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: DMPConfigManager? = null
        
        fun getInstance(context: Context): DMPConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DMPConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences("dmp_config", Context.MODE_PRIVATE)
    private val secureStorage = DMPSecureStorage(context)
    
    // Core ML配置
    var tfliteEnabled: Boolean
        get() = prefs.getBoolean("tflite_enabled", true)
        set(value) = prefs.edit { putBoolean("tflite_enabled", value) }
    
    var tfliteModelPath: String?
        get() = prefs.getString("tflite_model_path", null)
        set(value) = prefs.edit { putString("tflite_model_path", value) }
    
    // 厂商API配置
    var huaweiEnabled: Boolean
        get() = prefs.getBoolean("huawei_enabled", false)
        set(value) = prefs.edit { putBoolean("huawei_enabled", value) }
    
    var huaweiApiKey: String?
        get() = secureStorage.loadAPIKey("huawei_api_key")
        set(value) {
            if (value != null) {
                secureStorage.saveAPIKey(value, "huawei_api_key")
                DMPLogger.debug("华为API Key已保存到EncryptedSharedPreferences: ${value.maskAPIKey()}", 
                               file = "DMPConfigManager.kt", function = "huaweiApiKey", line = 0)
            }
        }
    
    var xiaomiEnabled: Boolean
        get() = prefs.getBoolean("xiaomi_enabled", false)
        set(value) = prefs.edit { putBoolean("xiaomi_enabled", value) }
    
    var xiaomiApiKey: String?
        get() = secureStorage.loadAPIKey("xiaomi_api_key")
        set(value) {
            if (value != null) {
                secureStorage.saveAPIKey(value, "xiaomi_api_key")
                DMPLogger.debug("小米API Key已保存到EncryptedSharedPreferences: ${value.maskAPIKey()}", 
                               file = "DMPConfigManager.kt", function = "xiaomiApiKey", line = 0)
            }
        }
    
    var oppoEnabled: Boolean
        get() = prefs.getBoolean("oppo_enabled", false)
        set(value) = prefs.edit { putBoolean("oppo_enabled", value) }
    
    var oppoApiKey: String?
        get() = secureStorage.loadAPIKey("oppo_api_key")
        set(value) {
            if (value != null) {
                secureStorage.saveAPIKey(value, "oppo_api_key")
                DMPLogger.debug("OPPO API Key已保存到EncryptedSharedPreferences: ${value.maskAPIKey()}", 
                               file = "DMPConfigManager.kt", function = "oppoApiKey", line = 0)
            }
        }
    
    var vivoEnabled: Boolean
        get() = prefs.getBoolean("vivo_enabled", false)
        set(value) = prefs.edit { putBoolean("vivo_enabled", value) }
    
    var vivoApiKey: String?
        get() = secureStorage.loadAPIKey("vivo_api_key")
        set(value) {
            if (value != null) {
                secureStorage.saveAPIKey(value, "vivo_api_key")
                DMPLogger.debug("vivo API Key已保存到EncryptedSharedPreferences: ${value.maskAPIKey()}", 
                               file = "DMPConfigManager.kt", function = "vivoApiKey", line = 0)
            }
        }
    
    // 模式配置
    var cloudEnabled: Boolean
        get() = prefs.getBoolean("cloud_enabled", false)
        set(value) = prefs.edit { putBoolean("cloud_enabled", value) }
    
    var localEnabled: Boolean
        get() = prefs.getBoolean("local_enabled", false)
        set(value) = prefs.edit { putBoolean("local_enabled", value) }
    
    var selectedProvider: String
        get() = prefs.getString("selected_provider", "OpenAI") ?: "OpenAI"
        set(value) = prefs.edit { putString("selected_provider", value) }
    
    var localModelPath: String?
        get() = prefs.getString("local_model_path", null)
        set(value) = prefs.edit { putString("local_model_path", value) }
    
    // 云端API Key管理（支持多厂商）
    fun getCloudApiKey(providerName: String): String? {
        return secureStorage.loadAPIKey("cloud_api_key_$providerName")
    }
    
    fun setCloudApiKey(providerName: String, apiKey: String) {
        if (apiKey.isNotEmpty()) {
            secureStorage.saveAPIKey(apiKey, "cloud_api_key_$providerName")
            DMPLogger.info("$providerName API Key已保存: ${apiKey.take(3)}***", 
                           file = "DMPConfigManager.kt", function = "setCloudApiKey", line = 0)
        }
    }
    
    fun getCloudBaseUrl(providerName: String): String? {
        return prefs.getString("cloud_base_url_$providerName", null)
    }
    
    fun setCloudBaseUrl(providerName: String, baseUrl: String) {
        prefs.edit { putString("cloud_base_url_$providerName", baseUrl) }
        DMPLogger.info("$providerName Base URL已保存: $baseUrl", 
                       file = "DMPConfigManager.kt", function = "setCloudBaseUrl", line = 0)
    }
    
    // API 类型管理
    fun getCloudApiType(providerName: String): String? {
        return prefs.getString("cloud_api_type_$providerName", null)
    }
    
    fun setCloudApiType(providerName: String, apiType: String) {
        prefs.edit { putString("cloud_api_type_$providerName", apiType) }
        DMPLogger.info("$providerName API Type已保存: $apiType", 
                       file = "DMPConfigManager.kt", function = "setCloudApiType", line = 0)
    }
    
    // 模型列表管理
    fun getCloudModels(providerName: String): List<String> {
        val modelsJson = prefs.getString("cloud_models_$providerName", null)
        return if (modelsJson.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                modelsJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    fun setCloudModels(providerName: String, models: List<String>) {
        val modelsJson = models.joinToString(",")
        prefs.edit { putString("cloud_models_$providerName", modelsJson) }
        DMPLogger.info("$providerName 模型列表已保存: ${models.joinToString(", ")}", 
                       file = "DMPConfigManager.kt", function = "setCloudModels", line = 0)
    }
    
    fun getCloudSelectedModel(providerName: String): String? {
        return prefs.getString("cloud_selected_model_$providerName", null)
    }
    
    fun setCloudSelectedModel(providerName: String, modelName: String) {
        prefs.edit { putString("cloud_selected_model_$providerName", modelName) }
        DMPLogger.info("$providerName 选中模型已保存: $modelName", 
                       file = "DMPConfigManager.kt", function = "setCloudSelectedModel", line = 0)
    }
    
    var cloudApiKey: String?
        get() = secureStorage.loadAPIKey("cloud_api_key")
        set(value) {
            if (value != null) {
                secureStorage.saveAPIKey(value, "cloud_api_key")
                DMPLogger.debug("Cloud API Key已保存到EncryptedSharedPreferences: ${value.maskAPIKey()}", 
                               file = "DMPConfigManager.kt", function = "cloudApiKey", line = 0)
            }
        }
    
    var cloudBaseURL: String
        get() = prefs.getString("cloud_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(value) = prefs.edit { putString("cloud_base_url", value) }
    
    var cloudModelName: String
        get() = prefs.getString("cloud_model_name", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"
        set(value) = prefs.edit { putString("cloud_model_name", value) }
    
    // 通用配置
    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit { putFloat("temperature", value) }
    
    var maxTokens: Int
        get() = prefs.getInt("max_tokens", 2048)
        set(value) = prefs.edit { putInt("max_tokens", value) }
    
    var preferredProvider: String
        get() = prefs.getString("preferred_provider", "auto") ?: "auto"
        set(value) = prefs.edit { putString("preferred_provider", value) }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        prefs.edit { clear() }
        DMPLogger.info("配置已重置为默认值", file = "DMPConfigManager.kt", function = "resetToDefaults", line = 0)
    }
    
    /**
     * 导出配置为Map (敏感信息脱敏)
     */
    fun exportConfig(): Map<String, Any> {
        return mapOf(
            "tflite_enabled" to tfliteEnabled,
            "tflite_model_path" to (tfliteModelPath ?: ""),
            "huawei_enabled" to huaweiEnabled,
            "huawei_api_key" to (huaweiApiKey?.maskAPIKey() ?: ""),
            "xiaomi_enabled" to xiaomiEnabled,
            "xiaomi_api_key" to (xiaomiApiKey?.maskAPIKey() ?: ""),
            "oppo_enabled" to oppoEnabled,
            "oppo_api_key" to (oppoApiKey?.maskAPIKey() ?: ""),
            "vivo_enabled" to vivoEnabled,
            "vivo_api_key" to (vivoApiKey?.maskAPIKey() ?: ""),
            "cloud_enabled" to cloudEnabled,
            "cloud_api_key" to (cloudApiKey?.maskAPIKey() ?: ""),
            "cloud_base_url" to cloudBaseURL,
            "cloud_model_name" to cloudModelName,
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            "preferred_provider" to preferredProvider
        )
    }
    
    /**
     * 从Map导入配置
     */
    fun importConfig(config: Map<String, Any>) {
        prefs.edit {
            (config["tflite_enabled"] as? Boolean)?.let { putBoolean("tflite_enabled", it) }
            (config["tflite_model_path"] as? String)?.let { putString("tflite_model_path", it) }
            (config["huawei_enabled"] as? Boolean)?.let { putBoolean("huawei_enabled", it) }
            (config["huawei_api_key"] as? String)?.let { huaweiApiKey = it }
            (config["xiaomi_enabled"] as? Boolean)?.let { putBoolean("xiaomi_enabled", it) }
            (config["xiaomi_api_key"] as? String)?.let { xiaomiApiKey = it }
            (config["oppo_enabled"] as? Boolean)?.let { putBoolean("oppo_enabled", it) }
            (config["oppo_api_key"] as? String)?.let { oppoApiKey = it }
            (config["vivo_enabled"] as? Boolean)?.let { putBoolean("vivo_enabled", it) }
            (config["vivo_api_key"] as? String)?.let { vivoApiKey = it }
            (config["cloud_enabled"] as? Boolean)?.let { putBoolean("cloud_enabled", it) }
            (config["cloud_api_key"] as? String)?.let { cloudApiKey = it }
            (config["cloud_base_url"] as? String)?.let { putString("cloud_base_url", it) }
            (config["cloud_model_name"] as? String)?.let { putString("cloud_model_name", it) }
            (config["temperature"] as? Float)?.let { putFloat("temperature", it) }
            (config["max_tokens"] as? Int)?.let { putInt("max_tokens", it) }
            (config["preferred_provider"] as? String)?.let { putString("preferred_provider", it) }
        }
    }
}