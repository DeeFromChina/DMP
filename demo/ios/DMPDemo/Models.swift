import Foundation

struct Message: Codable {
    let role: String
    let content: String
}

struct ChatRequest: Codable {
    let messages: [Message]
    let temperature: Float
    let max_tokens: Int
    let model: String
    let preferred_provider: String
    
    init(messages: [Message], temperature: Float = 0.7, max_tokens: Int = 2048, model: String = "", preferred_provider: String = "any") {
        self.messages = messages
        self.temperature = temperature
        self.max_tokens = max_tokens
        self.model = model
        self.preferred_provider = preferred_provider
    }
}

struct ChatResponse: Codable {
    let content: String
    let provider: String
    let model: String
    let usage: TokenUsage?
    let finish_reason: String
}

struct TokenUsage: Codable {
    let prompt_tokens: Int
    let completion_tokens: Int
    let total_tokens: Int
}

struct StreamChunk: Codable {
    let content: String
    let provider: String
    let model: String
    let finish_reason: String?
}

struct HealthResponse: Codable {
    let status: String
    let server_version: String
    let supported_protocols: [String]
    let providers_count: Int
}