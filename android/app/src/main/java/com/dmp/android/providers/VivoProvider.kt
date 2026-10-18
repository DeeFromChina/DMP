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
 * vivo AI开放平台提供者
 */
class VivoProvider(
    private val context: Context,
    private val apiKey: String? = null
) : LLMProvider {
    
    override val name: String = "vivo"
    override val priority: Int = 1
    
    override suspend fun isAvailable(): Boolean {
        val brand = android.os.Build.BRAND.lowercase()
        return brand.contains("vivo") && !apiKey.isNullOrEmpty()
    }
    
    override suspend fun chat(request: LLMRequest): LLMResponse {
        if (!isAvailable()) {
            throw DMPError(
                code = DMPErrorCode.NoProviderAvailable,
                message = "Provider not available: $name"
            )
        }
        
        // TODO: 集成vivo AI开放平台
        val prompt = request.messages.joinToString("\n") { "${it.role}: ${it.content}" }
        
        return LLMResponse(
            content = "[vivo蓝心] $prompt",
            provider = name,
            model = "vivo-lm"
        )
    }
    
    override fun chatStream(request: LLMRequest): Flow<LLMResponse> = flow {
        val response = chat(request)
        response.content.split(" ").forEach { word ->
            emit(LLMResponse(
                content = "$word ",
                provider = name,
                model = "vivo-lm"
            ))
            delay(50)
        }
    }
}