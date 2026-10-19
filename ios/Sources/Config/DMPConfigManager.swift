import Foundation

/// DMP配置管理器
public final class DMPConfigManager {
    public static let shared = DMPConfigManager()
    
    private let defaults = UserDefaults.standard
    private let secureStorage = DMPSecureStorage()
    
    // MARK: - Core ML配置
    public var coreMLEnabled: Bool {
        get { defaults.bool(forKey: "coreml_enabled") }
        set { defaults.set(newValue, forKey: "coreml_enabled") }
    }
    
    public var coreMLModelPath: String? {
        get { defaults.string(forKey: "coreml_model_path") }
        set { defaults.set(newValue, forKey: "coreml_model_path") }
    }
    
    // MARK: - Cloud配置
    public var cloudEnabled: Bool {
        get { defaults.bool(forKey: "cloud_enabled") }
        set { defaults.set(newValue, forKey: "cloud_enabled") }
    }
    
    public var cloudAPIKey: String {
        get {
            let key = secureStorage.loadAPIKey(forKey: "cloud_api_key") ?? ""
            if !key.isEmpty {
                DMPLogger.shared.trace("API Key loaded from Keychain: \(key.maskAPIKey())", 
                                      file: #file, function: #function, line: #line)
            }
            return key
        }
        set {
            if !newValue.isEmpty {
                secureStorage.saveAPIKey(newValue, forKey: "cloud_api_key")
                DMPLogger.shared.debug("API Key saved to Keychain: \(newValue.maskAPIKey())", 
                                      file: #file, function: #function, line: #line)
            }
        }
    }
    
    public var cloudBaseURL: String {
        get { defaults.string(forKey: "cloud_base_url") ?? "https://api.openai.com/v1" }
        set { defaults.set(newValue, forKey: "cloud_base_url") }
    }
    
    public var cloudModelName: String {
        get { defaults.string(forKey: "cloud_model_name") ?? "gpt-3.5-turbo" }
        set { defaults.set(newValue, forKey: "cloud_model_name") }
    }
    
    // MARK: - 通用配置
    public var temperature: Double {
        get { defaults.double(forKey: "temperature") != 0 ? defaults.double(forKey: "temperature") : 0.7 }
        set { defaults.set(newValue, forKey: "temperature") }
    }
    
    public var maxTokens: Int {
        get { defaults.integer(forKey: "max_tokens") != 0 ? defaults.integer(forKey: "max_tokens") : 2048 }
        set { defaults.set(newValue, forKey: "max_tokens") }
    }
    
    public var preferredProvider: String {
        get { defaults.string(forKey: "preferred_provider") ?? "auto" }
        set { defaults.set(newValue, forKey: "preferred_provider") }
    }
    
    // MARK: - 方法
    
    /// 保存配置
    public func save() {
        defaults.synchronize()
        DMPLogger.shared.debug("配置已保存", file: #file, function: #function, line: #line)
    }
    
    /// 重置为默认配置
    public func resetToDefaults() {
        let keys = [
            "coreml_enabled", "coreml_model_path",
            "cloud_enabled", "cloud_base_url", "cloud_model_name",
            "temperature", "max_tokens", "preferred_provider"
        ]
        keys.forEach { defaults.removeObject(forKey: $0) }
        
        // 清除安全存储
        secureStorage.deleteAPIKey(forKey: "cloud_api_key")
        
        defaults.synchronize()
        DMPLogger.shared.info("配置已重置为默认值", file: #file, function: #function, line: #line)
    }
    
    /// 导出配置为JSON (敏感信息脱敏)
    public func exportConfig() -> [String: Any] {
        return [
            "coreml_enabled": coreMLEnabled,
            "coreml_model_path": coreMLModelPath ?? "",
            "cloud_enabled": cloudEnabled,
            "cloud_api_key": cloudAPIKey.maskAPIKey(),
            "cloud_base_url": cloudBaseURL,
            "cloud_model_name": cloudModelName,
            "temperature": temperature,
            "max_tokens": maxTokens,
            "preferred_provider": preferredProvider
        ]
    }
    
    /// 从JSON导入配置
    public func importConfig(_ config: [String: Any]) {
        if let value = config["coreml_enabled"] as? Bool { coreMLEnabled = value }
        if let value = config["coreml_model_path"] as? String { coreMLModelPath = value }
        if let value = config["cloud_enabled"] as? Bool { cloudEnabled = value }
        if let value = config["cloud_api_key"] as? String { cloudAPIKey = value }
        if let value = config["cloud_base_url"] as? String { cloudBaseURL = value }
        if let value = config["cloud_model_name"] as? String { cloudModelName = value }
        if let value = config["temperature"] as? Double { temperature = value }
        if let value = config["max_tokens"] as? Int { maxTokens = value }
        if let value = config["preferred_provider"] as? String { preferredProvider = value }
        save()
    }
}