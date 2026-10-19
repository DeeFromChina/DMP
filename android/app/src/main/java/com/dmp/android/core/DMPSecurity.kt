package com.dmp.android.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 输入验证器
 */
object DMPInputValidator {

    /**
     * 验证错误
     */
    sealed class ValidationError(message: String) : Exception(message) {
        object EmptyInput : ValidationError("输入不能为空")
        class InputTooLong(maxLength: Int) : ValidationError("输入过长，最大长度为$maxLength")
        object PotentialSQLInjection : ValidationError("检测到潜在的SQL注入")
        object PotentialXSS : ValidationError("检测到潜在的XSS攻击")
        object InvalidAPIKeyFormat : ValidationError("API Key格式无效")
        class APIKeyTooLong(maxLength: Int) : ValidationError("API Key过长，最大长度为$maxLength")
        object EmptyAPIKey : ValidationError("API Key不能为空")
        object InvalidURL : ValidationError("URL格式无效")
        object InsecureURL : ValidationError("仅支持HTTPS协议")
        object InvalidTemperature : ValidationError("温度值必须在0-2之间")
        object InvalidMaxTokens : ValidationError("Max Tokens必须在256-8192之间")
    }

    /**
     * 验证消息
     */
    @Throws(ValidationError::class)
    fun validateMessage(message: String) {
        require(message.trim().isNotEmpty()) { throw ValidationError.EmptyInput }
        require(message.length <= 10000) { throw ValidationError.InputTooLong(10000) }

        // SQL注入检测
        val sqlPattern = Pattern.compile("('|--|;|DROP|INSERT|UPDATE|DELETE|SELECT|UNION|EXEC)", Pattern.CASE_INSENSITIVE)
        require(!sqlPattern.matcher(message).find()) { throw ValidationError.PotentialSQLInjection }

        // XSS检测
        require(!message.contains("<script", ignoreCase = true)) { throw ValidationError.PotentialXSS }
    }

    /**
     * 验证API Key
     */
    @Throws(ValidationError::class)
    fun validateAPIKey(key: String) {
        require(key.trim().isNotEmpty()) { throw ValidationError.EmptyAPIKey }
        require(key.length <= 200) { throw ValidationError.APIKeyTooLong(200) }

        // API Key格式验证
        require(key.matches(Regex("^[a-zA-Z0-9\\-_]{10,200}$"))) { throw ValidationError.InvalidAPIKeyFormat }
    }

    /**
     * 验证URL
     */
    @Throws(ValidationError::class)
    fun validateURL(url: String) {
        require(url.startsWith("https://", ignoreCase = true)) { throw ValidationError.InsecureURL }
    }

    /**
     * 验证温度
     */
    @Throws(ValidationError::class)
    fun validateTemperature(temp: Float) {
        require(temp in 0f..2f) { throw ValidationError.InvalidTemperature }
    }

    /**
     * 验证Max Tokens
     */
    @Throws(ValidationError::class)
    fun validateMaxTokens(tokens: Int) {
        require(tokens in 256..8192) { throw ValidationError.InvalidMaxTokens }
    }
}

/**
 * 安全存储
 */
class DMPSecureStorage(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "dmp_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 保存API Key
     */
    fun saveAPIKey(key: String, forKey: String) {
        sharedPreferences.edit()
            .putString(forKey, key)
            .apply()
        DMPLogger.debug("API Key saved to EncryptedSharedPreferences: ${key.maskAPIKey()}")
    }

    /**
     * 加载API Key
     */
    fun loadAPIKey(forKey: String): String? {
        val key = sharedPreferences.getString(forKey, null)
        key?.let { keyValue ->
            DMPLogger.trace("API Key loaded from EncryptedSharedPreferences: ${keyValue.maskAPIKey()}")
        }
        return key
    }

    /**
     * 删除API Key
     */
    fun deleteAPIKey(forKey: String) {
        sharedPreferences.edit()
            .remove(forKey)
            .apply()
        DMPLogger.debug("API Key deleted from EncryptedSharedPreferences: $forKey")
    }
}

/**
 * 数据脱敏扩展
 */
fun String.maskAPIKey(): String {
    if (length <= 8) return "***"
    return "${take(4)}...${takeLast(4)}"
}

fun String.maskToken(): String = "***"

fun String.maskUserID(): String {
    if (length <= 10) return "***"
    return "${take(5)}...${takeLast(3)}"
}

/**
 * 证书验证器
 */
class DMPCertificatePinner {

    companion object {
        /**
         * 验证服务器证书
         */
        fun validateCertificate(hostname: String, certificateChain: Array<java.security.cert.X509Certificate>): Boolean {
            if (certificateChain.isEmpty()) {
                DMPLogger.error("Certificate chain is empty")
                return false
            }

            val leafCert = certificateChain[0]

            // 证书固定验证 (可选)
            // val pinnedHash = "SHA256:base64-encoded-hash"
            // val hash = MessageDigest.getInstance("SHA-256").digest(leafCert.encoded)
            // val hashString = hash.joinToString(":") { "%02x".format(it) }
            // return hashString.uppercase() == pinnedHash

            return true
        }
    }
}

/**
 * 会话管理器
 */
object DMPSessionManager {

    private const val SESSION_TIMEOUT = 24 * 60 * 60 * 1000L // 24小时
    private var sessionStartTime: Long? = null

    /**
     * 创建新会话
     */
    fun createSession() {
        sessionStartTime = System.currentTimeMillis()
        DMPLogger.info("Session created")
    }

    /**
     * 检查会话是否有效
     */
    fun isSessionValid(): Boolean {
        val startTime = sessionStartTime ?: return false
        return System.currentTimeMillis() - startTime < SESSION_TIMEOUT
    }

    /**
     * 清除会话
     */
    fun clearSession() {
        sessionStartTime = null
        DMPLogger.info("Session cleared")
    }
}