import Foundation

/// 本地LLM引擎协议
public protocol LocalLLMEngine {
    /// 加载模型
    func loadModel(path: String) async throws
    
    /// 非流式推理
    func chat(messages: [Message]) async throws -> LLMResponse
    
    /// 流式推理
    func chatStream(messages: [Message]) -> AsyncStream<LLMResponse>
    
    /// 检查模型是否已加载
    var isModelLoaded: Bool { get }
}

/// Core ML 本地LLM实现
public final class CoreMLLocalLLM: LocalLLMEngine {
    private var model: MLModel?
    private let modelName: String
    
    public var isModelLoaded: Bool {
        return model != nil
    }
    
    public init(modelName: String = "llm-quantized") {
        self.modelName = modelName
    }
    
    public func loadModel(path: String) async throws {
        let url = URL(fileURLWithPath: path)
        let config = MLModelConfiguration()
        config.computeUnits = .all  // 使用所有可用计算单元(CPU+GPU+Neural Engine)
        
        self.model = try await MLModel(contentsOf: url, configuration: config)
        print("✅ Core ML model loaded: \(modelName)")
    }
    
    public func chat(messages: [Message]) async throws -> LLMResponse {
        guard let model = model else {
            throw DMPError.modelNotLoaded
        }
        
        // 合并消息为单个prompt
        let prompt = messages.map { "\($0.role.rawValue): \($0.content)" }.joined(separator: "\n")
        
        // 创建输入
        let input = try MLDictionaryFeatureProvider(dictionary: ["text": prompt])
        
        // 推理
        let output = try model.prediction(from: input)
        let content = output.featureValue(for: "text")?.stringValue ?? ""
        
        return LLMResponse(
            content: content,
            provider: "apple",
            model: modelName,
            finishReason: "stop"
        )
    }
    
    public func chatStream(messages: [Message]) -> AsyncStream<LLMResponse> {
        AsyncStream { continuation in
            Task {
                do {
                    let response = try await self.chat(messages: messages)
                    // 模拟流式输出(逐词)
                    let words = response.content.split(separator: " ")
                    for word in words {
                        continuation.yield(LLMResponse(
                            content: String(word) + " ",
                            provider: "apple",
                            model: self.modelName,
                            finishReason: "stop"
                        ))
                        try await Task.sleep(nanoseconds: 50_000_000) // 50ms延迟
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}