import Foundation

/// DMP MCP服务器
public final class DMPServer {
    private let router = LLMRouter()
    private var isRunning = false
    
    public init() {
        DMPLogger.shared.info("DMPServer initialized", file: #file, function: #function, line: #line)
    }
    
    /// 启动MCP服务器
    public func start(
        coreMLModelPath: String? = nil,
        cloudAPIKey: String? = nil,
        cloudBaseURL: String? = nil,
        cloudModelName: String? = nil
    ) async throws {
        DMPLogger.shared.info("启动DMP MCP服务器...", file: #file, function: #function, line: #line)
        
        // 输入验证
        if let apiKey = cloudAPIKey {
            do {
                try DMPInputValidator.validateAPIKey(apiKey)
            } catch {
                DMPLogger.shared.error("API Key验证失败: \(error.localizedDescription)", file: #file, function: #function, line: #line)
                throw DMPError.invalidAPIKey
            }
        }
        
        if let baseURL = cloudBaseURL {
            do {
                try DMPInputValidator.validateURL(baseURL)
            } catch {
                DMPLogger.shared.error("Base URL验证失败: \(error.localizedDescription)", file: #file, function: #function, line: #line)
                throw DMPError.invalidURL
            }
        }
        
        // 自动注册提供者
        router.autoRegister(
            coreMLModelPath: coreMLModelPath,
            cloudAPIKey: cloudAPIKey,
            cloudBaseURL: cloudBaseURL,
            cloudModelName: cloudModelName
        )
        
        isRunning = true
        DMPLogger.shared.info("DMP MCP服务器已启动", file: #file, function: #function, line: #line)
        
        // 启动MCP协议监听
        try await startMCPProtocol()
    }
    
    /// 停止MCP服务器
    public func stop() {
        isRunning = false
        DMPLogger.shared.info("DMP MCP服务器已停止", file: #file, function: #function, line: #line)
    }
    
    /// 处理MCP请求
    public func handleMCPRequest(_ request: MCPRequest) async throws -> MCPResponse {
        let traceID = UUID().uuidString
        let startTime = Date()
        
        DMPLogger.shared.info("处理MCP请求: \(request.method) (TraceID: \(traceID))", file: #file, function: #function, line: #line)
        
        return try await DMPLogger.shared.withTraceID(traceID) {
            switch request.method {
            case "tools/call":
                return try await handleToolCall(request)
                
            case "tools/list":
                return MCPResponse(
                    result: MCPResult(
                        content: "",
                        provider: "dmp",
                        model: "dmp-server"
                    ),
                    error: nil
                )
                
            case "initialize":
                return MCPResponse(
                    result: MCPResult(
                        content: "DMP MCP Server initialized",
                        provider: "dmp",
                        model: "dmp-server"
                    ),
                    error: nil
                )
                
            default:
                DMPLogger.shared.warn("未知MCP方法: \(request.method) (TraceID: \(traceID))", file: #file, function: #function, line: #line)
                return MCPResponse(
                    result: nil,
                    error: MCPError(code: -32601, message: "Method not found: \(request.method)")
                )
            }
        }
    }
    
    /// 流式聊天
    public func chatStream(request: LLMRequest) -> AsyncStream<LLMResponse> {
        return router.chatStream(request: request)
    }
    
    /// 获取提供者状态
    public func getProviderStatus() async -> [String: Bool] {
        return await router.getProviderStatus()
    }
    
    // MARK: - Private Methods
    
    private func startMCPProtocol() async throws {
        // TODO: 实现MCP协议监听 (stdio/SSE)
        // 这里需要根据具体的MCP客户端实现
        DMPLogger.shared.info("MCP协议监听已启动", file: #file, function: #function, line: #line)
    }
    
    private func handleToolCall(_ request: MCPRequest) async throws -> MCPResponse {
        guard let params = request.params,
              let arguments = params.arguments else {
            DMPLogger.shared.warn("无效的MCP参数", file: #file, function: #function, line: #line)
            return MCPResponse(
                result: nil,
                error: MCPError(code: -32602, message: "Invalid params")
            )
        }
        
        do {
            let response = try await router.chat(request: arguments)
            
            return MCPResponse(
                result: MCPResult(
                    content: response.content,
                    provider: response.provider,
                    model: response.model
                ),
                error: nil
            )
        } catch {
            DMPLogger.shared.error("Tool调用失败: \(error.localizedDescription)", file: #file, function: #function, line: #line)
            return MCPResponse(
                result: nil,
                error: MCPError(code: -1, message: error.localizedDescription)
            )
        }
    }
}