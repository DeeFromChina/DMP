package com.dmp.android.core

import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.min

/**
 * 错误码枚举
 */
enum class DMPErrorCode(val value: Int) {
    // 网络错误 1000-1999
    NetworkTimeout(1001),
    NetworkUnavailable(1002),
    SSLCertificateError(1003),

    // 认证错误 2000-2999
    InvalidAPIKey(2001),
    TokenExpired(2002),
    PermissionDenied(2003),

    // 配置错误 3000-3999
    ConfigMissing(3001),
    ConfigInvalid(3002),

    // 业务错误 4000-4999
    NoProviderAvailable(4001),
    RequestRejected(4002),
    InvalidResponse(4003),

    // 系统错误 5000-5999
    OutOfMemory(5001),
    StorageFull(5002)
}

/**
 * DMP错误
 */
class DMPError(
    val code: DMPErrorCode,
    message: String,
    val underlyingError: Throwable? = null,
    traceID: String? = null
) : Exception(message) {

    val traceID: String = traceID ?: DMPLogger.traceID ?: "N/A"
    val timestamp: Date = Date()

    init {
        // 记录错误日志
        DMPLogger.error("[$code.value] $message (TraceID: $traceID)")
    }

    /**
     * 是否可重试
     */
    val isRetryable: Boolean
        get() = when (code) {
            DMPErrorCode.NetworkTimeout,
            DMPErrorCode.NetworkUnavailable -> true
            else -> false
        }

    override val message: String
        get() = "[${code.value}] ${super.message} (TraceID: $traceID)"
}

/**
 * 错误追踪器
 */
object DMPErrorTracker {

    private val errorHistory = ConcurrentLinkedQueue<DMPError>()
    private const val MAX_HISTORY_COUNT = 100
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 记录错误
     */
    fun trackError(error: DMPError) {
        executor.execute {
            errorHistory.add(error)

            // 限制历史记录数量
            while (errorHistory.size > MAX_HISTORY_COUNT) {
                errorHistory.poll()
            }

            // 分析错误模式
            analyzeErrorPattern()
        }
    }

    /**
     * 获取错误统计
     */
    fun getErrorStats(): ErrorStats {
        val totalErrors = errorHistory.size
        val retryableErrors = errorHistory.count { it.isRetryable }
        val networkErrors = errorHistory.count { it.code.value in 1000..1999 }
        val authErrors = errorHistory.count { it.code.value in 2000..2999 }

        return ErrorStats(
            totalErrors = totalErrors,
            retryableErrors = retryableErrors,
            networkErrors = networkErrors,
            authErrors = authErrors
        )
    }

    /**
     * 分析错误模式
     */
    private fun analyzeErrorPattern() {
        val recentErrors = errorHistory.takeLast(5)
        if (recentErrors.isEmpty()) return

        val lastError = recentErrors.last()
        val sameCodeErrors = recentErrors.filter { it.code == lastError.code }

        if (sameCodeErrors.size >= 3) {
            DMPLogger.warn("Detected repeated error pattern: ${sameCodeErrors.first().code.value}")
            // 可以触发告警或自动降级
        }
    }

    /**
     * 清除错误历史
     */
    fun clearHistory() {
        errorHistory.clear()
    }
}

/**
 * 错误统计
 */
data class ErrorStats(
    val totalErrors: Int,
    val retryableErrors: Int,
    val networkErrors: Int,
    val authErrors: Int
) {
    val errorRate: Double
        get() = if (totalErrors > 0) (networkErrors + authErrors).toDouble() / totalErrors else 0.0
}

/**
 * 重试策略
 */
data class DMPPRetryPolicy(
    val maxRetries: Int = 3,
    val initialDelay: Long = 1000, // 毫秒
    val maxDelay: Long = 30000, // 毫秒
    val backoffMultiplier: Double = 2.0
) {
    companion object {
        val Default = DMPPRetryPolicy()
    }

    /**
     * 判断是否应该重试
     */
    fun shouldRetry(error: Throwable, attempt: Int): Boolean {
        if (attempt >= maxRetries) return false

        return when (error) {
            is DMPError -> error.isRetryable
            else -> false
        }
    }

    /**
     * 计算重试延迟
     */
    fun delayFor(attempt: Int): Long {
        val delay = (initialDelay * backoffMultiplier.pow(attempt)).toLong()
        return min(delay, maxDelay)
    }
}

/**
 * 带重试的协程执行
 */
suspend fun <T> withRetry(
    policy: DMPPRetryPolicy = DMPPRetryPolicy.Default,
    operation: suspend () -> T
): T {
    var lastError: Throwable? = null

    for (attempt in 0 until policy.maxRetries) {
        try {
            return operation()
        } catch (e: Throwable) {
            lastError = e

            if (!policy.shouldRetry(e, attempt)) {
                throw e
            }

            val delay = policy.delayFor(attempt)
            DMPLogger.warn("Retry attempt ${attempt + 1}/${policy.maxRetries} after ${delay}ms: ${e.message}")

            kotlinx.coroutines.delay(delay)
        }
    }

    throw lastError ?: DMPError(
        code = DMPErrorCode.NoProviderAvailable,
        message = "All retry attempts failed"
    )
}