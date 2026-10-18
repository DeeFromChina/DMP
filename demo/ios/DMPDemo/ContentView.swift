import SwiftUI

struct ChatMessage: Identifiable {
    let id = UUID()
    let role: String
    var content: String
}

enum DetailTab {
    case request, response
}

struct ContentView: View {
    @State private var messages: [ChatMessage] = []
    @State private var newMessage: String = ""
    @State private var serverUrl: String = ServerConfig.baseUrl
    @State private var isStreaming: Bool = false
    @State private var isLoading: Bool = false
    @State private var requestJson: String = ""
    @State private var responseJson: String = ""
    @State private var activeTab: DetailTab = .request
    @State private var showHealthAlert: Bool = false
    @State private var healthMessage: String = ""
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Config bar
                configBar
                
                // Messages
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(messages) { message in
                                messageBubble(message)
                            }
                        }
                        .padding()
                    }
                    .onChange(of: messages.count) { _ in
                        if let lastId = messages.last?.id {
                            withAnimation {
                                proxy.scrollTo(lastId, anchor: .bottom)
                            }
                        }
                    }
                }
                
                // Detail panel
                detailPanel
                
                // Input area
                inputArea
            }
            .navigationTitle("DMP API Demo")
        }
        .alert("健康检查", isPresented: $showHealthAlert) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(healthMessage)
        }
    }
    
    private var configBar: some View {
        VStack(spacing: 8) {
            HStack {
                Text("服务地址:")
                    .font(.caption)
                TextField("http://host:port", text: $serverUrl)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .font(.caption)
            }
            .padding(.horizontal)
            
            HStack {
                Toggle("流式模式", isOn: $isStreaming)
                    .toggleStyle(SwitchToggleStyle())
                
                Spacer()
                
                Button("健康检查") {
                    checkHealth()
                }
                .buttonStyle(.bordered)
                
                Button("清空") {
                    messages.removeAll()
                    requestJson = ""
                    responseJson = ""
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 8)
        .background(Color(.systemGray6))
    }
    
    private func messageBubble(_ message: ChatMessage) -> some View {
        HStack {
            if message.role == "user" {
                Spacer()
            }
            
            Text(message.content)
                .padding(12)
                .background(
                    message.role == "user" ? Color.blue :
                    message.role == "error" ? Color.red : Color(.systemBackground)
                )
                .foregroundColor(message.role == "assistant" ? .primary : .white)
                .cornerRadius(12)
                .frame(maxWidth: 280, alignment: message.role == "user" ? .trailing : .leading)
            
            if message.role == "assistant" || message.role == "error" {
                Spacer()
            }
        }
    }
    
    private var detailPanel: some View {
        VStack(spacing: 0) {
            // Tabs
            HStack(spacing: 0) {
                Button("请求体") {
                    activeTab = .request
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(activeTab == .request ? Color.blue : Color(.systemGray5))
                .foregroundColor(activeTab == .request ? .white : .primary)
                
                Button("响应体") {
                    activeTab = .response
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(activeTab == .response ? Color.blue : Color(.systemGray5))
                .foregroundColor(activeTab == .response ? .white : .primary)
            }
            
            // Content
            ScrollView {
                Text(activeTab == .request ? 
                     (requestJson.isEmpty ? "发送消息后查看请求体" : requestJson) :
                     (responseJson.isEmpty ? "收到响应后查看响应体" : responseJson))
                    .font(.system(.caption, design: .monospaced))
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(.systemBackground))
        }
        .frame(height: 200)
    }
    
    private var inputArea: some View {
        HStack(spacing: 8) {
            TextField("输入消息...", text: $newMessage, axis: .vertical)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .lineLimit(4)
                .disabled(isLoading)
            
            Button(action: sendMessage) {
                Image(systemName: "paperplane.fill")
                    .font(.title2)
            }
            .disabled(newMessage.trimmingCharacters(in: .whitespaces).isEmpty || isLoading)
        }
        .padding()
        .background(Color(.systemBackground))
    }
    
    private func sendMessage() {
        let message = newMessage.trimmingCharacters(in: .whitespaces)
        guard !message.isEmpty else { return }
        
        withAnimation {
            messages.append(ChatMessage(role: "user", content: message))
        }
        newMessage = ""
        
        if isStreaming {
            sendStreamMessage(message)
        } else {
            sendNormalMessage(message)
        }
    }
    
    private func sendNormalMessage(_ content: String) {
        isLoading = true
        
        Task {
            let chatMessages = messages.map { Message(role: $0.role, content: $0.content) }
            let request = ChatRequest(messages: chatMessages)
            
            do {
                let (reqJson, respJson, response) = try await DMPClient(baseUrl: serverUrl).chat(request: request)
                
                await MainActor.run {
                    requestJson = reqJson
                    responseJson = respJson
                    messages.append(ChatMessage(role: "assistant", content: response.content))
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    requestJson = try! JSONEncoder().encode(request).prettyPrinted
                    responseJson = "错误: \(error.localizedDescription)"
                    messages.append(ChatMessage(role: "error", content: "请求失败: \(error.localizedDescription)"))
                    isLoading = false
                }
            }
        }
    }
    
    private func sendStreamMessage(_ content: String) {
        isLoading = true
        
        let assistantIndex = messages.count
        withAnimation {
            messages.append(ChatMessage(role: "assistant", content: "正在处理..."))
        }
        
        Task {
            let chatMessages = messages.map { Message(role: $0.role, content: $0.content) }
            let request = ChatRequest(messages: chatMessages)
            let client = DMPClient(baseUrl: serverUrl)
            
            var fullContent = ""
            var provider = ""
            var model = ""
            
            for await event in client.chatStream(request: request) {
                switch event {
                case .requestSent(let json):
                    await MainActor.run {
                        requestJson = json
                    }
                case .statusReceived:
                    break
                case .chunkReceived(let chunk):
                    fullContent += chunk.content
                    provider = chunk.provider
                    model = chunk.model
                    await MainActor.run {
                        messages[assistantIndex].content = fullContent
                    }
                case .done:
                    let responseDict: [String: Any] = [
                        "content": fullContent,
                        "provider": provider,
                        "model": model,
                        "finish_reason": "stop"
                    ]
                    if let jsonData = try? JSONSerialization.data(withJSONObject: responseDict, options: .prettyPrinted),
                       let jsonString = String(data: jsonData, encoding: .utf8) {
                        await MainActor.run {
                            responseJson = jsonString
                        }
                    }
                    await MainActor.run {
                        isLoading = false
                    }
                case .error(let message):
                    await MainActor.run {
                        messages[assistantIndex].content = "错误: \(message)"
                        responseJson = "错误: \(message)"
                        isLoading = false
                    }
                }
            }
        }
    }
    
    private func checkHealth() {
        Task {
            do {
                let result = try await DMPClient(baseUrl: serverUrl).checkHealth()
                await MainActor.run {
                    healthMessage = result
                    showHealthAlert = true
                }
            } catch {
                await MainActor.run {
                    healthMessage = "健康检查失败: \(error.localizedDescription)"
                    showHealthAlert = true
                }
            }
        }
    }
}

extension Data {
    func prettyPrinted() -> String {
        guard let json = try? JSONSerialization.jsonObject(with: self),
              let data = try? JSONSerialization.data(withJSONObject: json, options: .prettyPrinted),
              let string = String(data: data, encoding: .utf8) else {
            return String(data: self, encoding: .utf8) ?? ""
        }
        return string
    }
}