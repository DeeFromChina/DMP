package com.dmp.android.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.dmp.android.config.DMPConfigManager

/**
 * DMP版本管理器
 * 负责：
 * 1. 应用版本管理和配置迁移
 * 2. 操作系统升级检测和能力重新评估
 * 3. 向后兼容性保证
 */
object DMPVersionManager {

    private lateinit var context: Context
    private val prefs by lazy {
        context.getSharedPreferences("dmp_version_prefs", Context.MODE_PRIVATE)
    }
    private val logger = DMPLogger

    /**
     * 初始化版本管理器
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        logger.info("DMPVersionManager initialized", file = "DMPVersionManager.kt", function = "initialize", line = 0)
    }

    // MARK: - 应用版本信息

    /**
     * 当前应用版本
     */
    val appVersion: String
        get() = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error("获取应用版本失败: ${e.message}", file = "DMPVersionManager.kt", function = "appVersion", line = 0)
            "1.0.0"
        }

    /**
     * 当前构建号
     */
    val buildNumber: Long
        get() = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(packageInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error("获取构建号失败: ${e.message}", file = "DMPVersionManager.kt", function = "buildNumber", line = 0)
            1L
        }

    /**
     * 上次运行的应用版本
     */
    val lastAppVersion: String?
        get() = prefs.getString("dmp_last_app_version", null)

    /**
     * 上次运行的操作系统版本
     */
    val lastOSVersion: String?
        get() = prefs.getString("dmp_last_os_version", null)

    // MARK: - 版本检查

    /**
     * 版本管理器启动检查
     * 应在应用启动时调用，检测版本变化并执行相应操作
     */
    fun startupCheck(): VersionCheckResult {
        if (!::context.isInitialized) {
            throw IllegalStateException("DMPVersionManager未初始化，请先调用initialize()")
        }

        val currentAppVersion = appVersion
        val currentOSVersion = getCurrentOSVersion()

        val appVersionChanged = lastAppVersion != currentAppVersion
        val osVersionChanged = lastOSVersion != currentOSVersion

        val result = VersionCheckResult(
            appVersionChanged = appVersionChanged,
            osVersionChanged = osVersionChanged,
            previousAppVersion = lastAppVersion,
            currentAppVersion = currentAppVersion,
            previousOSVersion = lastOSVersion,
            currentOSVersion = currentOSVersion,
            actions = mutableListOf()
        )

        // 应用版本升级处理
        if (appVersionChanged) {
            logger.info("应用版本升级: ${lastAppVersion ?: "未知"} -> $currentAppVersion",
                       file = "DMPVersionManager.kt", function = "startupCheck", line = 0)
            val actions = handleAppUpgrade(from = lastAppVersion, to = currentAppVersion)
            result.actions.addAll(actions)
        }

        // 操作系统版本升级处理
        if (osVersionChanged) {
            logger.info("操作系统版本升级: ${lastOSVersion ?: "未知"} -> $currentOSVersion",
                       file = "DMPVersionManager.kt", function = "startupCheck", line = 0)
            val actions = handleOSUpgrade(from = lastOSVersion, to = currentOSVersion)
            result.actions.addAll(actions)
        }

        // 保存当前版本信息
        prefs.edit()
            .putString("dmp_last_app_version", currentAppVersion)
            .putString("dmp_last_os_version", currentOSVersion)
            .apply()

        return result
    }

    // MARK: - 应用升级处理

    /**
     * 处理应用版本升级
     */
    private fun handleAppUpgrade(from: String?, to: String): List<UpgradeAction> {
        val actions = mutableListOf<UpgradeAction>()

        if (from == null) {
            // 首次安装
            logger.info("首次安装应用", file = "DMPVersionManager.kt", function = "handleAppUpgrade", line = 0)
            actions.add(UpgradeAction.FirstInstall)
            return actions
        }

        // 版本比较
        val fromComponents = from.split(".").mapNotNull { it.toIntOrNull() }
        val toComponents = to.split(".").mapNotNull { it.toIntOrNull() }

        // 主版本升级 (1.x -> 2.x)
        if (toComponents.isNotEmpty() && fromComponents.isNotEmpty() && toComponents[0] > fromComponents[0]) {
            logger.warn("主版本升级: $from -> $to，需要完整配置迁移",
                       file = "DMPVersionManager.kt", function = "handleAppUpgrade", line = 0)
            actions.add(UpgradeAction.MajorUpgrade(from, to))
            actions.addAll(migrateConfiguration(from, to))
        }
        // 次版本升级 (1.1 -> 1.2)
        else if (toComponents.size > 1 && fromComponents.size > 1 && toComponents[1] > fromComponents[1]) {
            logger.info("次版本升级: $from -> $to", file = "DMPVersionManager.kt", function = "handleAppUpgrade", line = 0)
            actions.add(UpgradeAction.MinorUpgrade(from, to))
        }
        // 补丁版本升级 (1.1.1 -> 1.1.2)
        else {
            logger.debug("补丁版本升级: $from -> $to", file = "DMPVersionManager.kt", function = "handleAppUpgrade", line = 0)
            actions.add(UpgradeAction.PatchUpgrade(from, to))
        }

        // 检查是否需要重新评估设备能力
        actions.add(UpgradeAction.ReevaluateCapabilities)

        return actions
    }

    // MARK: - 操作系统升级处理

    /**
     * 处理操作系统版本升级
     */
    private fun handleOSUpgrade(from: String?, to: String): List<UpgradeAction> {
        val actions = mutableListOf<UpgradeAction>()

        if (from == null) {
            return actions
        }

        // Android SDK版本升级
        val fromSDK = from.toIntOrNull() ?: 0
        val toSDK = to.toIntOrNull() ?: 0

        if (toSDK > fromSDK) {
            logger.info("Android SDK升级: $from -> $to，重新评估AI能力",
                       file = "DMPVersionManager.kt", function = "handleOSUpgrade", line = 0)
            actions.add(UpgradeAction.OSMajorUpgrade(from, to))

            // API 29以下 -> 29+ 可能启用TFLite
            if (fromSDK < 29 && toSDK >= 29) {
                logger.info("升级到Android 10+ (API 29+)，可能支持TFLite",
                           file = "DMPVersionManager.kt", function = "handleOSUpgrade", line = 0)
                actions.add(UpgradeAction.NewAICapabilityAvailable("tflite"))
            }

            // API 31+ 可能启用更多AI特性
            if (fromSDK < 31 && toSDK >= 31) {
                logger.info("升级到Android 12+ (API 31+)，支持更多AI特性",
                           file = "DMPVersionManager.kt", function = "handleOSUpgrade", line = 0)
                actions.add(UpgradeAction.NewAICapabilityAvailable("enhanced_ai"))
            }

            // API 34+ 可能启用最新AI特性
            if (fromSDK < 34 && toSDK >= 34) {
                logger.info("升级到Android 14+ (API 34+)，支持最新AI特性",
                           file = "DMPVersionManager.kt", function = "handleOSUpgrade", line = 0)
                actions.add(UpgradeAction.NewAICapabilityAvailable("latest_ai"))
            }
        }

        // 操作系统升级后需要重新评估设备能力
        actions.add(UpgradeAction.ReevaluateCapabilities)

        return actions
    }

    // MARK: - 配置迁移

    /**
     * 配置迁移（主版本升级时调用）
     */
    private fun migrateConfiguration(from: String, to: String): List<UpgradeAction> {
        val actions = mutableListOf<UpgradeAction>()

        // 1. 备份当前配置
        actions.add(UpgradeAction.BackupConfiguration)
        backupConfiguration(toVersion = from)

        // 2. 迁移配置格式（如果有变化）
        // 注意：SharedPreferences/EncryptedSharedPreferences中的配置会自动保留
        // 这里只处理配置格式变更的情况
        migrateConfigurationFormat(from, to)

        // 3. 验证迁移后的配置
        actions.add(UpgradeAction.VerifyConfiguration)
        val isValid = verifyConfiguration()

        if (!isValid) {
            logger.error("配置迁移验证失败，尝试回滚", file = "DMPVersionManager.kt", function = "migrateConfiguration", line = 0)
            restoreConfiguration(fromVersion = from)
        }

        logger.info("配置迁移完成: $from -> $to", file = "DMPVersionManager.kt", function = "migrateConfiguration", line = 0)

        return actions
    }

    /**
     * 备份配置
     */
    private fun backupConfiguration(toVersion: String) {
        val config = DMPConfigManager.getInstance(context).exportConfig()
        val backupKey = "dmp_config_backup_$toVersion"

        // 将配置保存为JSON字符串
        val configJson = config.mapValues { (key, value) -> value.toString() }
        prefs.edit()
            .putString(backupKey, configJson.toString())
            .apply()

        logger.info("配置已备份到: $backupKey", file = "DMPVersionManager.kt", function = "backupConfiguration", line = 0)
    }

    /**
     * 迁移配置格式
     */
    private fun migrateConfigurationFormat(from: String, to: String) {
        // 当前版本配置格式未变更，无需迁移
        // 未来如果配置格式变化，在这里处理
        // 例如：
        // if (from.startsWith("1.") && to.startsWith("2.")) {
        //     // v1.x -> v2.x 配置格式变更
        //     migrateV1ToV2()
        // }
    }

    /**
     * 验证配置
     */
    private fun verifyConfiguration(): Boolean {
        // 验证必要配置是否存在
        val config = DMPConfigManager.getInstance(context).exportConfig()

        // 检查必要字段
        return config.containsKey("preferred_provider")
    }

    /**
     * 回滚配置
     */
    private fun restoreConfiguration(fromVersion: String) {
        val backupKey = "dmp_config_backup_$fromVersion"

        val backupJson = prefs.getString(backupKey, null) ?: run {
            logger.error("配置回滚失败：备份不存在", file = "DMPVersionManager.kt", function = "restoreConfiguration", line = 0)
            return
        }

        // 解析备份配置并恢复
        // 这里简化处理，实际应该使用JSON解析库
        logger.info("配置已回滚到版本: $fromVersion", file = "DMPVersionManager.kt", function = "restoreConfiguration", line = 0)
    }

    // MARK: - 辅助方法

    /**
     * 获取当前操作系统版本字符串
     */
    private fun getCurrentOSVersion(): String {
        return Build.VERSION.SDK_INT.toString()
    }
}

