package com.dmp.android.device

import android.os.Build

/**
 * 设备检测器
 */
object DeviceDetector {
    
    /**
     * 版本号
     */
    data class Version(
        val sdkInt: Int,
        val release: String
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            return this.sdkInt.compareTo(other.sdkInt)
        }
        
        override fun toString(): String = release
    }
    
    /**
     * 设备类型（品牌 + 系统版本）
     */
    sealed class DeviceType {
        // 国内品牌
        data class Huawei(val version: Version) : DeviceType()
        data class Xiaomi(val version: Version) : DeviceType()
        data class OPPO(val version: Version) : DeviceType()
        data class Vivo(val version: Version) : DeviceType()
        
        // 海外品牌
        data class Samsung(val version: Version) : DeviceType()
        data class Google(val version: Version) : DeviceType()
        data class Sony(val version: Version) : DeviceType()
        data class LG(val version: Version) : DeviceType()
        data class Motorola(val version: Version) : DeviceType()
        data class Nokia(val version: Version) : DeviceType()
        data class OnePlus(val version: Version) : DeviceType()
        data class Realme(val version: Version) : DeviceType()
        
        // 其他
        data class Other(val version: Version) : DeviceType()
    }
    
    /**
     * 检测当前设备（品牌 + 系统版本）
     */
    fun detectDevice(): DeviceType {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val version = Version(
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE
        )
        
        return when {
            // 国内品牌
            brand.contains("huawei") || brand.contains("honor") -> DeviceType.Huawei(version)
            brand.contains("xiaomi") || brand.contains("redmi") || manufacturer.contains("xiaomi") -> DeviceType.Xiaomi(version)
            brand.contains("oppo") -> DeviceType.OPPO(version)
            brand.contains("vivo") -> DeviceType.Vivo(version)
            brand.contains("oneplus") -> DeviceType.OnePlus(version)
            brand.contains("realme") -> DeviceType.Realme(version)
            
            // 海外品牌
            brand.contains("samsung") || manufacturer.contains("samsung") -> DeviceType.Samsung(version)
            brand.contains("google") || brand.contains("pixel") || manufacturer.contains("google") -> DeviceType.Google(version)
            brand.contains("sony") || manufacturer.contains("sony") -> DeviceType.Sony(version)
            brand.contains("lg") || manufacturer.contains("lg") -> DeviceType.LG(version)
            brand.contains("motorola") || brand.contains("moto") || manufacturer.contains("motorola") -> DeviceType.Motorola(version)
            brand.contains("nokia") || manufacturer.contains("nokia") || brand.contains("hmd global") -> DeviceType.Nokia(version)
            
            // 其他
            else -> DeviceType.Other(version)
        }
    }
    
    /**
     * 检测当前设备品牌（兼容旧API）
     */
    fun detectDeviceBrand(): DeviceType {
        return detectDevice()
    }
    
    /**
     * 获取推荐的LLM提供者列表
     * 
     * 推荐逻辑考虑：
     * 1. 设备品牌：决定可用的厂商AI SDK
     * 2. 系统版本：决定AI能力是否可用
     *    - Android 14+ (API 34): 支持最新AI特性
     *    - Android 12+ (API 31): 支持大部分AI特性
     *    - Android 10+ (API 29): 基础AI支持
     *    - Android 10以下: 仅支持云端
     */
    fun getRecommendedProviders(deviceType: DeviceType): List<String> {
        return when (deviceType) {
            // 国内品牌
            is DeviceType.Huawei -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("huawei", "tflite", "cloud")  // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("huawei", "tflite", "cloud")  // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("huawei", "cloud")            // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Xiaomi -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("xiaomi", "tflite", "cloud")  // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("xiaomi", "tflite", "cloud")  // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("xiaomi", "cloud")            // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.OPPO -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("oppo", "tflite", "cloud")    // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("oppo", "tflite", "cloud")    // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("oppo", "cloud")              // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Vivo -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("vivo", "tflite", "cloud")    // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("vivo", "tflite", "cloud")    // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("vivo", "cloud")              // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.OnePlus -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("oneplus", "tflite", "cloud") // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("oneplus", "tflite", "cloud") // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("oneplus", "cloud")           // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Realme -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("realme", "tflite", "cloud")  // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("realme", "tflite", "cloud")  // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("realme", "cloud")            // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            
            // 海外品牌
            is DeviceType.Samsung -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("samsung", "tflite", "cloud") // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("samsung", "tflite", "cloud") // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("samsung", "cloud")           // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Google -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("google", "tflite", "cloud")  // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("google", "tflite", "cloud")  // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("google", "cloud")            // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Sony -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("sony", "tflite", "cloud")    // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("sony", "tflite", "cloud")    // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("sony", "cloud")              // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.LG -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("lg", "tflite", "cloud")      // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("lg", "tflite", "cloud")      // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("lg", "cloud")                // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Motorola -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("motorola", "tflite", "cloud")// Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("motorola", "tflite", "cloud")// Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("motorola", "cloud")          // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            is DeviceType.Nokia -> {
                when {
                    deviceType.version.sdkInt >= 34 -> listOf("nokia", "tflite", "cloud")   // Android 14+
                    deviceType.version.sdkInt >= 31 -> listOf("nokia", "tflite", "cloud")   // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("nokia", "cloud")             // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
            
            // 其他品牌
            is DeviceType.Other -> {
                when {
                    deviceType.version.sdkInt >= 31 -> listOf("tflite", "cloud")            // Android 12+
                    deviceType.version.sdkInt >= 29 -> listOf("tflite", "cloud")            // Android 10+
                    else -> listOf("cloud")                                                  // Android 10以下
                }
            }
        }
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "brand" to Build.BRAND,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "sdk" to Build.VERSION.SDK_INT.toString(),
            "release" to Build.VERSION.RELEASE,
            "hardware" to Build.HARDWARE,
            "display" to Build.DISPLAY
        )
    }
    
    /**
     * 检查是否支持厂商AI SDK
     */
    fun isVendorAISupported(deviceType: DeviceType): Boolean {
        return when (deviceType) {
            // 国内品牌
            is DeviceType.Huawei,
            is DeviceType.Xiaomi,
            is DeviceType.OPPO,
            is DeviceType.Vivo,
            is DeviceType.OnePlus,
            is DeviceType.Realme -> deviceType.version.sdkInt >= 29  // Android 10+
            
            // 海外品牌
            is DeviceType.Samsung,
            is DeviceType.Google,
            is DeviceType.Sony,
            is DeviceType.LG,
            is DeviceType.Motorola,
            is DeviceType.Nokia -> deviceType.version.sdkInt >= 29  // Android 10+
            
            is DeviceType.Other -> false
        }
    }
    
    /**
     * 检查是否支持TFLite
     */
    fun isTFLiteSupported(deviceType: DeviceType): Boolean {
        return deviceType.version.sdkInt >= 29  // Android 10+
    }
    
    /**
     * 检查是否为最新系统版本
     */
    fun isLatestOS(deviceType: DeviceType): Boolean {
        return deviceType.version.sdkInt >= 34  // Android 14+
    }
}