package com.dmp.android.compatibility

/**
 * 兼容性测试报告
 * 
 * 用于社区贡献者提交设备测试报告
 */
data class CompatibilityTestReport(
    val deviceInfo: DeviceInfo,
    val testResults: List<TestResult>,
    val overallStatus: TestStatus,
    val testedBy: String,
    val testDate: String,
    val notes: String?
) {
    enum class TestStatus {
        PASSED,
        PASSED_WITH_WARNINGS,
        FAILED,
        PARTIAL
    }
    
    data class DeviceInfo(
        val brand: String,
        val model: String,
        val androidVersion: String,
        val sdkInt: Int,
        val cpuArchitecture: String,
        val ramMB: Int,
        val gpuModel: String?
    )
    
    data class TestResult(
        val testName: String,
        val status: TestStatus,
        val durationMs: Long,
        val errorMessage: String?,
        val metrics: Map<String, Any>?
    )
}