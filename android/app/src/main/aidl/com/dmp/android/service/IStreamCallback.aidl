package com.dmp.android.service;

/**
 * 流式输出回调接口
 */
oneway interface IStreamCallback {
    /**
     * 接收到新的内容块
     */
    void onChunk(String content);
    
    /**
     * 流式输出完成
     */
    void onComplete();
    
    /**
     * 发生错误
     */
    void onError(String error);
}