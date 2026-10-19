import Foundation

/// LLM路由器 - 根据设备型号自动选择最优LLM提供者
public final class LLMRouter {
    private var providers: [LLMProvider] = []
    
    public init() {
        DMPLogger.shared.info("LLMRouter initialized", file: #file, function: #function, line: #line)
    }
    
    /// 注册提供者
    public func register(_ provider: LLMProvider) {
        providers.append(provider)
        providers.sort { $0.priority < $1.priority }
        DMPLogger.shared.info("注册LLM提供者: \(provider.name) (优先级: \(provider.priority))", 
                             file: #file, function: #function, line: #line)
    }
    
    /// 根据设备类型自动注册推荐的提供者
    public func autoRegister(
        coreMLModelPath: String? = nil,
        cloudAPIKey: String? = nil,
        cloudBaseURL: String? = nil,
        cloudModelName: String? = nil
    ) {
        let deviceType = DeviceDetector.detectCurrentDevice()
        let recommendedProviders = DeviceDetector.getRecommendedProviders(deviceType: deviceType)
        
        // 获取详细设备和系统信息
        let deviceInfo: String
        #if os(iOS)
        let deviceModel = UIDevice.current.modelIdentifier
        let systemVersion = ProcessInfo.processInfo.operatingSystemVersion
        deviceInfo = "\(deviceModel) - iOS \(systemVersion.majorVersion).\(systemVersion.minorVersion).\(systemVersion.patchVersion)"
        #else
        deviceInfo = "macOS"
        #endif
        
        DMPLogger.shared.info("设备: \(deviceInfo), 推荐提供者: \(recommendedProviders)", 
                             file: #file, function: #function, line: #line)
        
        // 清空现有提供者
        providers.removeAll()
        
        for providerName in recommendedProviders {
            switch providerName {
            case "apple_intelligence":
                register(AppleIntelligenceProvider())
                
            case "coreml":
                if let modelPath = coreMLModelPath {
                    register(CoreMLProvider(modelPath: modelPath))
                } else {
                    DMPLogger.shared.warn("Core ML模型路径未提供,跳过注册", 
                                         file: #file, function: #function, line: #line)
                }
                
            case "cloud":
                if let apiKey = cloudAPIKey {
                    register(CloudProvider(
                        apiKey: apiKey,
                        baseURL: cloudBaseURL ?? "https://api.openai.com/v1",
                        modelName: cloudModelName ?? "gpt-3.5-turbo"
                    ))
                } else {
                    DMPLogger.shared.warn("云端API密钥未提供,跳过注册", 
                                         file: #file, function: #function, line: #line)
                }
                
            default:
                DMPLogger.shared.warn("未知的提供者类型: \(providerName)", 
                                     file: #file, function: #function, line: #line)
            }
        }
    }
    
    /// 聊天请求 (自动回退)
    public func chat(request: LLMRequest) async throws -> LLMResponse {
        let traceID = UUID().uuidString
        let startTime = Date()
        
        DMPLogger.shared.info("开始处理聊天请求 (TraceID: \(traceID))", 
                             file: #file, function: #function, line: #line)
        
        return try await DMPLogger.shared.withTraceID(traceID) {
            var lastError: Error?
            var attemptedProviders: [String] = []
            
            for provider in providers {
                do {
                    DMPLogger.shared.debug("尝试提供者: \(provider.name) (TraceID: \(traceID))", 
                                          file: #file, function: #function, line: #line)
                    attemptedProviders.append(provider.name)
                    
                    guard await provider.isAvailable() else {
                        DMPLogger.shared.warn("提供者不可用: \(provider.name) (TraceID: \(traceID))", 
                                             file: #file, function: #function, line: #line)
                        continue
                    }
                    
                    let response = try await provider.chat(request: request)
                    let duration = Date().timeIntervalSince(startTime)
                    
                    DMPLogger.shared.info("成功使用提供者: \(provider.name), 耗时: \(String(format: "%.2f", duration))s (TraceID: \(traceID))", 
                                         file: #file, function: #function, line: #line)
                    
                    return response
                    
                } catch {
                    DMPLogger.shared.error("提供者失败: \(provider.name), 错误: \(error.localizedDescription) (TraceID: \(traceID))", 
                                          file: #file, function: #function, line: #line)
                    lastError = error
                    continue
                }
            }
            
            let duration = Date().timeIntervalSince(startTime)
            DMPLogger.shared.error("所有提供者均失败, 尝试: \(attemptedProviders.joined(separator: ", ")), 耗时: \(String(format: "%.2f", duration))s (TraceID: \(traceID))", 
                                  file: #file, function: #function, line: #line)
            
            throw DMPError.allProvidersFailed
        }
    }
    
    /// 流式聊天请求 (自动回退)
    public func chatStream(request: LLMRequest) -> AsyncStream<LLMResponse> {
        let traceID = UUID().uuidString
        let startTime = Date()
        
        DMPLogger.shared.info("开始流式聊天请求 (TraceID: \(traceID))", 
                             file: #file, function: #function, line: #line)
        
        return AsyncStream { continuation in
            Task {
                do {
                    try await DMPLogger.shared.withTraceID(traceID) {
                        var lastError: Error?
                        
                        for provider in self.providers {
                            do {
                                DMPLogger.shared.debug("流式尝试: \(provider.name) (TraceID: \(traceID))", 
                                                      file: #file, function: #function, line: #line)
                                
                                guard await provider.isAvailable() else {
                                    DMPLogger.shared.warn("流式提供者不可用: \(provider.name) (TraceID: \(traceID))", 
                                                         file: #file, function: #function, line: #line)
                                    continue
                                }
                                
                                for await chunk in provider.chatStream(request: request) {
                                    continuation.yield(chunk)
                                }
                                
                                let duration = Date().timeIntervalSince(startTime)
                                DMPLogger.shared.info("流式成功: \(provider.name), 耗时: \(String(format: "%.2f", duration))s (TraceID: \(traceID))", 
                                                     file: #file, function: #function, line: #line)
                                continuation.finish()
                                return
                                
                            } catch {
                                DMPLogger.shared.error("流式提供者失败: \(provider.name), 错误: \(error.localizedDescription) (TraceID: \(traceID))", 
                                                      file: #file, function: #function, line: #line)
                                lastError = error
                                continue
                            }
                        }
                        
                        DMPLogger.shared.error("所有流式提供者均失败 (TraceID: \(traceID))", 
                                              file: #file, function: #function, line: #line)
                        continuation.finish(throwing: DMPError.allProvidersFailed)
                    }
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
    
    /// 获取提供者状态
    public func getProviderStatus() async -> [String: Bool] {
        var status: [String: Bool] = [:]
        
        for provider in providers {
            let isAvailable = await provider.isAvailable()
            status[provider.name] = isAvailable
            
            DMPLogger.shared.debug("提供者状态: \(provider.name) = \(isAvailable ? "可用" : "不可用")", 
                                  file: #file, function: #function, line: #line)
        }
        
        return status
    }
}