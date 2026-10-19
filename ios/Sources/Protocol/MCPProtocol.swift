import Foundation

/// MCP协议消息
struct MCPMessage: Codable {
    let jsonrpc: String
    let method: String
    let params: MCPParams?
    let id: Int?
    let result: MCPResult?
    let error: MCPError?
    
    init(request: MCPRequest) {
        self.jsonrpc = "2.0"
        self.method = request.method
        self.params = request.params
        self.id = request.id
        self.result = nil
        self.error = nil
    }
    
    init(response: MCPResponse, id: Int) {
        self.jsonrpc = "2.0"
        self.method = ""
        self.params = nil
        self.id = id
        self.result = response.result
        self.error = response.error
    }
}

struct MCPParams: Codable {
    let name: String?
    let arguments: LLMRequest?
}

struct MCPResult: Codable {
    let content: String
    let provider: String
    let model: String
}

struct MCPError: Codable {
    let code: Int
    let message: String
}

/// MCP请求
struct MCPRequest: Codable {
    let method: String
    let params: MCPParams?
    let id: Int
}

/// MCP响应
struct MCPResponse {
    let result: MCPResult?
    let error: MCPError?
}