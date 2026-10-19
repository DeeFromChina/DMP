import Foundation

/// Apple Intelligence提供者 (iOS 18+)
public final class AppleIntelligenceProvider: LLMProvider {
    public var name: String = "apple_intelligence"
    public var priority: Int = 1
    
    public init() {}
    
    public func isAvailable() async -> Bool {
        #if os(iOS)
        guard #available(iOS 18.0, *) else {
            return false
        }
        
        // 检查设备是否支持Apple Intelligence
        let deviceType = DeviceDetector.detectCurrentDevice()
        if case .iphonePro = deviceType {
            return true
        }
        
        return false
        #else
        return false
        #endif
    }
    
    public func chat(request: LLMRequest) async throws -> LLMResponse {
        guard await isAvailable() else {
            throw DMPError.providerNotAvailable(name)
        }
        
        // TODO: 集成Apple Intelligence API
        // 目前使用模拟实现
        let prompt = request.messages.map { "\($0.role): \($0.content)" }.joined(separator: "\n")
        
        return LLMResponse(
            content: "[Apple Intelligence] \(prompt.prefix(50))...",
            provider: name,
            model: "apple-intelligence-1"
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
                            model: self.name
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