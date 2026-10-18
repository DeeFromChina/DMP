import Foundation

// MARK: - 输入验证器
struct DMPInputValidator {
    
    // MARK: - 验证错误
    enum ValidationError: Error, LocalizedError {
        case emptyInput
        case inputTooLong(maxLength: Int)
        case potentialSQLInjection
        case potentialXSS
        case invalidAPIKeyFormat
        case apiKeyTooLong(maxLength: Int)
        case emptyAPIKey
        case invalidURL
        case insecureURL
        case invalidTemperature
        case invalidMaxTokens
        
        var errorDescription: String? {
            switch self {
            case .emptyInput:
                return "输入不能为空"
            case .inputTooLong(let max):
                return "输入过长，最大长度为\(max)"
            case .potentialSQLInjection:
                return "检测到潜在的SQL注入"
            case .potentialXSS:
                return "检测到潜在的XSS攻击"
            case .invalidAPIKeyFormat:
                return "API Key格式无效"
            case .apiKeyTooLong(let max):
                return "API Key过长，最大长度为\(max)"
            case .emptyAPIKey:
                return "API Key不能为空"
            case .invalidURL:
                return "URL格式无效"
            case .insecureURL:
                return "仅支持HTTPS协议"
            case .invalidTemperature:
                return "温度值必须在0-2之间"
            case .invalidMaxTokens:
                return "Max Tokens必须在256-8192之间"
            }
        }
    }
    
    // MARK: - 验证方法
    static func validateMessage(_ message: String) throws {
        guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ValidationError.emptyInput
        }
        guard message.count <= 10000 else {
            throw ValidationError.inputTooLong(maxLength: 10000)
        }
        
        // SQL注入检测
        let sqlPattern = #"('|--|;|DROP|INSERT|UPDATE|DELETE|SELECT|UNION|EXEC)"#
        if message.range(of: sqlPattern, options: .regularExpression, range: nil, locale: nil) != nil {
            throw ValidationError.potentialSQLInjection
        }
        
        // XSS检测
        if message.range(of: "<script", options: .caseInsensitive) != nil {
            throw ValidationError.potentialXSS
        }
    }
    
    static func validateAPIKey(_ key: String) throws {
        guard !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ValidationError.emptyAPIKey
        }
        guard key.count <= 200 else {
            throw ValidationError.apiKeyTooLong(maxLength: 200)
        }
        
        // API Key格式验证 (允许字母、数字、-、_)
        let keyPattern = #"^[a-zA-Z0-9\-_]{10,200}$"#
        guard key.range(of: keyPattern, options: .regularExpression) != nil else {
            throw ValidationError.invalidAPIKeyFormat
        }
    }
    
    static func validateURL(_ url: String) throws {
        guard let urlObj = URL(string: url) else {
            throw ValidationError.invalidURL
        }
        guard urlObj.scheme?.lowercased() == "https" else {
            throw ValidationError.insecureURL
        }
    }
    
    static func validateTemperature(_ temp: Double) throws {
        guard (0...2).contains(temp) else {
            throw ValidationError.invalidTemperature
        }
    }
    
    static func validateMaxTokens(_ tokens: Int) throws {
        guard (256...8192).contains(tokens) else {
            throw ValidationError.invalidMaxTokens
        }
    }
}

// MARK: - 安全存储
class DMPSecureStorage {
    
    enum SecureStorageError: Error, LocalizedError {
        case encodingFailed
        case saveFailed(OSStatus)
        case loadFailed(OSStatus)
        case deleteFailed(OSStatus)
        
        var errorDescription: String? {
            switch self {
            case .encodingFailed:
                return "数据编码失败"
            case .saveFailed(let status):
                return "保存失败，错误码: \(status)"
            case .loadFailed(let status):
                return "加载失败，错误码: \(status)"
            case .deleteFailed(let status):
                return "删除失败，错误码: \(status)"
            }
        }
    }
    
    // MARK: - 保存API Key
    static func saveAPIKey(_ key: String, forKey: String) throws {
        guard let data = key.data(using: .utf8) else {
            throw SecureStorageError.encodingFailed
        }
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: forKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked
        ]
        
        // 先删除旧数据
        SecItemDelete(query as CFDictionary)
        
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw SecureStorageError.saveFailed(status)
        }
        
        DMPLogger.shared.debug("API Key saved to Keychain: \(key.maskAPIKey())")
    }
    
    // MARK: - 加载API Key
    static func loadAPIKey(forKey: String) throws -> String {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: forKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess else {
            throw SecureStorageError.loadFailed(status)
        }
        
        guard let data = result as? Data,
              let key = String(data: data, encoding: .utf8) else {
            throw SecureStorageError.loadFailed(status)
        }
        
        DMPLogger.shared.trace("API Key loaded from Keychain: \(key.maskAPIKey())")
        return key
    }
    
    // MARK: - 删除API Key
    static func deleteAPIKey(forKey: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: forKey
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SecureStorageError.deleteFailed(status)
        }
        
        DMPLogger.shared.debug("API Key deleted from Keychain: \(forKey)")
    }
}

// MARK: - 数据脱敏
extension String {
    /// 掩码API Key (显示前4后4)
    func maskAPIKey() -> String {
        guard count > 8 else { return "***" }
        let prefix = String(prefix(4))
        let suffix = String(suffix(4))
        return "\(prefix)...\(suffix)"
    }
    
    /// 掩码Token (完全隐藏)
    func maskToken() -> String {
        return "***"
    }
    
    /// 掩码用户ID (部分隐藏)
    func maskUserID() -> String {
        guard count > 10 else { return "***" }
        let prefix = String(prefix(5))
        let suffix = String(suffix(3))
        return "\(prefix)...\(suffix)"
    }
}

// MARK: - 证书验证
class DMPCertificatePinner {
    
    /// 验证服务器证书
    static func validateCertificate(_ trust: SecTrust, domain: String?) -> Bool {
        var error: CFError?
        let policy = SecPolicyCreateSSL(true, domain as CFString?)
        SecTrustSetPolicies(trust, policy)
        
        var result: SecTrustResultType = .unspecified
        let status = SecTrustEvaluate(trust, &result)
        
        guard status == errSecSuccess else {
            DMPLogger.shared.error("Certificate validation failed: \(status)")
            return false
        }
        
        // 获取证书
        let certCount = SecTrustGetCertificateCount(trust)
        guard certCount > 0,
              let certificate = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let leafCert = certificate.first else {
            return false
        }
        
        // 证书固定验证 (可选)
        // let pinnedHash = "SHA256:base64-encoded-hash"
        // let certData = SecCertificateCopyData(leafCert) as Data
        // let hash = SHA256.hash(data: certData)
        // let hashString = hash.map { String(format: "%02x", $0) }.joined(separator: ":")
        // return hashString.uppercased() == pinnedHash
        
        return true
    }
}

// MARK: - 会话管理
class DMPSessionManager {
    static let shared = DMPSessionManager()
    
    private let sessionTimeout: TimeInterval = 24 * 60 * 60 // 24小时
    private var sessionStartTime: Date?
    
    private init() {}
    
    /// 创建新会话
    func createSession() {
        sessionStartTime = Date()
        DMPLogger.shared.info("Session created")
    }
    
    /// 检查会话是否有效
    func isSessionValid() -> Bool {
        guard let startTime = sessionStartTime else {
            return false
        }
        return Date().timeIntervalSince(startTime) < sessionTimeout
    }
    
    /// 清除会话
    func clearSession() {
        sessionStartTime = nil
        DMPLogger.shared.info("Session cleared")
    }
}