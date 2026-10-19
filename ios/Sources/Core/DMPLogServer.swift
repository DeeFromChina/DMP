import Foundation
import Network

#if DEBUG

/// 日志HTTP服务器（仅Debug模式可用）
/// 
/// 功能：
/// 1. 提供日志查看Web界面
/// 2. 生成二维码供电脑扫码连接
/// 3. 仅在同WiFi局域网内可访问
/// 4. Release模式自动禁用（安全保护）
class DMPLogServer {
    
    private var httpServer: LogHTTPServer?
    private var isRunning = false
    private var serverURL = ""
    
    static let shared = DMPLogServer()
    
    private init() {}
    
    /// 启动日志服务器
    func startServer() -> String {
        #if DEBUG
        // Debug模式：启动服务器
        do {
            httpServer = try LogHTTPServer(port: 28473)
            try httpServer?.start()
            isRunning = true
            serverURL = "http://\(getLocalIPAddress()):28473"
            DMPLogger.info("日志HTTP服务器已启动: \(serverURL)", file: "DMPLogServer.swift", function: "startServer", line: 0)
            return serverURL
        } catch {
            DMPLogger.error("日志HTTP服务器启动失败: \(error.localizedDescription)", file: "DMPLogServer.swift", function: "startServer", line: 0)
            return ""
        }
        #else
        // Release模式：禁用
        DMPLogger.warn("Release模式，禁用日志HTTP服务器", file: "DMPLogServer.swift", function: "startServer", line: 0)
        return ""
        #endif
    }
    
    /// 停止服务器
    func stopServer() {
        httpServer?.stop()
        isRunning = false
        DMPLogger.info("日志HTTP服务器已停止", file: "DMPLogServer.swift", function: "stopServer", line: 0)
    }
    
    /// 获取服务器URL
    func getServerURL() -> String {
        return serverURL
    }
    
    /// 检查是否运行中
    func isServerRunning() -> Bool {
        return isRunning
    }
    
    /// 获取本地IP地址
    private func getLocalIPAddress() -> String {
        var address: String?
        
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return "localhost" }
        guard let firstAddr = ifaddr else { return "localhost" }
        
        var ptr = firstAddr
        while ptr.pointee.ifa_next != nil {
            defer { ptr = ptr.pointee.ifa_next! }
            
            let interface = ptr.pointee
            let addrFamily = interface.ifa_addr.pointee.sa_family
            
            if addrFamily == UInt8(AF_INET) {
                let name = String(cString: interface.ifa_name)
                if name == "en0" || name == "en1" { // WiFi interfaces
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                               &hostname, socklen_t(hostname.count), nil, socklen_t(0), NI_NUMERICHOST)
                    address = String(cString: hostname)
                    break
                }
            }
        }
        
        freeifaddrs(ifaddr)
        return address ?? "localhost"
    }
}

/// 添加日志到缓冲区（在DMPLogger中调用）
extension DMPLogServer {
    static func addLog(_ entry: DMPLogEntry) {
        #if DEBUG
        LogBuffer.shared.addLog(entry)
        #endif
    }
    
    static func getRecentLogs(count: Int = 100) -> [DMPLogEntry] {
        #if DEBUG
        return LogBuffer.shared.getRecentLogs(count: count)
        #else
        return []
        #endif
    }
}

/// 日志缓冲区
class LogBuffer {
    static let shared = LogBuffer()
    private var logs: [DMPLogEntry] = []
    private let maxBufferSize = 2000
    private let lock = NSLock()
    
    private init() {}
    
    func addLog(_ entry: DMPLogEntry) {
        lock.lock()
        defer { lock.unlock() }
        
        logs.append(entry)
        if logs.count > maxBufferSize {
            logs.removeFirst(logs.count - maxBufferSize)
        }
    }
    
    func getRecentLogs(count: Int) -> [DMPLogEntry] {
        lock.lock()
        defer { lock.unlock() }
        
        let endIndex = logs.endIndex
        let startIndex = max(0, endIndex - count)
        return Array(logs[startIndex..<endIndex])
    }
    
    func clearLogs() {
        lock.lock()
        defer { lock.unlock() }
        logs.removeAll()
    }
}

#endif