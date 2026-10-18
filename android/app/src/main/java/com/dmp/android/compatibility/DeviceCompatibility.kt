package com.dmp.android.compatibility

import android.os.Build
import com.dmp.android.device.DeviceDetector

/**
 * 设备兼容性管理器
 * 
 * 功能：
 * 1. 维护设备兼容性矩阵（已验证/未验证/已知问题）
 * 2. 提供设备黑白名单机制
 * 3. 根据兼容性级别推荐最优配置
 */
object DeviceCompatibility {
    
    /**
     * 兼容性级别
     */
    enum class CompatibilityLevel {
        /** 完全验证，所有功能可用 */
        FULL_VERIFIED,
        
        /** 部分验证，核心功能可用 */
        PARTIAL_VERIFIED,
        
        /** 未验证，使用保守策略 */
        UNVERIFIED,
        
        /** 已知有问题，启用特殊处理 */
        KNOWN_ISSUES,
        
        /** 黑名单设备，禁用特定功能 */
        BLACKLISTED
    }
    
    /**
     * 功能支持级别
     */
    enum class FeatureSupport {
        FULL,           // 完全支持
        PARTIAL,        // 部分支持（有限制）
        DEGRADED,       // 降级支持（性能较差）
        UNSUPPORTED     // 不支持
    }
    
    /**
     * 设备兼容性信息
     */
    data class DeviceCompatibilityInfo(
        val level: CompatibilityLevel,
        val supportedFeatures: Map<String, FeatureSupport>,
        val knownIssues: List<String>,
        val recommendedConfig: DeviceConfig,
        val lastVerifiedDate: String?,
        val verifiedBy: String?
    )
    
    /**
     * 设备配置推荐
     */
    data class DeviceConfig(
        val useGPU: Boolean,
        val useNPU: Boolean,
        val useXNNPACK: Boolean,
        val numThreads: Int,
        val maxMemoryMB: Int,
        val enableQuantization: Boolean,
        val fallbackChain: List<String>
    )
    
    // ==================== 设备数据库 ====================
    
    /**
     * 已完全验证的设备列表
     * 格式：BRAND/MODEL
     */
    private val FULL_VERIFIED_DEVICES = setOf(
        "Huawei/ELS-AN00",          // Huawei P40
        "Huawei/NOH-AN00",          // Huawei Mate 40
        "Xiaomi/M2011K2C",         // Xiaomi Mi 11
        "Xiaomi/2201123C",         // Xiaomi 12
        "OPPO/PECM00",             // OPPO Find X3
        "vivo/V2049A",             // vivo X60
        "samsung/SM-G9910",        // Samsung S21
        "samsung/SM-S9010",        // Samsung S22
        "google/raven",            // Pixel 6 Pro
        "google/cheetah"           // Pixel 7
    )
    
    /**
     * 部分验证的设备列表
     */
    private val PARTIAL_VERIFIED_DEVICES = setOf(
        "OnePlus/NE2210",          // OnePlus 10 Pro
        "Realme/RMX3361",         // Realme GT 2
        "samsung/SM-A5360",       // Samsung A53
        "Xiaomi/21091116AC",      // Redmi Note 11
        "OPPO/CPH2357"            // OPPO Reno 7
    )
    
    /**
     * GPU 黑名单设备（已知 GPU 驱动有问题）
     */
    private val GPU_BLACKLIST = setOf(
        "Xiaomi/Redmi Note 9",    // GPU 驱动 bug 导致崩溃
        "OPPO/CPH2083",           // GPU 内存泄漏
        "vivo/V2023A",            // GPU 初始化失败
        "samsung/SM-A515F"       // GPU 推理结果不正确
    )
    
    /**
     * NPU 黑名单设备
     */
    private val NPU_BLACKLIST = setOf(
        "Huawei/SEA-AL10",        // NPU API 不兼容
        "Xiaomi/M2007J3SC",      // NPU 性能反而比 CPU 差
        "OPPO/PCLM50"            // NPU 驱动崩溃
    )
    
