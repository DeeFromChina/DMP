package com.dmp.android.service;

import com.dmp.android.service.Message;
import com.dmp.android.service.LLMResponse;
import com.dmp.android.service.IStreamCallback;

/**
 * DMP LLM 服务 AIDL 接口
 * 供第三方应用通过 SDK 调用，提供高性能 IPC 通信
 */
interface IDMPServer {
    /**
     * 非流式聊天
     */
    LLMResponse chat(in List<Message> messages, double temperature, int maxTokens);
    
    /**
     * 流式聊天
     */
    void chatStream(in List<Message> messages, double temperature, int maxTokens, 
                    in IStreamCallback callback);
    
    /**
     * 检查服务是否运行
     */
    boolean isRunning();
    
    /**
     * 获取当前活跃的提供者
     */
    String getActiveProvider();
    
    /**
     * 获取当前使用的模型
     */
    String getCurrentModel();
}