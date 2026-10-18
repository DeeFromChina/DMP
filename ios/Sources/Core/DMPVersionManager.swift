import Foundation

// MARK: - 版本管理器

/// DMP版本管理器
/// 负责：
/// 1. 应用版本管理和配置迁移
/// 2. 操作系统升级检测和能力重新评估
/// 3. 向后兼容性保证
public final class DMPVersionManager {
    public static let shared = DMPVersionManager()
    
    private let defaults = UserDefaults.standard
    private let logger = DMPLogger.shared
    
    // MARK: - 应用版本信息
    
    /// 当前应用版本
    public var appVersion: String {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
    
    /// 当前构建号
    public var buildNumber: String {
        return Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }
    
    /// 上次运行的应用版本
    public var lastAppVersion: String? {
        return defaults.string(forKey: "dmp_last_app_version")
    }
    
    /// 上次运行的操作系统版本
    public var lastOSVersion: String? {
        return defaults.string(forKey: "dmp_last_os_version")
    }
    
    // MARK: - 初始化
    
    private init() {
        logger.info("DMPVersionManager initialized", file: #file, function: #function, line: #line)
    }
    
    /// 版本管理器启动检查
    /// 应在应用启动时调用，检测版本变化并执行相应操作
    public func startupCheck() -> VersionCheckResult {
        let currentAppVersion = appVersion
        let currentOSVersion = getCurrentOSVersion()
        
        let appVersionChanged = lastAppVersion != currentAppVersion
        let osVersionChanged = lastOSVersion != currentOSVersion
        
        var result = VersionCheckResult(
            appVersionChanged: appVersionChanged,
            osVersionChanged: osVersionChanged,
            previousAppVersion: lastAppVersion,
            currentAppVersion: currentAppVersion,
            previousOSVersion: lastOSVersion,
            currentOSVersion: currentOSVersion,
            actions: []
        )
        
        // 应用版本升级处理
        if appVersionChanged {
            logger.info("应用版本升级: \(lastAppVersion ?? "未知") -> \(currentAppVersion)", 
                       file: #file, function: #function, line: #line)
            let actions = handleAppUpgrade(from: lastAppVersion, to: currentAppVersion)
            result.actions.append(contentsOf: actions)
        }
        
        // 操作系统版本升级处理
        if osVersionChanged {
            logger.info("操作系统版本升级: \(lastOSVersion ?? "未知") -> \(currentOSVersion)", 
                       file: #file, function: #function, line: #line)
            let actions = handleOSUpgrade(from: lastOSVersion, to: currentOSVersion)
            result.actions.append(contentsOf: actions)
        }
        
        // 保存当前版本信息
        defaults.set(currentAppVersion, forKey: "dmp_last_app_version")
        defaults.set(currentOSVersion, forKey: "dmp_last_os_version")
        defaults.synchronize()
        
        return result
    }
    
    // MARK: - 应用升级处理
    
    /// 处理应用版本升级
    private func handleAppUpgrade(from: String?, to: String) -> [UpgradeAction] {
        var actions: [UpgradeAction] = []
        
        guard let fromVersion = from else {
            // 首次安装
            logger.info("首次安装应用", file: #file, function: #function, line: #line)
            actions.append(.firstInstall)
            return actions
        }
        
        // 版本比较
        let fromComponents = fromVersion.split(separator: ".").compactMap { Int($0) }
        let toComponents = to.split(separator: ".").compactMap { Int($0) }
        
        // 主版本升级 (1.x -> 2.x)
        if toComponents.count > 0 && fromComponents.count > 0 && toComponents[0] > fromComponents[0] {
            logger.warn("主版本升级: \(from) -> \(to)，需要完整配置迁移", 
                       file: #file, function: #function, line: #line)
            actions.append(.majorUpgrade(from: from, to: to))
            actions.append(contentsOf: migrateConfiguration(from: from, to: to))
        }
        // 次版本升级 (1.1 -> 1.2)
        else if toComponents.count > 1 && fromComponents.count > 1 && toComponents[1] > fromComponents[1] {
            logger.info("次版本升级: \(from) -> \(to)", file: #file, function: #function, line: #line)
            actions.append(.minorUpgrade(from: from, to: to))
        }
        // 补丁版本升级 (1.1.1 -> 1.1.2)
        else {
            logger.debug("补丁版本升级: \(from) -> \(to)", file: #file, function: #function, line: #line)
            actions.append(.patchUpgrade(from: from, to: to))
        }
        
        // 检查是否需要重新评估设备能力
        actions.append(.reevaluateCapabilities)
        
        return actions
    }
    
    // MARK: - 操作系统升级处理
    
    /// 处理操作系统版本升级
    private func handleOSUpgrade(from: String?, to: String) -> [UpgradeAction] {
        var actions: [UpgradeAction] = []
        
        guard let fromVersion = from else {
            return []
        }
        
        #if os(iOS)
        let fromComponents = fromVersion.split(separator: ".").compactMap { Int($0) }
        let toComponents = to.split(separator: ".").compactMap { Int($0) }
        
        // 检测iOS主版本升级 (17 -> 18)
        if toComponents.count > 0 && fromComponents.count > 0 && toComponents[0] > fromComponents[0] {
            logger.info("iOS主版本升级: \(from) -> \(to)，重新评估AI能力", 
                       file: #file, function: #function, line: #line)
            actions.append(.osMajorUpgrade(from: from, to: to))
            
            // iOS 17 -> 18 可能启用Apple Intelligence
            if fromComponents[0] < 18 && toComponents[0] >= 18 {
                logger.info("升级到iOS 18+，可能支持Apple Intelligence", 
                           file: #file, function: #function, line: #line)
                actions.append(.newAICapabilityAvailable("apple_intelligence"))
            }
        }
        #elseif os(Android)
        // Android SDK版本升级
        let fromSDK = Int(fromVersion) ?? 0
        let toSDK = Int(to) ?? 0
        
        if toSDK > fromSDK {
            logger.info("Android SDK升级: \(from) -> \(to)，重新评估AI能力", 
                       file: #file, function: #function, line: #line)
            actions.append(.osMajorUpgrade(from: from, to: to))
            
            // API 29以下 -> 29+ 可能启用TFLite
            if fromSDK < 29 && toSDK >= 29 {
                logger.info("升级到Android 10+ (API 29+)，可能支持TFLite", 
                           file: #file, function: #function, line: #line)
                actions.append(.newAICapabilityAvailable("tflite"))
            }
            
            // API 31+ 可能启用更多AI特性
            if fromSDK < 31 && toSDK >= 31 {
                logger.info("升级到Android 12+ (API 31+)，支持更多AI特性", 
                           file: #file, function: #function, line: #line)
                actions.append(.newAICapabilityAvailable("enhanced_ai"))
            }
        }
        #endif
        
        // 操作系统升级后需要重新评估设备能力
        actions.append(.reevaluateCapabilities)
        
        return actions
    }
    
    // MARK: - 配置迁移
    
    /// 配置迁移（主版本升级时调用）
    private func migrateConfiguration(from: String, to: String) -> [UpgradeAction] {
        var actions: [UpgradeAction] = []
        
        // 1. 备份当前配置
        actions.append(.backupConfiguration)
        backupConfiguration(toVersion: from)
        
        // 2. 迁移配置格式（如果有变化）
        // 注意：UserDefaults/Keychain中的配置会自动保留
        // 这里只处理配置格式变更的情况
        migrateConfigurationFormat(from: from, to: to)
        
        // 3. 验证迁移后的配置
        actions.append(.verifyConfiguration)
        let isValid = verifyConfiguration()
        
        if !isValid {
            logger.error("配置迁移验证失败，尝试回滚", file: #file, function: #function, line: #line)
            restoreConfiguration(fromVersion: from)
        }
        
        logger.info("配置迁移完成: \(from) -> \(to)", file: #file, function: #function, line: #line)
        
        return actions
    }
    
    /// 备份配置
    private func backupConfiguration(toVersion: String) {
        let config = DMPConfigManager.shared.exportConfig()
        let backupKey = "dmp_config_backup_\(toVersion)"
        
        // 将配置序列化为JSON并保存
        if let jsonData = try? JSONSerialization.data(withJSONObject: config),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            defaults.set(jsonString, forKey: backupKey)
            logger.info("配置已备份到: \(backupKey)", file: #file, function: #function, line: #line)
        }
    }
    
    /// 迁移配置格式
    private func migrateConfigurationFormat(from: String, to: String) {
        // 当前版本配置格式未变更，无需迁移
        // 未来如果配置格式变化，在这里处理
        // 例如：
        // if from.hasPrefix("1.") && to.hasPrefix("2.") {
        //     // v1.x -> v2.x 配置格式变更
        //     migrateV1ToV2()
        // }
    }
    
    /// 验证配置
    private func verifyConfiguration() -> Bool {
        // 验证必要配置是否存在
        let config = DMPConfigManager.shared.exportConfig()
        
        // 检查必要字段
        guard let _ = config["preferred_provider"] as? String else {
            return false
        }
        
        return true
    }
    
    /// 回滚配置
    private func restoreConfiguration(fromVersion: String) {
        let backupKey = "dmp_config_backup_\(fromVersion)"
        
        guard let jsonString = defaults.string(forKey: backupKey),
              let jsonData = jsonString.data(using: .utf8),
              let config = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
            logger.error("配置回滚失败：备份不存在", file: #file, function: #function, line: #line)
            return
        }
        
        DMPConfigManager.shared.importConfig(config)
        logger.info("配置已回滚到版本: \(fromVersion)", file: #file, function: #function, line: #line)
    }
    
    // MARK: - 辅助方法
    
    /// 获取当前操作系统版本字符串
    private func getCurrentOSVersion() -> String {
        #if os(iOS)
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
        #elseif os(Android)
        return "\(android.os.Build.VERSION.SDK_INT)"
        #else
        return "unknown"
        #endif
    }
}

// MARK: - 版本检查结果

/// 版本检查结果
public struct VersionCheckResult {
    public let appVersionChanged: Bool
    public let osVersionChanged: Bool
    public let previousAppVersion: String?
    public let currentAppVersion: String
    public let previousOSVersion: String?
    public let currentOSVersion: String
    public var actions: [UpgradeAction]
    
    /// 是否需要重新初始化LLM提供者
    public var needsReinitialization: Bool {
        return actions.contains { action in
            if case .reevaluateCapabilities = action {
                return true
            }
            return false
        }
    }
    
    /// 是否有新的AI能力可用
    public var newAICapabilities: [String] {
        return actions.compactMap { action in
            if case .newAICapabilityAvailable(let capability) = action {
                return capability
            }
            return nil
        }
    }
}

// MARK: - 升级操作

/// 升级操作类型
public enum UpgradeAction: Equatable {
    /// 首次安装
    case firstInstall
    
    /// 主版本升级
    case majorUpgrade(from: String, to: String)
    
    /// 次版本升级
    case minorUpgrade(from: String, to: String)
    
    /// 补丁版本升级
    case patchUpgrade(from: String, to: String)
    
    /// 操作系统主版本升级
    case osMajorUpgrade(from: String, to: String)
    
    /// 备份配置
    case backupConfiguration
    
    /// 验证配置
    case verifyConfiguration
    
    /// 重新评估设备能力
    case reevaluateCapabilities
    
    /// 新的AI能力可用
    case newAICapabilityAvailable(String)
    
    public static func == (lhs: UpgradeAction, rhs: UpgradeAction) -> Bool {
        switch (lhs, rhs) {
        case (.firstInstall, .firstInstall):
            return true
        case (.majorUpgrade(let lFrom, let lTo), .majorUpgrade(let rFrom, let rTo)):
            return lFrom == rFrom && lTo == rTo
        case (.minorUpgrade(let lFrom, let lTo), .minorUpgrade(let rFrom, let rTo)):
            return lFrom == rFrom && lTo == rTo
        case (.patchUpgrade(let lFrom, let lTo), .patchUpgrade(let rFrom, let rTo)):
            return lFrom == rFrom && lTo == rTo
        case (.osMajorUpgrade(let lFrom, let lTo), .osMajorUpgrade(let rFrom, let rTo)):
            return lFrom == rFrom && lTo == rTo
        case (.backupConfiguration, .backupConfiguration):
            return true
        case (.verifyConfiguration, .verifyConfiguration):
            return true
        case (.reevaluateCapabilities, .reevaluateCapabilities):
            return true
        case (.newAICapabilityAvailable(let lCap), .newAICapabilityAvailable(let rCap)):
            return lCap == rCap
        default:
            return false
        }
    }
}