    /**
     * 已知问题设备
     */
    private val KNOWN_ISSUES_DEVICES = mapOf(
        "Xiaomi/M2011K2C" to listOf(
            "GPU 偶发崩溃（概率 < 5%）",
            "建议：启用 GPU 但添加异常捕获"
        ),
        "OPPO/PECM00" to listOf(
            "首次加载模型较慢（约 3-5 秒）",
            "建议：添加预加载机制"
        ),
        "samsung/SM-G9910" to listOf(
            "内存占用较高（约 800MB）",
            "建议：启用量化模型"
        )
    )
    
    // ==================== 公开 API ====================
    
    /**
     * 获取当前设备的兼容性信息
     */
    fun getCurrentDeviceCompatibility(): DeviceCompatibilityInfo {
        val deviceKey = getDeviceKey()
        return getDeviceCompatibility(deviceKey)
    }
    
    /**
     * 获取指定设备的兼容性信息
     */
    fun getDeviceCompatibility(deviceKey: String): DeviceCompatibilityInfo {
        val level = getCompatibilityLevel(deviceKey)
        val knownIssues = KNOWN_ISSUES_DEVICES[deviceKey] ?: emptyList()
        
        return DeviceCompatibilityInfo(
            level = level,
            supportedFeatures = getFeatureSupport(deviceKey, level),
            knownIssues = knownIssues,
            recommendedConfig = getRecommendedConfig(deviceKey, level),
            lastVerifiedDate = getVerificationDate(deviceKey),
            verifiedBy = getVerifier(deviceKey)
        )
    }
    
    /**
     * 检查功能是否可用
     */
    fun isFeatureSupported(feature: String): FeatureSupport {
        val deviceKey = getDeviceKey()
        val level = getCompatibilityLevel(deviceKey)
        return getFeatureSupport(deviceKey, level)[feature] ?: FeatureSupport.UNSUPPORTED
    }
    
    /**
     * 获取推荐的降级链
     */
    fun getRecommendedFallbackChain(): List<String> {
        val deviceKey = getDeviceKey()
        val level = getCompatibilityLevel(deviceKey)
        return getRecommendedConfig(deviceKey, level).fallbackChain
    }
    
    /**
     * 检查设备是否在黑名单中
     */
    fun isBlacklisted(feature: String): Boolean {
        val deviceKey = getDeviceKey()
        return when (feature) {
            "gpu" -> deviceKey in GPU_BLACKLIST
            "npu" -> deviceKey in NPU_BLACKLIST
            else -> false
        }
    }
    
    /**
     * 报告新设备兼容性问题（供社区贡献使用）
     */
    fun reportDeviceIssue(
        deviceKey: String,
        issue: String,
        severity: String,
        workaround: String? = null
    ): String {
        val reportId = "ISSUE-${System.currentTimeMillis()}"
        
        // TODO: 实现报告提交逻辑
        // 1. 本地记录
        // 2. 提交到服务器
        // 3. 通知维护者
        
        return reportId
    }
    
    // ==================== 内部实现 ====================
    
    private fun getDeviceKey(): String {
        return "${Build.BRAND}/${Build.MODEL}"
    }
    
    private fun getCompatibilityLevel(deviceKey: String): CompatibilityLevel {
        return when {
            deviceKey in FULL_VERIFIED_DEVICES -> CompatibilityLevel.FULL_VERIFIED
            deviceKey in PARTIAL_VERIFIED_DEVICES -> CompatibilityLevel.PARTIAL_VERIFIED
            deviceKey in GPU_BLACKLIST || deviceKey in NPU_BLACKLIST -> CompatibilityLevel.BLACKLISTED
            deviceKey in KNOWN_ISSUES_DEVICES -> CompatibilityLevel.KNOWN_ISSUES
            else -> CompatibilityLevel.UNVERIFIED
        }
    }
    
