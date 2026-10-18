package com.dmp.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dmp.android.core.DMPLogger
import com.dmp.android.models.LLMRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * DMP AIDL 服务实现
 * 供第三方应用通过 SDK 调用 LLM 能力
 */
class DMPService : Service() {

    companion object {
        private const val TAG = "DMPService"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // AIDL Binder 实现
    private val binder = object : IDMPServer.Stub() {
        
        override fun chat(
            messages: List<Message>,
            temperature: Double,
            maxTokens: Int
        ): LLMResponse {
            DMPLogger.info("AIDL chat 调用: messages=${messages.size}")
            
            val currentRouter = DMPServiceManager.getRouter()
            if (currentRouter == null) {
                DMPLogger.error("Router 未初始化")
                return LLMResponse(
                    "Error: LLM Router not initialized",
                    "",
                    "",
                    "error",
                    0,
                    0
                )
            }

            // 转换为内部 Message 格式
            val internalMessages = messages.map { 
                com.dmp.android.models.Message(it.role, it.content) 
            }.toMutableList()

            val request = LLMRequest(
                messages = internalMessages,
                temperature = temperature,
                maxTokens = maxTokens
            )

            // 同步调用 LLM
            var response: com.dmp.android.models.LLMResponse? = null
            val job = serviceScope.launch {
                try {
                    response = currentRouter.chat(request)
                } catch (e: Exception) {
                    DMPLogger.error("LLM 调用失败: ${e.message}")
                }
            }
            
            // 等待完成
            runBlocking {
                job.join()
            }

            return if (response != null) {
                LLMResponse(
                    response!!.content,
                    response!!.provider,
                    response!!.model,
                    response!!.finishReason,
                    response!!.usage?.promptTokens ?: 0,
                    response!!.usage?.completionTokens ?: 0
                )
            } else {
                LLMResponse(
                    "Error: LLM request failed",
                    "",
                    "",
                    "error",
                    0,
                    0
                )
            }
        }

        override fun chatStream(
            messages: List<Message>,
            temperature: Double,
            maxTokens: Int,
            callback: IStreamCallback
        ) {
            DMPLogger.info("AIDL chatStream 调用: messages=${messages.size}")
            
            val currentRouter = DMPServiceManager.getRouter()
            if (currentRouter == null) {
                DMPLogger.error("Router 未初始化")
                callback.onError("LLM Router not initialized")
                return
            }

            // 转换为内部 Message 格式
            val internalMessages = messages.map { 
                com.dmp.android.models.Message(it.role, it.content) 
            }.toMutableList()

            val request = LLMRequest(
                messages = internalMessages,
                temperature = temperature,
                maxTokens = maxTokens
            )

            // 流式调用
            serviceScope.launch {
                try {
                    currentRouter.chatStream(request).collect { chunk ->
                        callback.onChunk(chunk.content)
                    }
                    callback.onComplete()
                } catch (e: Exception) {
                    DMPLogger.error("流式调用失败: ${e.message}")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }

        override fun isRunning(): Boolean {
            return DMPServiceManager.getRouter() != null
        }

        override fun getActiveProvider(): String {
            return ""
        }

        override fun getCurrentModel(): String {
            return ""
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        DMPLogger.info("DMPService 被绑定")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DMPLogger.info("DMPService 被解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DMPLogger.info("DMPService 销毁")
        serviceJob.cancel()
        super.onDestroy()
    }
}