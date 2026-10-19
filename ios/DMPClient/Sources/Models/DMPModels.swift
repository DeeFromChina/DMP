import Foundation

// MARK: - 数据模型

/// 消息角色
public enum MessageRole: String, Codable {
    case system
    case user
    case assistant
    case tool
}

/// 对话消息
public struct Message: Codable {
    public let role: MessageRole
    public let content: String
    public let name: String?
    
    public init(role: MessageRole, content: String, name: String? = nil) {
        self.role = role
        self.content = content
        self.name = name
    }
}

/// LLM请求
public struct LLMRequest: Codable {
    public let messages: [Message]
    public let temperature: Double
    public let maxTokens: Int
    public let stream: Bool
    public let model: String?
    public let preferredProvider: ProviderType?
    
    public init(
        messages: [Message],
        temperature: Double = 0.7,
        maxTokens: Int = 2048,
        stream: Bool = false,
        model: String? = nil,
        preferredProvider: ProviderType? = nil
    ) {
        self.messages = messages
        self.temperature = temperature
        self.maxTokens = maxTokens
        self.stream = stream
        self.model = model
        self.preferredProvider = preferredProvider
    }
}

/// LLM响应
public struct LLMResponse: Codable {
    public let content: String
    public let provider: String
    public let model: String
    public let usage: UsageInfo?
    public let finishReason: String
    
    public struct UsageInfo: Codable {
        public let promptTokens: Int
        public let completionTokens: Int
        public let totalTokens: Int
    }
}

/// 提供者类型
public enum ProviderType: String, Codable {
    case local
    case mobile
    case cloud
    case any
}

/// 提供者状态
public struct ProviderStatus: Codable {
    public let name: String
    public let priority: Int
    public let available: Bool
    public let type: ProviderType
}

// MARK: - 错误定义

public enum DMPError: Error, LocalizedError {
    case networkError(Error)
    case serverError(String)
    case providerNotAvailable(String)
    case timeout
    case modelNotLoaded
    case invalidResponse(String)
    case authenticationFailed
    case allProvidersFailed
    
    public var errorDescription: String? {
        switch self {
        case .networkError(let error):
            return "网络错误: \(error.localizedDescription)"
        case .serverError(let message):
            return "服务器错误: \(message)"
        case .providerNotAvailable(let name):
            return "提供者不可用: \(name)"
        case .timeout:
            return "请求超时"
        case .modelNotLoaded:
            return "模型未加载"
        case .invalidResponse(let message):
            return "无效响应: \(message)"
        case .authenticationFailed:
            return "认证失败"
        case .allProvidersFailed:
            return "所有提供者都失败了"
        }
    }
}

// MARK: - 配置

public struct DMPConfiguration {
    public let serverURL: String
    public let localLLM: LocalLLMConfig
    public let fallback: FallbackConfig
    
    public struct LocalLLMConfig {
        public let enabled: Bool
        public let modelPath: String?
        public let maxTokens: Int
        public let temperature: Double
        
        public init(
            enabled: Bool = true,
            modelPath: String? = nil,
            maxTokens: Int = 2048,
            temperature: Double = 0.7
        ) {
            self.enabled = enabled
            self.modelPath = modelPath
            self.maxTokens = maxTokens
            self.temperature = temperature
        }
    }
    
    public struct FallbackConfig {
        public let enabled: Bool
        public let strategy: FallbackStrategy
        public let timeout: TimeInterval
        public let maxRetries: Int
        
        public enum FallbackStrategy: String {
            case priority
            case roundRobin
            case random
        }
        
        public init(
            enabled: Bool = true,
            strategy: FallbackStrategy = .priority,
            timeout: TimeInterval = 30,
            maxRetries: Int = 3
        ) {
            self.enabled = enabled
            self.strategy = strategy
            self.timeout = timeout
            self.maxRetries = maxRetries
        }
    }
    
    public init(
        serverURL: String,
        localLLM: LocalLLMConfig = .init(),
        fallback: FallbackConfig = .init()
    ) {
        self.serverURL = serverURL
        self.localLLM = localLLM
        self.fallback = fallback
    }
}