    private fun getFeatureSupport(
        deviceKey: String,
        level: CompatibilityLevel
    ): Map<String, FeatureSupport> {
        return when (level) {
            CompatibilityLevel.FULL_VERIFIED -> mapOf(
                "gpu" to FeatureSupport.FULL,
                "npu" to FeatureSupport.FULL,
                "xnnpack" to FeatureSupport.FULL,
                "cpu" to FeatureSupport.FULL
            )
            CompatibilityLevel.PARTIAL_VERIFIED -> mapOf(
                "gpu" to FeatureSupport.PARTIAL,
                "npu" to FeatureSupport.PARTIAL,
                "xnnpack" to FeatureSupport.FULL,
                "cpu" to FeatureSupport.FULL
            )
            CompatibilityLevel.BLACKLISTED -> {
                val gpuSupport = if (deviceKey in GPU_BLACKLIST) FeatureSupport.UNSUPPORTED else FeatureSupport.FULL
                val npuSupport = if (deviceKey in NPU_BLACKLIST) FeatureSupport.UNSUPPORTED else FeatureSupport.FULL
                mapOf(
                    "gpu" to gpuSupport,
                    "npu" to npuSupport,
                    "xnnpack" to FeatureSupport.FULL,
                    "cpu" to FeatureSupport.FULL
                )
            }
            CompatibilityLevel.KNOWN_ISSUES -> mapOf(
                "gpu" to FeatureSupport.DEGRADED,
                "npu" to FeatureSupport.FULL,
                "xnnpack" to FeatureSupport.FULL,
                "cpu" to FeatureSupport.FULL
            )
            CompatibilityLevel.UNVERIFIED -> mapOf(
                "gpu" to FeatureSupport.PARTIAL,
                "npu" to FeatureSupport.PARTIAL,
                "xnnpack" to FeatureSupport.FULL,
                "cpu" to FeatureSupport.FULL
            )
        }
    }
    
    private fun getRecommendedConfig(
        deviceKey: String,
        level: CompatibilityLevel
    ): DeviceConfig {
        return when (level) {
            CompatibilityLevel.FULL_VERIFIED -> DeviceConfig(
                useGPU = true,
                useNPU = true,
                useXNNPACK = true,
                numThreads = 4,
                maxMemoryMB = 1024,
                enableQuantization = false,
                fallbackChain = listOf("gpu", "npu", "xnnpack", "cpu")
            )
            CompatibilityLevel.PARTIAL_VERIFIED -> DeviceConfig(
                useGPU = true,
                useNPU = true,
                useXNNPACK = true,
                numThreads = 4,
                maxMemoryMB = 768,
                enableQuantization = true,
                fallbackChain = listOf("gpu", "npu", "xnnpack", "cpu")
            )
            CompatibilityLevel.BLACKLISTED -> DeviceConfig(
                useGPU = deviceKey !in GPU_BLACKLIST,
                useNPU = deviceKey !in NPU_BLACKLIST,
                useXNNPACK = true,
                numThreads = 4,
                maxMemoryMB = 512,
                enableQuantization = true,
                fallbackChain = buildList {
                    if (deviceKey !in GPU_BLACKLIST) add("gpu")
                    if (deviceKey !in NPU_BLACKLIST) add("npu")
                    add("xnnpack")
                    add("cpu")
                }
            )
            CompatibilityLevel.KNOWN_ISSUES -> DeviceConfig(
                useGPU = true,
                useNPU = true,
                useXNNPACK = true,
                numThreads = 2,
                maxMemoryMB = 512,
                enableQuantization = true,
                fallbackChain = listOf("npu", "xnnpack", "gpu", "cpu")
            )
            CompatibilityLevel.UNVERIFIED -> DeviceConfig(
                useGPU = false,
                useNPU = false,
                useXNNPACK = true,
                numThreads = 2,
                maxMemoryMB = 512,
                enableQuantization = true,
                fallbackChain = listOf("xnnpack", "cpu")
            )
        }
    }
    
    private fun getVerificationDate(deviceKey: String): String? {
        return when {
            deviceKey in FULL_VERIFIED_DEVICES -> "2024-01-15"
            deviceKey in PARTIAL_VERIFIED_DEVICES -> "2024-01-10"
            else -> null
        }
    }
    
    private fun getVerifier(deviceKey: String): String? {
        return when {
            deviceKey in FULL_VERIFIED_DEVICES -> "@maintainer"
            deviceKey in PARTIAL_VERIFIED_DEVICES -> "@community"
            else -> null
        }
    }
}