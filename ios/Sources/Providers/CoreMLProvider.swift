import Foundation
import CoreML

/// Core ML本地模型提供者
///
/// 已集成设备兼容性管理：
/// 1. 根据设备兼容性自动选择最优配置
/// 2. 运行时检测硬件加速能力（ANE/GPU/CPU）
/// 3. 优雅降级（ANE -> GPU -> CPU）
public final class CoreMLProvider: LLMProvider {
    public var name: String = "coreml"
    public var priority: Int = 2
    
    private var model: MLModel?
    private let modelPath: String?
    
    public init(modelPath: String? = nil) {
        self.modelPath = modelPath
    }
    
    public func isAvailable() async -> Bool {
        // 检查模型文件是否存在
        guard let modelPath = modelPath else {
            return false
        }
        
        let url = URL(fileURLWithPath: modelPath)
        guard FileManager.default.fileExists(atPath: url.path) else {
            return false
        }
        
        // 尝试加载模型
        if model == nil {
            do {
                try await loadModel()
                return true
            } catch {
                print("⚠️ Core ML模型加载失败: \(error)")
                return false
            }
        }
        
        return true
    }
    
    /// 加载模型（使用兼容性感知配置）
    private func loadModel() async throws {
        guard let modelPath = modelPath else {
            throw DMPError.modelNotLoaded
        }
        
        let url = URL(fileURLWithPath: modelPath)
        
        // 获取设备兼容性配置
        let compatInfo = DeviceCompatibility.getCurrentDeviceCompatibility()
        let config = compatInfo.recommendedConfig
        
        print("[CoreMLProvider] 使用兼容性配置: ANE=\(config.useANE), GPU=\(config.useGPU), 内存限制=\(config.maxMemoryMB)MB")
        
        do {
            let mlConfig = MLModelConfiguration()
            
            // 根据兼容性配置设置计算单元
            if config.useANE && !DeviceCompatibility.isBlacklisted("ane") {
                mlConfig.computeUnits = .all  // ANE + GPU + CPU
                print("[CoreMLProvider] 启用 ANE 加速")
            } else if config.useGPU && !DeviceCompatibility.isBlacklisted("gpu") {
                mlConfig.computeUnits = .cpuAndGPU  // GPU + CPU
                print("[CoreMLProvider] 启用 GPU 加速")
            } else {
                mlConfig.computeUnits = .cpuOnly  // 仅 CPU
                print("[CoreMLProvider] 使用 CPU 模式")
            }
            
            // 设置性能目标
            mlConfig.performance = config.enableQuantization ? .efficient : .fast
            
            model = try await MLModel(contentsOf: url, configuration: mlConfig)
            print("[CoreMLProvider] 模型加载成功: \(modelPath)")
        } catch {
            print("[CoreMLProvider] 模型加载失败: \(error)")
            
            // 尝试使用最保守的配置重新加载
            print("[CoreMLProvider] 尝试使用保守配置重新加载...")
            try await loadModelWithFallbackConfig(url: url)
        }
    }
    
    /// 使用保守配置加载模型（降级策略）
    private func loadModelWithFallbackConfig(url: URL) async throws {
        let mlConfig = MLModelConfiguration()
        mlConfig.computeUnits = .cpuOnly
        mlConfig.performance = .efficient
        
        model = try await MLModel(contentsOf: url, configuration: mlConfig)
        print("[CoreMLProvider] 使用保守配置加载成功（仅 CPU）")
    }
    
    public func chat(request: LLMRequest) async throws -> LLMResponse {
        guard await isAvailable(), let model = model else {
            throw DMPError.modelNotLoaded
        }
        
        let prompt = request.messages.map { "\($0.role): \($0.content)" }.joined(separator: "\n")
        
        // Core ML推理
        let input = try MLDictionaryFeatureProvider(dictionary: ["text": prompt])
        let output = try model.prediction(from: input)
        let content = output.featureValue(for: "text")?.stringValue ?? ""
        
        return LLMResponse(
            content: content,
            provider: name,
            model: "coreml-local"
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
                            model: "coreml-local"
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