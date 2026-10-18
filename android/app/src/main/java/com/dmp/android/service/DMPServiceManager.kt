package com.dmp.android.service

import com.dmp.android.core.DMPLogger
import com.dmp.android.router.LLMRouter

/**
 * DMP 服务管理器（单例）
 * 用于在 Activity 和 Service 之间共享 LLMRouter
 */
object DMPServiceManager {
    
    private const val TAG = "DMPServiceManager"
    
    @Volatile
    private var router: LLMRouter? = null
    
    /**
     * 设置 LLMRouter
     */
    fun setRouter(newRouter: LLMRouter) {
        router = newRouter
        DMPLogger.info("DMPServiceManager Router 已设置")
    }
    
    /**
     * 获取 LLMRouter
     */
    fun getRouter(): LLMRouter? {
        return router
    }
    
    /**
     * 清除 Router
     */
    fun clearRouter() {
        router = null
        DMPLogger.info("DMPServiceManager Router 已清除")
    }
}