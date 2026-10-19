import SwiftUI

/// 配置页面
public struct ConfigView: View {
    @StateObject private var viewModel = ConfigViewModel()
    @Environment(\.dismiss) private var dismiss
    
    public init() {}
    
    public var body: some View {
        NavigationView {
            Form {
                // Core ML配置
                Section("Core ML本地模型") {
                    Toggle("启用Core ML", isOn: $viewModel.coreMLEnabled)
                    
                    if viewModel.coreMLEnabled {
                        LabeledContent("模型路径") {
                            Text(viewModel.coreMLModelPath.isEmpty ? "未选择" : viewModel.coreMLModelPath)
                                .foregroundColor(.secondary)
                        }
                        
                        Button("选择模型文件") {
                            viewModel.showFilePicker = true
                        }
                    }
                }
                
                // Cloud配置
                Section("云端LLM") {
                    Toggle("启用云端LLM", isOn: $viewModel.cloudEnabled)
                    
                    if viewModel.cloudEnabled {
                        SecureField("API Key", text: $viewModel.cloudAPIKey)
                            .textContentType(.password)
                        
                        TextField("Base URL", text: $viewModel.cloudBaseURL)
                            .autocapitalization(.none)
                            .keyboardType(.URL)
                        
                        TextField("模型名称", text: $viewModel.cloudModelName)
                            .autocapitalization(.none)
                    }
                }
                
                // 高级配置
                Section("高级设置") {
                    LabeledContent("温度") {
                        Slider(value: $viewModel.temperature, in: 0...2, step: 0.1) {
                            Text("温度")
                        } minimumValueLabel: {
                            Text("0")
                        } maximumValueLabel: {
                            Text(String(format: "%.1f", viewModel.temperature))
                        }
                    }
                    
                    Stepper("最大Tokens: \(viewModel.maxTokens)", value: $viewModel.maxTokens, in: 256...8192, step: 256)
                    
                    Picker("优先提供者", selection: $viewModel.preferredProvider) {
                        Text("自动").tag("auto")
                        Text("本地优先").tag("local")
                        Text("云端优先").tag("cloud")
                    }
                }
                
                // 操作
                Section {
                    Button("保存配置") {
                        viewModel.saveConfig()
                    }
                    .buttonStyle(.borderedProminent)
                    
                    Button("重置为默认", role: .destructive) {
                        viewModel.resetConfig()
                    }
                }
                
                // 信息
                Section("设备信息") {
                    LabeledContent("设备型号", value: viewModel.deviceInfo.model)
                    LabeledContent("iOS版本", value: viewModel.deviceInfo.iosVersion)
                    LabeledContent("推荐LLM", value: viewModel.deviceInfo.recommendedLLM)
                }
            }
            .navigationTitle("DMP配置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
            .fileImporter(
                isPresented: $viewModel.showFilePicker,
                allowedContentTypes: [.item],
                allowsMultipleSelection: false
            ) { result in
                if case .success(let urls) = result, let url = urls.first {
                    viewModel.coreMLModelPath = url.path
                }
            }
            .onChange(of: viewModel.saveTrigger) { _ in
                // 保存后显示提示
            }
        }
    }
}

class ConfigViewModel: ObservableObject {
    @Published var coreMLEnabled = false
    @Published var coreMLModelPath = ""
    @Published var cloudEnabled = false
    @Published var cloudAPIKey = ""
    @Published var cloudBaseURL = "https://api.openai.com/v1"
    @Published var cloudModelName = "gpt-3.5-turbo"
    @Published var temperature = 0.7
    @Published var maxTokens = 2048
    @Published var preferredProvider = "auto"
    @Published var showFilePicker = false
    @Published var saveTrigger = false
    
    struct DeviceInfo {
        let model: String
        let iosVersion: String
        let recommendedLLM: String
    }
    
    @Published var deviceInfo = DeviceInfo(model: "", iosVersion: "", recommendedLLM: "")
    
    init() {
        loadConfig()
        loadDeviceInfo()
    }
    
    func loadConfig() {
        let config = DMPConfigManager.shared
        coreMLEnabled = config.coreMLEnabled
        coreMLModelPath = config.coreMLModelPath ?? ""
        cloudEnabled = config.cloudEnabled
        cloudAPIKey = config.cloudAPIKey
        cloudBaseURL = config.cloudBaseURL
        cloudModelName = config.cloudModelName
        temperature = config.temperature
        maxTokens = config.maxTokens
        preferredProvider = config.preferredProvider
    }
    
    func saveConfig() {
        let config = DMPConfigManager.shared
        config.coreMLEnabled = coreMLEnabled
        config.coreMLModelPath = coreMLModelPath
        config.cloudEnabled = cloudEnabled
        config.cloudAPIKey = cloudAPIKey
        config.cloudBaseURL = cloudBaseURL
        config.cloudModelName = cloudModelName
        config.temperature = temperature
        config.maxTokens = maxTokens
        config.preferredProvider = preferredProvider
        config.save()
        
        saveTrigger.toggle()
    }
    
    func resetConfig() {
        DMPConfigManager.shared.resetToDefaults()
        loadConfig()
    }
    
    func loadDeviceInfo() {
        let deviceType = DeviceDetector.detectCurrentDevice()
        let recommended = DeviceDetector.getRecommendedProviders(deviceType: deviceType)
        
        #if os(iOS)
        let device = UIDevice.current
        deviceInfo = DeviceInfo(
            model: device.modelIdentifier,
            iosVersion: "iOS \(device.systemVersion)",
            recommendedLLM: recommended.joined(separator: ", ")
        )
        #endif
    }
}