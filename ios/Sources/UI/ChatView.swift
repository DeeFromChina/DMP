import SwiftUI

/// 聊天界面
public struct ChatView: View {
    @StateObject private var viewModel = ChatViewModel()
    @State private var inputText = ""
    @State private var showConfig = false
    
    public init() {}
    
    public var body: some View {
        VStack {
            // 消息列表
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.messages) { message in
                        MessageBubble(message: message)
                    }
                    
                    if viewModel.isStreaming {
                        HStack {
                            ProgressView()
                            Text("正在生成...")
                                .foregroundColor(.secondary)
                        }
                        .padding()
                    }
                }
                .padding()
            }
            
            // 输入框
            HStack {
                TextField("输入消息...", text: $inputText, axis: .vertical)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .lineLimit(1...6)
                    .padding()
                
                Button {
                    Task {
                        await viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.title)
                }
                .disabled(inputText.isEmpty || viewModel.isStreaming)
                .padding(.trailing)
            }
            .padding(.bottom)
        }
        .navigationTitle("DMP Chat")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showConfig = true
                } label: {
                    Image(systemName: "gearshape")
                }
            }
        }
        .sheet(isPresented: $showConfig) {
            ConfigView()
        }
        .task {
            await viewModel.initialize()
        }
    }
}

struct MessageBubble: View {
    let message: ChatMessage
    
    var body: some View {
        HStack {
            if message.role == "user" {
                Spacer()
                Text(message.content)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            } else {
                Text(message.content)
                    .padding()
                    .background(Color.gray.opacity(0.2))
                    .cornerRadius(12)
                Spacer()
            }
        }
    }
}

class ChatViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var isStreaming = false
    
    private let server = DMPServer()
    
    @MainActor
    func initialize() async {
        let config = DMPConfigManager.shared
        
        do {
            try await server.start(
                coreMLModelPath: config.coreMLEnabled ? config.coreMLModelPath : nil,
                cloudAPIKey: config.cloudEnabled ? config.cloudAPIKey : nil,
                cloudBaseURL: config.cloudBaseURL,
                cloudModelName: config.cloudModelName
            )
            
            let status = await server.getProviderStatus()
            print("提供者状态: \(status)")
        } catch {
            print("初始化失败: \(error)")
        }
    }
    
    @MainActor
    func sendMessage(_ text: String) async {
        guard !text.isEmpty else { return }
        
        // 添加用户消息
        let userMessage = ChatMessage(role: "user", content: text)
        messages.append(userMessage)
        
        // 创建助手消息占位
        let assistantMessage = ChatMessage(role: "assistant", content: "")
        messages.append(assistantMessage)
        
        // 发送请求
        isStreaming = true
        do {
            let config = DMPConfigManager.shared
            let request = LLMRequest(
                messages: messages
                    .filter { $0.role != "system" }
                    .map { Message(role: $0.role, content: $0.content) },
                temperature: config.temperature,
                maxTokens: config.maxTokens
            )
            
            // 使用流式输出
            for await chunk in server.chatStream(request: request) {
                if let lastIndex = messages.indices.last {
                    messages[lastIndex].content += chunk.content
                }
            }
        } catch {
            if let lastIndex = messages.indices.last {
                messages[lastIndex].content = "错误: \(error.localizedDescription)"
            }
        }
        isStreaming = false
    }
}

struct ChatMessage: Identifiable {
    let id = UUID()
    let role: String
    var content: String
}