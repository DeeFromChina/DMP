package com.dmp.android.providers

import com.dmp.android.models.LLMRequest
import com.dmp.android.models.LLMResponse
import kotlinx.coroutines.flow.Flow

/**
 * LLM提供者接口
 */
interface LLMProvider {
    val name: String
    val priority: Int
    
    suspend fun isAvailable(): Boolean
    suspend fun chat(request: LLMRequest): LLMResponse
    fun chatStream(request: LLMRequest): Flow<LLMResponse>
}