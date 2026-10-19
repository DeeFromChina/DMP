import Foundation
import os

// MARK: - 日志级别
enum DMPLogLevel: Int, Comparable {
    case trace = 0
    case debug = 1
    case info = 2
    case warn = 3
    case error = 4
    case fatal = 5
    
    var description: String {
        switch self {
        case .trace: return "TRACE"
        case .debug: return "DEBUG"
        case .info: return "INFO"
        case .warn: return "WARN"
        case .error: return "ERROR"
        case .fatal: return "FATAL"
        }
    }
    
    var osLogType: OSLogType {
        switch self {
        case .trace, .debug: return .debug
        case .info: return .info
        case .warn: return .default
        case .error, .fatal: return .error
        }
    }
}

// MARK: - 日志输出器
protocol DMPLogOutput {
    func output(_ entry: DMPLogEntry)
}

// MARK: - 控制台输出
struct DMPConsoleOutput: DMPLogOutput {
    func output(_ entry: DMPLogEntry) {
        let log = OSLog(subsystem: entry.subsystem, category: entry.category)
        os_log("%{public}@", log: log, type: entry.level.osLogType, entry.formattedMessage)
    }
}

// MARK: - 文件输出
class DMPFileOutput: DMPLogOutput {
    private let fileURL: URL
    private let maxFileSize: Int
    private var fileHandle: FileHandle?
    private let queue = DispatchQueue(label: "com.dmp.logger.file")
    
    init(fileURL: URL, maxFileSize: Int = 10 * 1024 * 1024) { // 10MB
        self.fileURL = fileURL
        self.maxFileSize = maxFileSize
        setupFile()
    }
    
    private func setupFile() {
        do {
            if !FileManager.default.fileExists(atPath: fileURL.path) {
                FileManager.default.createFile(atPath: fileURL.path, contents: nil)
            }
            fileHandle = try FileHandle(forWritingTo: fileURL)
            fileHandle?.seekToEndOfFile()
        } catch {
            print("Failed to setup log file: \(error)")
        }
    }
    
    func output(_ entry: DMPLogEntry) {
        queue.async {
            self.write(entry)
        }
    }
    
    private func write(_ entry: DMPLogEntry) {
        guard let data = (entry.formattedMessage + "\n").data(using: .utf8) else { return }
        
        // 检查文件大小
        if let fileSize = try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize,
           fileSize + data.count > maxFileSize {
            rotateFile()
        }
        
        fileHandle?.write(data)
        fileHandle?.synchronizeFile()
    }
    
    private func rotateFile() {
        fileHandle?.closeFile()
        
        let rotatedURL = fileURL.deletingLastPathComponent()
            .appendingPathComponent("dmp-\(Date().toString(format: "yyyy-MM-dd")).log")
        
        try? FileManager.default.moveItem(at: fileURL, to: rotatedURL)
        setupFile()
    }
}

// MARK: - 日志条目
struct DMPLogEntry {
    let timestamp: Date
    let level: DMPLogLevel
    let subsystem: String
    let category: String
    let traceID: String?
    let message: String
    let file: String
    let function: String
    let line: Int
    
    var formattedMessage: String {
        let time = timestamp.toString(format: "yyyy-MM-dd'T'HH:mm:ss.SSS")
        let trace = traceID.map { "[\($0)] " } ?? ""
        let fileName = (file as NSString).lastPathComponent
        return "[\(time)] [\(level.description)] [\(category)] \(trace)\(message) (\(fileName):\(line))"
    }
}

// MARK: - 日志管理器
final class DMPLogger {
    static let shared = DMPLogger()
    
    var logLevel: DMPLogLevel = .info
    var outputs: [DMPLogOutput] = []
    var traceID: String?
    var enableMasking = true
    
    private let queue = DispatchQueue(label: "com.dmp.logger", qos: .utility)
    private let subsystem = "com.dmp.logger"
    
