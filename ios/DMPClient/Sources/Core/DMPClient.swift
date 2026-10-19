import Foundation

/// DMP主客户端
public final class DMPClient {
    private let config: DMPConfiguration
    private let serverClient: DMPServerClient
    private var localEngine: LocalLLMEngine?
    
    /// 初始化客户端
    public init(config: DMPConfiguration) {
        self.config = config
        self.serverClient = DMPServerClient(
            baseURL: config.serverURL,
            timeout: config.fallback.timeout
        )
        
        // 初始化本地LLM引擎
        if config.localLLM.enabled, let modelPath = config.localLLM.modelPath {
            self.localEngine = CoreMLLocalLLM()
            
            // 异步加载模型
            Task {
                do {
                    try await self.localEngine?.loadModel(path: modelPath)
                } catch {
                    print("⚠️ Failed to load local model: \(error)")
                }
            }
        }
    }
    
    /// 非流式聊天(自动回退)
    public func chat(messages: [Message]) async throws -> LLMResponse {
        // 1. 优先尝试本地LLM
        if let localEngine = localEngine, localEngine.isModelLoaded {
            do {
                print("📱 Trying local LLM...")
                let response = try await localEngine.chat(messages: messages)
                print("✅ Got response from local LLM")
                return response
            } catch {
                print("⚠️ Local LLM failed: \(error), falling back to server")
            }
        }
        
        // 2. 回退到服务端
        print("🌐 Trying server...")
        let response = try await serverClient.chat(messages: messages)
        print("✅ Got response from server")
        return response
    }
    
    /// 流式聊天(自动回退)
    public func chatStream(messages: [Message]) -> AsyncThrowingStream<LLMResponse, Error> {
        AsyncThrowingStream { continuation in
            Task {
                // 1. 优先尝试本地LLM
                if let localEngine = localEngine, localEngine.isModelLoaded {
                    print("📱 Streaming from local LLM...")
                    for try await chunk in localEngine.chatStream(messages: messages) {
                        continuation.yield(chunk)
                    }
                    continuation.finish()
                    return
                }
                
                // 2. 回退到服务端
                print("🌐 Streaming from server...")
                for try await chunk in serverClient.chatStream(messages: messages) {
                    continuation.yield(chunk)
                }
                continuation.finish()
            }
        }
    }
    
    /// 获取提供者状态
    public func getProviderStatus() async throws -> [ProviderStatus] {
        return try await serverClient.getProviderStatus()
    }
    
    /// 健康检查
    public func healthCheck() async -> Bool {
        // 检查本地模型
        if config.localLLM.enabled {
            if let localEngine = localEngine, localEngine.isModelLoaded {
                return true
            }
        }
        
        // 检查服务端
        do {
            _ = try await serverClient.getProviderStatus()
            return true
        } catch {
            return false
        }
    }
}