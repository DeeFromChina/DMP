import Foundation

/// 设备检测器
public final class DeviceDetector {
    
    /// 设备类型
    public enum DeviceType {
        case iphonePro(iosVersion: Version)
        case iphoneStandard(iosVersion: Version)
        case iphoneLegacy(iosVersion: Version)
        case ipadPro
        case ipadStandard
        case unknown
    }
    
    /// 版本号
    public struct Version: Comparable {
        let major: Int
        let minor: Int
        let patch: Int
        
        public static func < (lhs: Version, rhs: Version) -> Bool {
            if lhs.major != rhs.major { return lhs.major < rhs.major }
            if lhs.minor != rhs.minor { return lhs.minor < rhs.minor }
            return lhs.patch < rhs.patch
        }
    }
    
    /// 检测当前设备
    public static func detectCurrentDevice() -> DeviceType {
        #if os(iOS)
        let systemVersion = ProcessInfo.processInfo.operatingSystemVersion
        let version = Version(
            major: systemVersion.majorVersion,
            minor: systemVersion.minorVersion,
            patch: systemVersion.patchVersion
        )
        
        let deviceModel = UIDevice.current.modelIdentifier
        
        // iPhone 15 Pro 及以上 (支持Apple Intelligence)
        if isIPhone15ProOrLater(model: deviceModel) {
            return .iphonePro(iosVersion: version)
        }
        
        // iPhone 12-14 (支持Core ML)
        if isIPhone12OrLater(model: deviceModel) {
            return .iphoneStandard(iosVersion: version)
        }
        
        // 旧款iPhone
        return .iphoneLegacy(iosVersion: version)
        
        #elseif os(macOS)
        return .unknown
        #else
        return .unknown
        #endif
    }
    
    /// 获取推荐的LLM提供者列表
    public static func getRecommendedProviders(deviceType: DeviceType) -> [String] {
        switch deviceType {
        case .iphonePro(let version) where version >= Version(major: 18, minor: 0, patch: 0):
            // iPhone 15 Pro + iOS 18: Apple Intelligence > Core ML > Cloud
            return ["apple_intelligence", "coreml", "cloud"]
            
        case .iphonePro, .iphoneStandard:
            // iPhone 12-14 或 iPhone 15 Pro (iOS < 18): Core ML > Cloud
            return ["coreml", "cloud"]
            
        case .iphoneLegacy:
            // 旧款iPhone: Cloud only
            return ["cloud"]
            
        case .ipadPro:
            // iPad Pro: Core ML > Apple Intelligence
            return ["coreml", "apple_intelligence"]
            
        case .ipadStandard:
            return ["coreml", "cloud"]
            
        case .unknown:
            return ["cloud"]
        }
    }
    
    // MARK: - Private Methods
    
    private static func isIPhone15ProOrLater(model: String) -> Bool {
        let proModels = [
            "iPhone16,1", // iPhone 15 Pro
            "iPhone16,2", // iPhone 15 Pro Max
            "iPhone17,1", // iPhone 16 Pro
            "iPhone17,2", // iPhone 16 Pro Max
        ]
        return proModels.contains(model) || model.hasPrefix("iPhone18,")
    }
    
    private static func isIPhone12OrLater(model: String) -> Bool {
        let standardModels = [
            "iPhone13,2", // iPhone 12
            "iPhone13,3", // iPhone 12 Pro
            "iPhone13,4", // iPhone 12 Pro Max
            "iPhone14,2", // iPhone 13 Pro
            "iPhone14,3", // iPhone 13 Pro Max
            "iPhone14,4", // iPhone 13 mini
            "iPhone14,5", // iPhone 13
            "iPhone14,6", // iPhone SE 3
            "iPhone14,7", // iPhone 14
            "iPhone14,8", // iPhone 14 Plus
            "iPhone15,2", // iPhone 14 Pro
            "iPhone15,3", // iPhone 14 Pro Max
            "iPhone15,4", // iPhone 15
            "iPhone15,5", // iPhone 15 Plus
        ]
        return standardModels.contains(model) || isIPhone15ProOrLater(model: model)
    }
}

#if os(iOS)
import UIKit

extension UIDevice {
    var modelIdentifier: String {
        if let identifier = ProcessInfo().environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return identifier
        }
        
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        return identifier
    }
}
#endif