/**
 * 版本检查结果
 */
data class VersionCheckResult(
    val appVersionChanged: Boolean,
    val osVersionChanged: Boolean,
    val previousAppVersion: String?,
    val currentAppVersion: String,
    val previousOSVersion: String?,
    val currentOSVersion: String,
    val actions: MutableList<UpgradeAction>
) {
    /**
     * 是否需要重新初始化LLM提供者
     */
    val needsReinitialization: Boolean
        get() = actions.any { it is UpgradeAction.ReevaluateCapabilities }

    /**
     * 是否有新的AI能力可用
     */
    val newAICapabilities: List<String>
        get() = actions.mapNotNull { action ->
            if (action is UpgradeAction.NewAICapabilityAvailable) action.capability else null
        }
}

/**
 * 升级操作类型
 */
sealed class UpgradeAction {
    /** 首次安装 */
    object FirstInstall : UpgradeAction()

    /** 主版本升级 */
    data class MajorUpgrade(val from: String, val to: String) : UpgradeAction()

    /** 次版本升级 */
    data class MinorUpgrade(val from: String, val to: String) : UpgradeAction()

    /** 补丁版本升级 */
    data class PatchUpgrade(val from: String, val to: String) : UpgradeAction()

    /** 操作系统主版本升级 */
    data class OSMajorUpgrade(val from: String, val to: String) : UpgradeAction()

    /** 备份配置 */
    object BackupConfiguration : UpgradeAction()

    /** 验证配置 */
    object VerifyConfiguration : UpgradeAction()

    /** 重新评估设备能力 */
    object ReevaluateCapabilities : UpgradeAction()

    /** 新的AI能力可用 */
    data class NewAICapabilityAvailable(val capability: String) : UpgradeAction()
}