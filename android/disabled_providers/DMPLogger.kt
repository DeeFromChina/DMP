package com.dmp.android.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * 日志级别
 */
enum class DMPLogLevel(val value: Int, val description: String) {
    TRACE(0, "TRACE"),
    DEBUG(1, "DEBUG"),
    INFO(2, "INFO"),
    WARN(3, "WARN"),
    ERROR(4, "ERROR"),
    FATAL(5, "FATAL");

    operator fun compareTo(other: DMPLogLevel): Int = this.value - other.value
}

/**
 * 日志条目
 */
data class DMPLogEntry(
    val timestamp: Date,
    val level: DMPLogLevel,
    val tag: String,
    val traceID: String?,
    val message: String,
    val file: String,
    val function: String,
    val line: Int
) {
    val formattedMessage: String
        get() {
            val time = dateFormat.format(timestamp)
            val trace = traceID?.let { "[$it] " } ?: ""
            val fileName = file.substringAfterLast("/")
            return "[$time] [${level.description}] [$tag] $trace$message ($fileName:$line)"
        }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }
}

/**
 * 日志输出器接口
 */
interface DMPLogOutput {
    fun output(entry: DMPLogEntry)
}

/**
 * 控制台输出
 */
class DMPConsoleOutput : DMPLogOutput {
    override fun output(entry: DMPLogEntry) {
        when (entry.level) {
            DMPLogLevel.TRACE -> Log.v(entry.tag, entry.formattedMessage)
            DMPLogLevel.DEBUG -> Log.d(entry.tag, entry.formattedMessage)
            DMPLogLevel.INFO -> Log.i(entry.tag, entry.formattedMessage)
            DMPLogLevel.WARN -> Log.w(entry.tag, entry.formattedMessage)
            DMPLogLevel.ERROR -> Log.e(entry.tag, entry.formattedMessage)
            DMPLogLevel.FATAL -> Log.wtf(entry.tag, entry.formattedMessage)
        }
    }
}

/**
 * 文件输出
 */
class DMPFileOutput(private val context: Context, maxFileSize: Long = 10 * 1024 * 1024) : DMPLogOutput {
    private val logFile: File
    private val maxFileSize: Long
    private val executor = Executors.newSingleThreadExecutor()

    init {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        logFile = File(logsDir, "dmp.log")
        this.maxFileSize = maxFileSize
    }

    override fun output(entry: DMPLogEntry) {
        executor.execute {
            write(entry)
        }
    }

    private fun write(entry: DMPLogEntry) {
        try {
            // 检查文件大小
            if (logFile.exists() && logFile.length() + entry.formattedMessage.length > maxFileSize) {
                rotateFile()
            }

            FileWriter(logFile, true).use { writer ->
                writer.write(entry.formattedMessage + "\n")
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e("DMPFileOutput", "Failed to write log", e)
        }
    }

    private fun rotateFile() {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val rotatedFile = File(logFile.parent, "dmp-${dateFormat.format(Date())}.log")
            logFile.renameTo(rotatedFile)
        } catch (e: Exception) {
            Log.e("DMPFileOutput", "Failed to rotate log file", e)
        }
    }
}

/**
 * 日志管理器
 */
object DMPLogger {
    @Volatile
    var logLevel: DMPLogLevel = DMPLogLevel.INFO

    private val outputs = mutableListOf<DMPLogOutput>()
    @Volatile
    var traceID: String? = null
    @Volatile
    var enableMasking = true

    private const val DEFAULT_TAG = "DMP"

    init {
        // 默认添加控制台输出
        outputs.add(DMPConsoleOutput())
    }

    /**
     * 配置日志系统
     */
    fun configure(context: Context, level: DMPLogLevel, enableConsole: Boolean = true, enableFile: Boolean = true) {
        logLevel = level
        outputs.clear()

        if (enableConsole) {
            outputs.add(DMPConsoleOutput())
        }

        if (enableFile) {
            outputs.add(DMPFileOutput(context))
        }
    }

    /**
     * 带TraceID执行
     */
    fun <T> withTraceID(traceID: String, execute: () -> T): T {
        val oldTraceID = this.traceID
        this.traceID = traceID
        return try {
            execute()
        } finally {
            this.traceID = oldTraceID
        }
    }

    /**
     * 带TraceID执行（支持挂起函数）
     */
    suspend fun <T> withTraceIDSuspend(traceID: String, execute: suspend () -> T): T {
        val oldTraceID = this.traceID
        this.traceID = traceID
        return try {
            execute()
        } finally {
            this.traceID = oldTraceID
        }
    }

    /**
     * TRACE级别日志
     */
    @JvmStatic
    fun trace(message: String, file: String = "", function: String = "", line: Int = 0) {
        log(DMPLogLevel.TRACE, message, file, function, line)
    }

    /**
     * DEBUG级别日志
     */
    @JvmStatic
    fun debug(message: String, file: String = "", function: String = "", line: Int = 0) {
        log(DMPLogLevel.DEBUG, message, file, function, line)
    }

    /**
     * INFO级别日志
     */
    @JvmStatic
    fun info(message: String, file: String = "", function: String = "", line: Int = 0) {
        log(DMPLogLevel.INFO, message, file, function, line)
    }

    /**
     * WARN级别日志
     */
    @JvmStatic
    fun warn(message: String, file: String = "", function: String = "", line: Int = 0) {
        log(DMPLogLevel.WARN, message, file, function, line)
    }

    /**
     * ERROR级别日志
     */
    @JvmStatic
    fun error(message: String, file: String = "", function: String = "", line: Int = 0, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$message\n${sw.toString()}"
        } else {
            message
        }
        log(DMPLogLevel.ERROR, fullMessage, file, function, line)
    }

    /**
     * FATAL级别日志
     */
    @JvmStatic
    fun fatal(message: String, file: String = "", function: String = "", line: Int = 0) {
        log(DMPLogLevel.FATAL, message, file, function, line)
    }

    /**
     * 内部日志记录方法
     */
    private fun log(level: DMPLogLevel, message: String, file: String, function: String, line: Int) {
        if (level < logLevel) return

        val tag = if (file.isNotEmpty()) {
            file.substringAfterLast("/").substringBefore(".")
        } else {
            DEFAULT_TAG
        }

        val processedMessage = if (enableMasking) maskSensitiveData(message) else message

        val entry = DMPLogEntry(
            timestamp = Date(),
            level = level,
            tag = tag,
            traceID = traceID,
            message = processedMessage,
            file = file,
            function = function,
            line = line
        )

        outputs.forEach { it.output(entry) }
        
        // 同时推送到HTTP服务器缓冲区（Debug模式）
        if (BuildConfig.DEBUG) {
            DMPLogServer.addLog(entry)
        }
    }

    /**
     * 敏感数据脱敏
     */
    private fun maskSensitiveData(message: String): String {
        var masked = message

        // 掩码 API Key (sk-xxxx...)
        val apiKeyPattern = Pattern.compile("sk-[a-zA-Z0-9]{10,}")
        val apiKeyMatcher = apiKeyPattern.matcher(masked)
        masked = apiKeyMatcher.replaceAll("sk-****")

        // 掩码 Bearer Token
        val tokenPattern = Pattern.compile("Bearer [a-zA-Z0-9._\\-]+")
        val tokenMatcher = tokenPattern.matcher(masked)
        masked = tokenMatcher.replaceAll("Bearer ****")

        return masked
    }
}