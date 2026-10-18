import Foundation

/// 优雅降级链管理器
///
/// 当首选 Provider 失败时，自动按降级链尝试下一个
/// 避免单点故障导致整个服务不可用
public final class FallbackChain {
    
    /// 降级链配置
    public struct ChainConfig {
        public let providers: [LLMProvider]
        public let maxRetries: Int
        public let timeoutMs: TimeInterval
        public let fallbackStrategy: FallbackStrategy
        
        public init(
            providers: [LLMProvider],
            maxRetries: Int = 2,
            timeoutMs: TimeInterval = 30,
            fallbackStrategy: FallbackStrategy = .sequential
        ) {
            self.providers = providers
            self.maxRetries = maxRetries
            self.timeoutMs = timeoutMs
            self.fallbackStrategy = fallbackStrategy
        }
    }
    
    /// 降级策略
    public enum FallbackStrategy {
        case sequential     // 顺序尝试
        case parallel       // 并行尝试（取最快的）
        case weighted       // 加权尝试（根据历史成功率）
    }
    
    /// 降级结果
    public struct FallbackResult {
        public let success: Bool
        public let response: LLMResponse?
        public let usedProvider: String?
        public let fallbackCount: Int
        public let totalDurationMs: Int64
        public let errors: [ProviderError]
    }
    
    /// Provider 错误信息
    public struct ProviderError {
        public let providerName: String
        public let errorMessage: String
        public let errorType: ErrorType
        public let timestamp: Date
        
        public enum ErrorType {
            case timeout
            case networkError
            case modelNotLoaded
            case outOfMemory
            case unknown
        }
    }
    
    /// Provider 成功率统计（用于加权策略）
    private var providerStats: [String: ProviderStats] = [:]
    
    public struct ProviderStats {
        public var totalRequests: Int = 0
        public var successRequests: Int = 0
        public var avgResponseTimeMs: Int64 = 0
    }
    
    // MARK: - Public Methods
    
    /// 执行降级链请求
    public func executeWithFallback(
        request: LLMRequest,
        config: ChainConfig
    ) async -> FallbackResult {
        let startTime = Date()
        var errors: [ProviderError] = []
        
        // 根据策略排序 providers
        let orderedProviders = sortProviders(config.providers)
        
        var fallbackCount = 0
        
        for provider in orderedProviders {
            if errors.count > config.maxRetries {
                print("[FallbackChain] 超过最大重试次数，停止尝试")
                break
            }
            
            do {
                print("[FallbackChain] 尝试使用 Provider: \(provider.name)")
                
                // 检查可用性
                guard await provider.isAvailable() else {
                    print("[FallbackChain] Provider \(provider.name) 不可用，跳过")
                    errors.append(
                        ProviderError(
                            providerName: provider.name,
                            errorMessage: "Provider 不可用",
                            errorType: .modelNotLoaded,
                            timestamp: Date()
                        )
                    )
                    fallbackCount += 1
                    continue
                }
                
                // 执行请求
                let response = try await provider.chat(request: request)
                let duration = Int64(Date().timeIntervalSince(startTime) * 1000)
                
                // 记录成功统计
                recordSuccess(providerName: provider.name, durationMs: duration)
                
                return FallbackResult(
                    success: true,
                    response: response,
                    usedProvider: provider.name,
                    fallbackCount: fallbackCount,
                    totalDurationMs: duration,
                    errors: errors
                )
                
            } catch {
                let errorType = classifyError(error)
                print("[FallbackChain] Provider \(provider.name) 失败: \(error.localizedDescription)")
                
                errors.append(
                    ProviderError(
                        providerName: provider.name,
                        errorMessage: error.localizedDescription,
                        errorType: errorType,
                        timestamp: Date()
                    )
                )
                
                fallbackCount += 1
                recordFailure(providerName: provider.name)
            }
        }
        
        // 所有 Provider 都失败了
        let totalDuration = Int64(Date().timeIntervalSince(startTime) * 1000)
        return FallbackResult(
            success: false,
            response: nil,
            usedProvider: nil,
            fallbackCount: fallbackCount,
            totalDurationMs: totalDuration,
            errors: errors
        )
    }
    
    /// 获取统计报告
    public func getStatsReport() -> [String: ProviderStats] {
        return providerStats
    }
    
    /// 重置统计
    public func resetStats() {
        providerStats.removeAll()
    }
    
    // MARK: - Private Methods
    
    /// 分类错误类型
    private func classifyError(_ error: Error) -> ProviderError.ErrorType {
        let message = error.localizedDescription.lowercased()
        if message.contains("timeout") {
            return .timeout
        } else if message.contains("network") {
            return .networkError
        } else if message.contains("model") || message.contains("load") {
            return .modelNotLoaded
        } else if message.contains("memory") {
            return .outOfMemory
        } else {
            return .unknown
        }
    }
    
    /// 记录成功
    private func recordSuccess(providerName: String, durationMs: Int64) {
        var stats = providerStats[providerName, default: ProviderStats()]
        stats.totalRequests += 1
        stats.successRequests += 1
        stats.avgResponseTimeMs = (stats.avgResponseTimeMs + durationMs) / 2
        providerStats[providerName] = stats
    }
    
    /// 记录失败
    private func recordFailure(providerName: String) {
        var stats = providerStats[providerName, default: ProviderStats()]
        stats.totalRequests += 1
        providerStats[providerName] = stats
    }
    
    /// 根据策略排序 providers
    private func sortProviders(_ providers: [LLMProvider]) -> [LLMProvider] {
        // 默认使用顺序策略
        return providers
    }
}