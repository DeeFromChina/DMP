import Foundation

/// LLM请求
public struct LLMRequest: Codable {
    public let messages: [Message]
    public let temperature: Double
    public let maxTokens: Int
    public let stream: Bool
    public let model: String?
    
    public init(
        messages: [Message],
        temperature: Double = 0.7,
        maxTokens: Int = 2048,
        stream: Bool = false,
        model: String? = nil
    ) {
        self.messages = messages
        self.temperature = temperature
        self.maxTokens = maxTokens
        self.stream = stream
        self.model = model
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
    
    public init(
        content: String,
        provider: String,
        model: String,
        usage: UsageInfo? = nil,
        finishReason: String = "stop"
    ) {
        self.content = content
        self.provider = provider
        self.model = model
        self.usage = usage
        self.finishReason = finishReason
    }
}

/// 消息
public struct Message: Codable {
    public let role: String
    public let content: String
    
    public init(role: String, content: String) {
        self.role = role
        self.content = content
    }
}

/// LLM提供者协议
public protocol LLMProvider {
    var name: String { get }
    var priority: Int { get }
    func isAvailable() async -> Bool
    func chat(request: LLMRequest) async throws -> LLMResponse
    func chatStream(request: LLMRequest) -> AsyncStream<LLMResponse>
}

/// DMP错误
public enum DMPError: Error, LocalizedError {
    case providerNotAvailable(String)
    case allProvidersFailed
    case timeout
    case modelNotLoaded
    case invalidRequest(String)
    case networkError(Error)
    
    public var errorDescription: String? {
        switch self {
        case .providerNotAvailable(let name):
            return "LLM提供者不可用: \(name)"
        case .allProvidersFailed:
            return "所有LLM提供者都失败了"
        case .timeout:
            return "请求超时"
        case .modelNotLoaded:
            return "模型未加载"
        case .invalidRequest(let message):
            return "无效请求: \(message)"
        case .networkError(let error):
            return "网络错误: \(error.localizedDescription)"
        }
    }
}