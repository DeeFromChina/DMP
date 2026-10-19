import Foundation

enum StreamEvent {
    case requestSent(String)
    case statusReceived(Int)
    case chunkReceived(StreamChunk)
    case done
    case error(String)
}

class DMPClient: ObservableObject {
    private let baseUrl: String
    private let session: URLSession
    
    init(baseUrl: String) {
        self.baseUrl = baseUrl
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)
    }
    
    func chat(request: ChatRequest) async throws -> (requestJson: String, responseJson: String, response: ChatResponse) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted
        let requestData = try encoder.encode(request)
        let requestJson = String(data: requestData, encoding: .utf8) ?? ""
        
        guard let url = URL(string: "\(baseUrl)/chat") else {
            throw NSError(domain: "Invalid URL", code: -1)
        }
        
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = requestData
        
        let (data, response) = try await session.data(for: urlRequest)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(domain: "Invalid response", code: -1)
        }
        
        let responseJson = String(data: data, encoding: .utf8) ?? ""
        
        if httpResponse.statusCode == 200 {
            let chatResponse = try JSONDecoder().decode(ChatResponse.self, from: data)
            return (requestJson, responseJson, chatResponse)
        } else {
            throw NSError(domain: "HTTP \(httpResponse.statusCode): \(responseJson)", code: httpResponse.statusCode)
        }
    }
    
    func chatStream(request: ChatRequest) -> AsyncStream<StreamEvent> {
        AsyncStream { continuation in
            Task {
                do {
                    let encoder = JSONEncoder()
                    encoder.outputFormatting = .prettyPrinted
                    let requestData = try encoder.encode(request)
                    let requestJson = String(data: requestData, encoding: .utf8) ?? ""
                    
                    continuation.yield(.requestSent(requestJson))
                    
                    guard let url = URL(string: "\(baseUrl)/chat/stream") else {
                        continuation.yield(.error("Invalid URL"))
                        continuation.finish()
                        return
                    }
                    
                    var urlRequest = URLRequest(url: url)
                    urlRequest.httpMethod = "POST"
                    urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    urlRequest.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                    urlRequest.httpBody = requestData
                    
                    let (bytes, response) = try await session.bytes(for: urlRequest)
                    
                    guard let httpResponse = response as? HTTPURLResponse else {
                        continuation.yield(.error("Invalid response"))
                        continuation.finish()
                        return
                    }
                    
                    continuation.yield(.statusReceived(httpResponse.statusCode))
                    
                    var buffer = ""
                    for try await line in bytes.lines {
                        buffer += line + "\n"
                        
                        if line.hasPrefix("data: ") {
                            let data = String(line.dropFirst(6))
                            if data == "[DONE]" {
                                continuation.yield(.done)
                                continuation.finish()
                                return
                            }
                            
                            if let dataBytes = data.data(using: .utf8) {
                                do {
                                    let chunk = try JSONDecoder().decode(StreamChunk.self, from: dataBytes)
                                    continuation.yield(.chunkReceived(chunk))
                                } catch {
                                    // 忽略解析错误
                                }
                            }
                        }
                    }
                } catch {
                    continuation.yield(.error(error.localizedDescription))
                    continuation.finish()
                }
            }
        }
    }
    
    func checkHealth() async throws -> String {
        guard let url = URL(string: "\(baseUrl)/health") else {
            throw NSError(domain: "Invalid URL", code: -1)
        }
        
        let (data, response) = try await session.data(from: url)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw NSError(domain: "Health check failed", code: -1)
        }
        
        return String(data: data, encoding: .utf8) ?? ""
    }
}