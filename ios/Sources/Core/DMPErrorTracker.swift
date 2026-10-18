import Foundation

// MARK: - 错误码枚举
enum DMPErrorCode: Int {
    // 网络错误 1000-1999
    case networkTimeout = 1001
    case networkUnavailable = 1002
    case sslCertificateError = 1003
    
    // 认证错误 2000-2999
    case invalidAPIKey = 2001
    case tokenExpired = 2002
    case permissionDenied = 2003
    
    // 配置错误 3000-3999
    case configMissing = 3001
    case configInvalid = 3002
    
    // 业务错误 4000-4999
    case noProviderAvailable = 4001
    case requestRejected = 4002
    case invalidResponse = 4003
    
    // 系统错误 5000-5999
    case outOfMemory = 5001
    case storageFull = 5002
}

// MARK: - DMP错误
struct DMPError: Error, LocalizedError {
    let code: DMPErrorCode
    let message: String
    let underlyingError: Error?
    let traceID: String?
    let timestamp: Date
    
    init(code: DMPErrorCode, message: String, underlyingError: Error? = nil, traceID: String? = nil) {
        self.code = code
        self.message = message
        self.underlyingError = underlyingError
        self.traceID = traceID ?? DMPLogger.shared.traceID
        self.timestamp = Date()
        
        // 记录错误日志
        DMPLogger.shared.error("[\(code.rawValue)] \(message) (TraceID: \(self.traceID ?? "N/A"))")
    }
    
    var errorDescription: String? {
        var desc = "[\(code.rawValue)] \(message)"
        if let traceID = traceID {
            desc += " (TraceID: \(traceID))"
        }
        return desc
    }
    
    var isRetryable: Bool {
        switch code {
        case .networkTimeout, .networkUnavailable:
            return true
        case .sslCertificateError, .invalidAPIKey, .tokenExpired, .permissionDenied,
             .configMissing, .configInvalid, .noProviderAvailable, .requestRejected,
             .invalidResponse, .outOfMemory, .storageFull:
            return false
        }
    }
}

// MARK: - 错误追踪器
class DMPErrorTracker {
    static let shared = DMPErrorTracker()
    
    private var errorHistory: [DMPError] = []
    private let maxHistoryCount = 100
    private let queue = DispatchQueue(label: "com.dmp.error.tracker")
    
    private init() {}
    
    /// 记录错误
    func trackError(_ error: DMPError) {
        queue.async {
            self.errorHistory.append(error)
            
            // 限制历史记录数量
            if self.errorHistory.count > self.maxHistoryCount {
                self.errorHistory.removeFirst(self.errorHistory.count - self.maxHistoryCount)
            }
            
            // 分析错误模式
            self.analyzeErrorPattern()
        }
    }
    
    /// 获取错误统计
    func getErrorStats() -> ErrorStats {
        queue.sync {
            let totalErrors = errorHistory.count
            let retryableErrors = errorHistory.filter { $0.isRetryable }.count
            let networkErrors = errorHistory.filter { $0.code.rawValue >= 1000 && $0.code.rawValue < 2000 }.count
            let authErrors = errorHistory.filter { $0.code.rawValue >= 2000 && $0.code.rawValue < 3000 }.count
            
            return ErrorStats(
                totalErrors: totalErrors,
                retryableErrors: retryableErrors,
                networkErrors: networkErrors,
                authErrors: authErrors
            )
        }
    }
    
    /// 分析错误模式
    private func analyzeErrorPattern() {
        // 检查连续错误
        let recentErrors = Array(errorHistory.suffix(5))
        let sameCodeErrors = recentErrors.filter { $0.code == recentErrors.last?.code }
        
        if sameCodeErrors.count >= 3 {
            DMPLogger.shared.warn("Detected repeated error pattern: \(sameCodeErrors.first?.code.rawValue ?? 0)")
            // 可以触发告警或自动降级
        }
    }
    
    /// 清除错误历史
    func clearHistory() {
        queue.async {
            self.errorHistory.removeAll()
        }
    }
}

// MARK: - 错误统计
struct ErrorStats {
    let totalErrors: Int
    let retryableErrors: Int
    let networkErrors: Int
    let authErrors: Int
    
    var errorRate: Double {
        guard totalErrors > 0 else { return 0 }
        return Double(networkErrors + authErrors) / Double(totalErrors)
    }
}

// MARK: - 重试策略
struct DMPPRetryPolicy {
    let maxRetries: Int
    let initialDelay: TimeInterval
    let maxDelay: TimeInterval
    let backoffMultiplier: Double
    
    static let `default` = DMPPRetryPolicy(
        maxRetries: 3,
        initialDelay: 1.0,
        maxDelay: 30.0,
        backoffMultiplier: 2.0
    )
    
    /// 判断是否应该重试
    func shouldRetry(error: Error, attempt: Int) -> Bool {
        guard attempt < maxRetries else { return false }
        
        if let dmpError = error as? DMPError {
            return dmpError.isRetryable
        }
        
        return false
    }
    
    /// 计算重试延迟
    func delay(for attempt: Int) -> TimeInterval {
        let delay = initialDelay * pow(backoffMultiplier, Double(attempt))
        return min(delay, maxDelay)
    }
}

// MARK: - 重试辅助函数
func withRetry<T>(
    policy: DMPPRetryPolicy = .default,
    operation: @escaping () async throws -> T
) async throws -> T {
    var lastError: Error?
    
    for attempt in 0..<policy.maxRetries {
        do {
            return try await operation()
        } catch {
            lastError = error
            
            if !policy.shouldRetry(error: error, attempt: attempt) {
                throw error
            }
            
            let delay = policy.delay(for: attempt)
            DMPLogger.shared.warn("Retry attempt \(attempt + 1)/\(policy.maxRetries) after \(delay)s: \(error.localizedDescription)")
            
            try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        }
    }
    
    throw lastError ?? DMPError(
        code: .noProviderAvailable,
        message: "All retry attempts failed"
    )
}