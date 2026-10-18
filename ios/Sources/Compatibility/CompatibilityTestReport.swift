import Foundation

/// 兼容性测试报告
///
/// 用于社区贡献者提交设备测试报告
public struct CompatibilityTestReport: Codable {
    
    public enum TestStatus: String, Codable {
        case passed = "PASSED"
        case passedWithWarnings = "PASSED_WITH_WARNINGS"
        case failed = "FAILED"
        case partial = "PARTIAL"
    }
    
    public struct DeviceInfo: Codable {
        public let deviceIdentifier: String
        public let deviceName: String
        public let iosVersion: String
        public let cpuArchitecture: String
        public let ramMB: Int
        public let gpuModel: String?
    }
    
    public struct TestResult: Codable {
        public let testName: String
        public let status: TestStatus
        public let durationMs: Int64
        public let errorMessage: String?
        public let metrics: [String: String]?
    }
    
    public let deviceInfo: DeviceInfo
    public let testResults: [TestResult]
    public let overallStatus: TestStatus
    public let testedBy: String
    public let testDate: String
    public let notes: String?
}