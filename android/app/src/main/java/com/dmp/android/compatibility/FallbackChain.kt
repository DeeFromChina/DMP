package com.dmp.android.compatibility

import android.util.Log
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import com.dmp.android.providers.LLMProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 优雅降级链管理器
 * 
 * 当首选 Provider 失败时，自动按降级链尝试下一个
 * 避免单点故障导致整个服务不可用
 */
class FallbackChain {
    
    companion object {
        private const val TAG = "FallbackChain"
    }
    
    /**
     * 降级链配置
     */
    data class ChainConfig(
        val providers: List<LLMProvider>,
        val maxRetries: Int = 2,
        val timeoutMs: Long = 30000,
        val fallbackStrategy: FallbackStrategy = FallbackStrategy.SEQUENTIAL
    )
    
    /**
     * 降级策略
     */
    enum class FallbackStrategy {
        SEQUENTIAL,     // 顺序尝试
        PARALLEL,       // 并行尝试（取最快的）
        WEIGHTED        // 加权尝试（根据历史成功率）
    }
    
    /**
     * 降级结果
     */
    data class FallbackResult(
        val success: Boolean,
        val response: LLMResponse?,
        val usedProvider: String?,
        val fallbackCount: Int,
        val totalDurationMs: Long,
        val errors: List<ProviderError>
    )
    
    /**
     * Provider 错误信息
     */
    data class ProviderError(
        val providerName: String,
        val errorMessage: String,
        val errorType: ErrorType,
        val timestamp: Long
    )
    
    /**
     * 错误类型
     */
    enum class ErrorType {
        TIMEOUT,
        NETWORK_ERROR,
        MODEL_NOT_LOADED,
        OUT_OF_MEMORY,
        UNKNOWN
    }
    
    /**
     * Provider 成功率统计（用于加权策略）
     */
    private val providerStats = mutableMapOf<String, ProviderStats>()
    
    data class ProviderStats(
        var totalRequests: Int = 0,
        var successRequests: Int = 0,
        var avgResponseTimeMs: Long = 0
    )
    
    /**
     * 执行降级链请求
     */
    suspend fun executeWithFallback(
        request: LLMRequest,
        config: ChainConfig
    ): FallbackResult {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<ProviderError>()
        
        // 根据策略排序 providers
        val orderedProviders = when (config.fallbackStrategy) {
            FallbackStrategy.SEQUENTIAL -> config.providers
            FallbackStrategy.PARALLEL -> config.providers  // 并行时顺序不重要
            FallbackStrategy.WEIGHTED -> sortBySuccessRate(config.providers)
        }
        
        var fallbackCount = 0
        
        for (provider in orderedProviders) {
            if (errors.size > config.maxRetries) {
                Log.w(TAG, "超过最大重试次数，停止尝试")
                break
            }
            
            try {
                Log.d(TAG, "尝试使用 Provider: ${provider.name}")
                
                // 检查可用性
                if (!provider.isAvailable()) {
                    Log.w(TAG, "Provider ${provider.name} 不可用，跳过")
                    errors.add(
                        ProviderError(
                            providerName = provider.name,
                            errorMessage = "Provider 不可用",
                            errorType = ErrorType.MODEL_NOT_LOADED,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    fallbackCount++
                    continue
                }
                
                // 执行请求
                val response = provider.chat(request)
                val duration = System.currentTimeMillis() - startTime
                
                // 记录成功统计
                recordSuccess(provider.name, duration)
                
                return FallbackResult(
                    success = true,
                    response = response,
                    usedProvider = provider.name,
                    fallbackCount = fallbackCount,
                    totalDurationMs = duration,
                    errors = errors
                )
                
            } catch (e: Exception) {
                val errorType = classifyError(e)
                Log.e(TAG, "Provider ${provider.name} 失败: ${e.message}", e)
                
                errors.add(
                    ProviderError(
                        providerName = provider.name,
                        errorMessage = e.message ?: "未知错误",
                        errorType = errorType,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                fallbackCount++
                recordFailure(provider.name)
            }
        }
        
        // 所有 Provider 都失败了
        val totalDuration = System.currentTimeMillis() - startTime
        return FallbackResult(
            success = false,
            response = null,
            usedProvider = null,
            fallbackCount = fallbackCount,
            totalDurationMs = totalDuration,
            errors = errors
        )
    }
    
    /**
     * 流式请求的降级处理
     */
    fun streamWithFallback(
        request: LLMRequest,
        config: ChainConfig
    ): Flow<LLMResponse> = flow {
        val errors = mutableListOf<ProviderError>()
        
        for (provider in config.providers) {
            try {
                if (!provider.isAvailable()) {
                    continue
                }
                
                // 尝试流式请求
                provider.chatStream(request).collect { response ->
                    emit(response)
                }
                
                // 成功则返回
                return@flow
                
            } catch (e: Exception) {
                Log.e(TAG, "流式请求失败: ${provider.name}", e)
                errors.add(
                    ProviderError(
                        providerName = provider.name,
                        errorMessage = e.message ?: "未知错误",
                        errorType = classifyError(e),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        
        // 所有 Provider 都失败
        throw Exception("所有 Provider 都失败了: ${errors.joinToString { "${it.providerName}: ${it.errorMessage}" }}")
    }
    
    /**
     * 分类错误类型
     */
    private fun classifyError(e: Exception): ErrorType {
        return when {
            e.message?.contains("timeout", ignoreCase = true) == true -> ErrorType.TIMEOUT
            e.message?.contains("network", ignoreCase = true) == true -> ErrorType.NETWORK_ERROR
            e.message?.contains("model", ignoreCase = true) == true -> ErrorType.MODEL_NOT_LOADED
            e is OutOfMemoryError -> ErrorType.OUT_OF_MEMORY
            else -> ErrorType.UNKNOWN
        }
    }
    
    /**
     * 记录成功
     */
    private fun recordSuccess(providerName: String, durationMs: Long) {
        val stats = providerStats.getOrPut(providerName) { ProviderStats() }
        stats.totalRequests++
        stats.successRequests++
        stats.avgResponseTimeMs = (stats.avgResponseTimeMs + durationMs) / 2
    }
    
    /**
     * 记录失败
     */
    private fun recordFailure(providerName: String) {
        val stats = providerStats.getOrPut(providerName) { ProviderStats() }
        stats.totalRequests++
    }
    
    /**
     * 按成功率排序
     */
    private fun sortBySuccessRate(providers: List<LLMProvider>): List<LLMProvider> {
        return providers.sortedByDescending { provider ->
            val stats = providerStats[provider.name]
            if (stats == null || stats.totalRequests == 0) {
                0.5  // 新 Provider 默认中等优先级
            } else {
                stats.successRequests.toDouble() / stats.totalRequests
            }
        }
    }
    
    /**
     * 获取统计报告
     */
    fun getStatsReport(): Map<String, ProviderStats> {
        return providerStats.toMap()
    }
    
    /**
     * 重置统计
     */
    fun resetStats() {
        providerStats.clear()
    }
}