import Foundation

/// 云端LLM提供者 (可选)
public final class CloudProvider: LLMProvider {
    public var name: String = "cloud"
    public var priority: Int = 3
    
    private let apiKey: String
    private let baseURL: String
    private let modelName: String
    
    public init(
        apiKey: String,
        baseURL: String = "https://api.openai.com/v1",
        modelName: String = "gpt-3.5-turbo"
    ) {
        self.apiKey = apiKey
        self.baseURL = baseURL
        self.modelName = modelName
    }
    
    public func isAvailable() async -> Bool {
        return !apiKey.isEmpty
    }
    
    public func chat(request: LLMRequest) async throws -> LLMResponse {
        guard await isAvailable() else {
            throw DMPError.providerNotAvailable(name)
        }
        
        let url = URL(string: "\(baseURL)/chat/completions")!
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        
        let payload: [String: Any] = [
            "model": request.model ?? modelName,
            "messages": request.messages.map { ["role": $0.role, "content": $0.content] },
            "temperature": request.temperature,
            "max_tokens": request.maxTokens
        ]
        
        urlRequest.httpBody = try JSONSerialization.data(withJSONObject: payload)
        
        let (data, response) = try await URLSession.shared.data(for: urlRequest)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw DMPError.networkError(NSError(domain: "CloudProvider", code: -1, userInfo: nil))
        }
        
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let choices = json?["choices"] as? [[String: Any]]
        let message = choices?.first?["message"] as? [String: Any]
        let content = message?["content"] as? String ?? ""
        
        return LLMResponse(
            content: content,
            provider: name,
            model: request.model ?? modelName
        )
    }
    
    public func chatStream(request: LLMRequest) -> AsyncStream<LLMResponse> {
        AsyncStream { continuation in
            Task {
                do {
                    let response = try await self.chat(request: request)
                    let words = response.content.split(separator: " ")
                    for word in words {
                        continuation.yield(LLMResponse(
                            content: String(word) + " ",
                            provider: self.name,
                            model: self.modelName
                        ))
                        try await Task.sleep(nanoseconds: 50_000_000)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}