    private init() {
        // 默认添加控制台输出
        outputs.append(DMPConsoleOutput())
        
        // 添加文件输出
        if let logsDir = try? FileManager.default.url(
            for: .cachesDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ) {
            let fileURL = logsDir.appendingPathComponent("dmp.log")
            outputs.append(DMPFileOutput(fileURL: fileURL))
        }
    }
    
    func configure(level: DMPLogLevel, enableConsole: Bool = true, enableFile: Bool = true) {
        logLevel = level
        outputs.removeAll()
        
        if enableConsole {
            outputs.append(DMPConsoleOutput())
        }
        
        if enableFile {
            if let logsDir = try? FileManager.default.url(
                for: .cachesDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            ) {
                let fileURL = logsDir.appendingPathComponent("dmp.log")
                outputs.append(DMPFileOutput(fileURL: fileURL))
            }
        }
    }
    
    func withTraceID<T>(_ traceID: String, execute: () -> T) -> T {
        let oldTraceID = self.traceID
        self.traceID = traceID
        defer { self.traceID = oldTraceID }
        return execute()
    }
    
    func trace(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(.trace, message, file: file, function: function, line: line)
    }
    
    func debug(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(.debug, message, file: file, function: function, line: line)
    }
    
    func info(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(.info, message, file: file, function: function, line: line)
    }
    
    func warn(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(.warn, message, file: file, function: function, line: line)
    }
    
    func error(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(.error, message, file: file, function: function, line: line)
    }
    
    func fatal(_ message: String, file: String = #file, function: String = #function, line: Int = #line) {
        log(.fatal, message, file: file, function: function, line: line)
    }
    
    private func log(_ level: DMPLogLevel, _ message: String, file: String, function: String, line: Int) {
        guard level >= logLevel else { return }
        
        let entry = DMPLogEntry(
            timestamp: Date(),
            level: level,
            subsystem: subsystem,
            category: extractCategory(file: file),
            traceID: traceID,
            message: enableMasking ? maskSensitiveData(message) : message,
            file: file,
            function: function,
            line: line
        )
        
        for output in outputs {
            output.output(entry)
        }
        
        // 同时推送到HTTP服务器缓冲区（Debug模式）
        #if DEBUG
        DMPLogServer.addLog(entry)
        #endif
    }
    
    private func extractCategory(file: String) -> String {
        let fileName = (file as NSString).lastPathComponent
        return fileName.replacingOccurrences(of: ".swift", with: "")
    }
    
    private func maskSensitiveData(_ message: String) -> String {
        var masked = message
        
        // 掩码 API Key (sk-xxxx...)
        let apiKeyPattern = #"sk-[a-zA-Z0-9]{10,}"#
        if let regex = try? NSRegularExpression(pattern: apiKeyPattern) {
            let range = NSRange(masked.startIndex..., in: masked)
            masked = regex.stringByReplacingMatches(
                in: masked,
                range: range,
                withTemplate: "sk-****"
            )
        }
        
        // 掩码 Bearer Token
        let tokenPattern = #"Bearer [a-zA-Z0-9\._\-]+"#
        if let regex = try? NSRegularExpression(pattern: tokenPattern) {
            let range = NSRange(masked.startIndex..., in: masked)
            masked = regex.stringByReplacingMatches(
                in: masked,
                range: range,
                withTemplate: "Bearer ****"
            )
        }
        
        return masked
    }
}

// MARK: - 日期格式化
extension Date {
    func toString(format: String) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = format
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: self)
    }
}

// MARK: - 便捷宏
#define DMP_LOG_TRACE(msg) DMPLogger.shared.trace(msg)
#define DMP_LOG_DEBUG(msg) DMPLogger.shared.debug(msg)
#define DMP_LOG_INFO(msg) DMPLogger.shared.info(msg)
#define DMP_LOG_WARN(msg) DMPLogger.shared.warn(msg)
#define DMP_LOG_ERROR(msg) DMPLogger.shared.error(msg)
#define DMP_LOG_FATAL(msg) DMPLogger.shared.fatal(msg)