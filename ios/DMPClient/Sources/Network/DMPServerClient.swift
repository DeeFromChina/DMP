import Foundation

/// MCP协议客户端
public final class DMPServerClient {
    private let baseURL: String
    private let session: URLSession
    private let timeout: TimeInterval
    
    public init(baseURL: String, timeout: TimeInterval = 30) {
        self.baseURL = baseURL
        self.timeout = timeout
        
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = timeout
        self.session = URLSession(configuration: config)
    }
    
    /// 非流式聊天
    public func chat(messages: [Message]) async throws -> LLMResponse {
        let request = LLMRequest(messages: messages, stream: false)
        let response = try await sendMCPRequest(request: request)
        return response
    }
    
    /// 流式聊天
    public func chatStream(messages: [Message]) -> AsyncThrowingStream<LLMResponse, Error> {
        AsyncThrowingStream { continuation in
            Task {
                do {
                    let request = LLMRequest(messages: messages, stream: true)
                    try await self.sendSSEStream(request: request) { response in
                        continuation.yield(response)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
    
    /// 获取提供者状态
    public func getProviderStatus() async throws -> [ProviderStatus] {
        let url = URL(string: "\(baseURL)/status")!
        let (data, _) = try await session.data(from: url)
        
        let decoder = JSONDecoder()
        return try decoder.decode([ProviderStatus].self, from: data)
    }
    
    // MARK: - Private Methods
    
    private func sendMCPRequest(request: LLMRequest) async throws -> LLMResponse {
        let url = URL(string: "\(baseURL)/v1/chat")!
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let encoder = JSONEncoder()
        urlRequest.httpBody = try encoder.encode(request)
        
        let (data, response) = try await session.data(for: urlRequest)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw DMPError.invalidResponse("Invalid HTTP response")
        }
        
        guard httpResponse.statusCode == 200 else {
            throw DMPError.serverError("HTTP \(httpResponse.statusCode)")
        }
        
        let decoder = JSONDecoder()
        return try decoder.decode(LLMResponse.self, from: data)
    }
    
    private func sendSSEStream(request: LLMRequest, onChunk: @escaping (LLMResponse) -> Void) async throws {
        let url = URL(string: "\(baseURL)/v1/chat/stream")!
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        
        let encoder = JSONEncoder()
        urlRequest.httpBody = try encoder.encode(request)
        
        let (bytes, response) = try await session.bytes(for: urlRequest)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw DMPError.serverError("Failed to connect to stream")
        }
        
        // 解析SSE流
        for try await line in bytes.lines {
            if line.hasPrefix("data: ") {
                let jsonData = String(line.dropFirst(6))
                if jsonData == "[DONE]" {
                    break
                }
                
                let decoder = JSONDecoder()
                if let data = jsonData.data(using: .utf8),
                   let chunk = try? decoder.decode(LLMResponse.self, from: data) {
                    onChunk(chunk)
                }
            }
        }
    }
}