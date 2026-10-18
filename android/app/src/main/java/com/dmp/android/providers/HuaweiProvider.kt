package com.dmp.android.providers

import android.content.Context
import com.dmp.android.core.DMPError
import com.dmp.android.core.DMPErrorCode
import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 华为HMS Core AI提供者
 */
class HuaweiProvider(
    private val context: Context,
    private val apiKey: String? = null
) : LLMProvider {
    
    override val name: String = "huawei"
    override val priority: Int = 1
    
    override suspend fun isAvailable(): Boolean {
        // 检查是否有HMS Core
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo("com.huawei.hms.core", 0)
            !apiKey.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun chat(request: LLMRequest): LLMResponse {
        if (!isAvailable()) {
            throw DMPError(
                code = DMPErrorCode.NoProviderAvailable,
                message = "Huawei provider not available"
            )
        }
        
        // TODO: 集成华为HMS Core AI
        val prompt = request.messages.joinToString("\n") { "${it.role}: ${it.content}" }
        
        return LLMResponse(
            content = "[华为盘古] $prompt",
            provider = name,
            model = "pangu-7b"
        )
    }
    
    override fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        val response = chat(request)
        response.content.split(" ").forEach { word ->
            emit(LLMResponse(
                content = "$word ",
                provider = name,
                model = "pangu-7b"
            ))
            delay(50)
        }
    }
}