import DMP

// MARK: - 基础使用示例

func basicUsage() async {
    // 创建MCP服务器
    let server = DMPServer()
    
    // 启动服务
    do {
        try await server.start(
            coreMLModelPath: Bundle.main.path(forResource: "llm-quantized", ofType: "mlmodel"),
            cloudAPIKey: "sk-xxx" // 可选
        )
        
        print("✅ DMP服务器已启动")
        
        // 查看提供者状态
        let status = await server.getProviderStatus()
        for (name, available) in status {
            print("\(name): \(available ? "✅" : "❌")")
        }
    } catch {
        print("❌ 启动失败: \(error)")
    }
}

// MARK: - 直接使用路由器

func useRouterDirectly() async {
    let router = LLMRouter()
    
    // 自动注册（根据设备型号）
    router.autoRegister(
        coreMLModelPath: Bundle.main.path(forResource: "llm", ofType: "mlmodel"),
        cloudAPIKey: "sk-xxx"
    )
    
    // 非流式聊天
    let request = LLMRequest(
        messages: [
            Message(role: "system", content: "你是一个有帮助的助手"),
            Message(role: "user", content: "你好，请介绍一下你自己")
        ]
    )
    
    do {
        let response = try await router.chat(request: request)
        print("回复: \(response.content)")
        print("提供者: \(response.provider)")
        print("模型: \(response.model)")
    } catch {
        print("错误: \(error)")
    }
}

// MARK: - 流式输出

func streamChat() async {
    let router = LLMRouter()
    router.autoRegister(cloudAPIKey: "sk-xxx")
    
    let request = LLMRequest(
        messages: [Message(role: "user", content: "请讲一个简短的故事")],
        stream: true
    )
    
    print("开始流式输出:")
    for await chunk in router.chatStream(request: request) {
        print(chunk.content, terminator: "")
    }
    print("\n输出完成")
}

// MARK: - SwiftUI聊天应用示例

import SwiftUI

struct ChatView: View {
    @StateObject private var viewModel = ChatViewModel()
    @State private var inputText = ""
    
    var body: some View {
        VStack {
            // 消息列表
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.messages) { message in
                        MessageBubble(message: message)
                    }
                }
                .padding()
            }
            
            // 输入框
            HStack {
                TextField("输入消息...", text: $inputText)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding()
                
                Button("发送") {
                    Task {
                        await viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isStreaming)
                .padding(.trailing)
            }
            .padding(.bottom)
        }
        .navigationTitle("DMP Chat")
        .task {
            await viewModel.initialize()
        }
    }
}

class ChatViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var isStreaming = false
    
    private let server = DMPServer()
    
    @MainActor
    func initialize() async {
        do {
            try await server.start(
                coreMLModelPath: Bundle.main.path(forResource: "llm", ofType: "mlmodel"),
                cloudAPIKey: "sk-xxx"
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
        
        // 流式输出
        isStreaming = true
        do {
            let request = LLMRequest(
                messages: messages
                    .filter { $0.role != "system" }
                    .map { Message(role: $0.role, content: $0.content) }
            )
            
            // 这里需要直接访问router，简化示例使用非流式
            let response = try await server.handleMCPRequest(
                MCPRequest(
                    method: "tools/call",
                    params: MCPParams(name: "chat", arguments: request),
                    id: 1
                )
            )
            
            if let result = response.result {
                messages[messages.count - 1].content = result.content
            }
        } catch {
            messages[messages.count - 1].content = "错误: \(error.localizedDescription)"
        }
        isStreaming = false
    }
}

struct ChatMessage: Identifiable {
    let id = UUID()
    let role: String
    var content: String
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

// MARK: - 手动注册提供者

func manualProviderRegistration() {
    let router = LLMRouter()
    
    // 手动注册特定提供者
    router.register(AppleIntelligenceProvider())
    router.register(CoreMLProvider(modelPath: "/path/to/model.mlmodel"))
    router.register(CloudProvider(
        apiKey: "sk-xxx",
        baseURL: "https://api.openai.com/v1",
        modelName: "gpt-4"
    ))
}

// MARK: - 检查设备信息

func checkDeviceInfo() {
    let deviceType = DeviceDetector.detectCurrentDevice()
    print("设备类型: \(deviceType)")
    
    let recommendedProviders = DeviceDetector.getRecommendedProviders(deviceType: deviceType)
    print("推荐提供者: \(recommendedProviders)")
}