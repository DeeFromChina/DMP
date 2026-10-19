package com.dmp.demo.sdk

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 简单的日志工具，输出到文件
 */
object Logger {
    private const val TAG = "DMPDemoSDK"
    private var logFile: File? = null

    fun init(context: Context) {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        logFile = File(logsDir, "demo-sdk.log")
        info("Logger initialized: ${logFile?.absolutePath}")
    }

    fun info(msg: String) = log("INFO", msg)
    fun warn(msg: String) = log("WARN", msg)
    fun error(msg: String, e: Exception? = null) {
        val fullMsg = if (e != null) "$msg\n${Log.getStackTraceString(e)}" else msg
        log("ERROR", fullMsg)
    }

    private fun log(level: String, msg: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts] [$level] [$TAG] $msg"
        Log.i(TAG, msg)
        try {
            logFile?.let { FileWriter(it, true).use { w -> w.write(line + "\n"); w.flush() } }
        } catch (e: Exception) {
            Log.e(TAG, "Write log failed", e)
        }
    }
}