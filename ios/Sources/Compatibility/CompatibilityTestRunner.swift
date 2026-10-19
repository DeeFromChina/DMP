import Foundation

#if os(iOS)
import UIKit
#endif

/// 兼容性测试运行器
///
/// 用于在设备上运行完整的兼容性测试套件
/// 生成测试报告供社区共享
public final class CompatibilityTestRunner {
    
    /// 运行完整测试套件
    public func runFullTestSuite(
        providers: [LLMProvider],
        testedBy: String
    ) async -> CompatibilityTestReport {
        var testResults: [CompatibilityTestReport.TestResult] = []
        
        // 1. ANE 测试
        testResults.append(await testANE())
        
        // 2. GPU 测试
        testResults.append(await testGPU())
        
        // 3. CPU 测试
        testResults.append(await testCPU())
        
        // 4. 内存测试
        testResults.append(await testMemory())
        
        // 5. Provider 测试
        for provider in providers {
            testResults.append(await testProvider(provider))
        }
        
        // 计算总体状态
        let overallStatus = calculateOverallStatus(testResults)
        
        return CompatibilityTestReport(
            deviceInfo: collectDeviceInfo(),
            testResults: testResults,
            overallStatus: overallStatus,
            testedBy: testedBy,
            testDate: ISO8601DateFormatter().string(from: Date()),
            notes: nil
        )
    }
    
    // MARK: - Private Methods
    
    /// 测试 ANE
    private func testANE() async -> CompatibilityTestReport.TestResult {
        let startTime = Date()
        do {
            let detector = RuntimeCapabilityDetector()
            let result = detector.testANE()
            
            return CompatibilityTestReport.TestResult(
                testName: "ANE (Neural Engine)",
                status: result.isSupported ? .passed : .failed,
                durationMs: result.testDurationMs,
                errorMessage: result.errorMessage,
                metrics: ["supported": String(result.isSupported)]
            )
        } catch {
            return CompatibilityTestReport.TestResult(
                testName: "ANE (Neural Engine)",
                status: .failed,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: error.localizedDescription,
                metrics: nil
            )
        }
    }
    
    /// 测试 GPU
    private func testGPU() async -> CompatibilityTestReport.TestResult {
        let startTime = Date()
        do {
            let detector = RuntimeCapabilityDetector()
            let result = detector.testGPU()
            
            return CompatibilityTestReport.TestResult(
                testName: "Metal GPU",
                status: result.isSupported ? .passed : .failed,
                durationMs: result.testDurationMs,
                errorMessage: result.errorMessage,
                metrics: ["supported": String(result.isSupported)]
            )
        } catch {
            return CompatibilityTestReport.TestResult(
                testName: "Metal GPU",
                status: .failed,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: error.localizedDescription,
                metrics: nil
            )
        }
    }
    
    /// 测试 CPU
    private func testCPU() async -> CompatibilityTestReport.TestResult {
        let startTime = Date()
        do {
            let detector = RuntimeCapabilityDetector()
            let result = detector.testCPU()
            
            return CompatibilityTestReport.TestResult(
                testName: "CPU Inference",
                status: .passed,
                durationMs: result.testDurationMs,
                errorMessage: nil,
                metrics: [
                    "cores": String(ProcessInfo.processInfo.processorCount),
                    "performance": result.performance?.rawValue ?? "UNKNOWN"
                ]
            )
        } catch {
            return CompatibilityTestReport.TestResult(
                testName: "CPU Inference",
                status: .failed,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: error.localizedDescription,
                metrics: nil
            )
        }
    }
    
    /// 测试内存
    private func testMemory() async -> CompatibilityTestReport.TestResult {
        let startTime = Date()
        do {
            let detector = RuntimeCapabilityDetector()
            let memoryInfo = detector.getMemoryInfo()
            
            let status: CompatibilityTestReport.TestStatus = memoryInfo.isLowMemoryDevice ? .passedWithWarnings : .passed
            
            return CompatibilityTestReport.TestResult(
                testName: "Memory",
                status: status,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: nil,
                metrics: [
                    "totalMemoryMB": String(memoryInfo.totalMemoryMB),
                    "availableMemoryMB": String(memoryInfo.availableMemoryMB),
                    "isLowMemory": String(memoryInfo.isLowMemoryDevice)
                ]
            )
        } catch {
            return CompatibilityTestReport.TestResult(
                testName: "Memory",
                status: .failed,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: error.localizedDescription,
                metrics: nil
            )
        }
    }
    
    /// 测试 Provider
    private func testProvider(_ provider: LLMProvider) async -> CompatibilityTestReport.TestResult {
        let startTime = Date()
        do {
            let isAvailable = await provider.isAvailable()
            
            return CompatibilityTestReport.TestResult(
                testName: "Provider: \(provider.name)",
                status: isAvailable ? .passed : .failed,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: isAvailable ? nil : "Provider 不可用",
                metrics: [
                    "available": String(isAvailable),
                    "priority": String(provider.priority)
                ]
            )
        } catch {
            return CompatibilityTestReport.TestResult(
                testName: "Provider: \(provider.name)",
                status: .failed,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000),
                errorMessage: error.localizedDescription,
                metrics: nil
            )
        }
    }
    
    /// 收集设备信息
    private func collectDeviceInfo() -> CompatibilityTestReport.DeviceInfo {
        #if os(iOS)
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        
        let deviceName = UIDevice.current.name
        let systemVersion = UIDevice.current.systemVersion
        
        return CompatibilityTestReport.DeviceInfo(
            deviceIdentifier: identifier,
            deviceName: deviceName,
            iosVersion: systemVersion,
            cpuArchitecture: "ARM64",
            ramMB: Int(ProcessInfo.processInfo.physicalMemory / (1024 * 1024)),
            gpuModel: nil
        )
        #else
        return CompatibilityTestReport.DeviceInfo(
            deviceIdentifier: "macOS",
            deviceName: "Mac",
            iosVersion: ProcessInfo.processInfo.operatingSystemVersionString,
            cpuArchitecture: "ARM64",
            ramMB: Int(ProcessInfo.processInfo.physicalMemory / (1024 * 1024)),
            gpuModel: nil
        )
        #endif
    }
    
    /// 计算总体状态
    private func calculateOverallStatus(
        _ results: [CompatibilityTestReport.TestResult]
    ) -> CompatibilityTestReport.TestStatus {
        let failed = results.filter { $0.status == .failed }.count
        let warnings = results.filter { $0.status == .passedWithWarnings }.count
        
        if failed > 0 {
            return .failed
        } else if warnings > 0 {
            return .passedWithWarnings
        } else {
            return .passed
        }
    }
}