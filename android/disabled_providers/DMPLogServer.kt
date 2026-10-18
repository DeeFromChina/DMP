package com.dmp.android.core

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志HTTP服务器（仅Debug模式可用）
 * 
 * 功能：
 * 1. 提供日志查看Web界面
 * 2. 生成二维码供手机扫码连接
 * 3. 仅在同WiFi局域网内可访问
 * 4. Release模式自动禁用（安全保护）
 */
class DMPLogServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val logger = DMPLogger
    private var isRunning = false
    private var serverUrl = ""

    /**
     * 启动日志服务器
     */
    fun startServer(): String {
        if (BuildConfig.DEBUG.not()) {
            logger.warn("Release模式，禁用日志HTTP服务器", file = "DMPLogServer.kt", function = "startServer", line = 0)
            return ""
        }

        return try {
            start()
            isRunning = true
            serverUrl = "http://${getLocalIpAddress()}:$port"
            logger.info("日志HTTP服务器已启动: $serverUrl", file = "DMPLogServer.kt", function = "startServer", line = 0)
            serverUrl
        } catch (e: Exception) {
            logger.error("日志HTTP服务器启动失败: ${e.message}", file = "DMPLogServer.kt", function = "startServer", line = 0, throwable = e)
            ""
        }
    }

    /**
     * 停止服务器
     */
    fun stopServer() {
        if (isRunning) {
            stop()
            isRunning = false
            logger.info("日志HTTP服务器已停止", file = "DMPLogServer.kt", function = "stopServer", line = 0)
        }
    }

    /**
     * 生成二维码Bitmap
     */
    fun generateQRCode(size: Int = 512): Bitmap? {
        if (serverUrl.isEmpty()) return null

        return try {
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H

            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(serverUrl, BarcodeFormat.QR_CODE, size, size, hints)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }

            logger.info("二维码已生成，URL: $serverUrl", file = "DMPLogServer.kt", function = "generateQRCode", line = 0)
            bitmap
        } catch (e: Exception) {
            logger.error("二维码生成失败: ${e.message}", file = "DMPLogServer.kt", function = "generateQRCode", line = 0, throwable = e)
            null
        }
    }

    /**
     * 处理HTTP请求
     */
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/" || uri == "/index.html" -> serveLogPage()
            uri == "/api/logs" -> serveLogs()
            uri == "/api/logs/recent" -> serveRecentLogs(session)
            uri == "/api/status" -> serveStatus()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    /**
     * 提供日志查看页面
     */
    private fun serveLogPage(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>DMP 日志查看器</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: #1e1e1e;
                        color: #d4d4d4;
                        padding: 20px;
                    }
                    .header {
                        background: #2d2d30;
                        padding: 15px 20px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .header h1 { color: #4ec9b0; font-size: 20px; }
                    .status { color: #4ec9b0; font-size: 14px; }
                    .controls {
                        display: flex;
                        gap: 10px;
                        margin-bottom: 20px;
                    }
                    .controls button {
                        background: #0e639c;
                        color: white;
                        border: none;
                        padding: 8px 16px;
                        border-radius: 4px;
                        cursor: pointer;
                        font-size: 14px;
                    }
                    .controls button:hover { background: #1177bb; }
                    .controls button.active { background: #4ec9b0; }
                    .log-container {
                        background: #1e1e1e;
                        border: 1px solid #3e3e42;
                        border-radius: 8px;
                        padding: 15px;
                        max-height: 70vh;
                        overflow-y: auto;
                        font-family: 'Consolas', 'Monaco', monospace;
                        font-size: 13px;
                        line-height: 1.6;
                    }
                    .log-entry { padding: 2px 0; border-bottom: 1px solid #2d2d30; }
                    .log-trace { color: #6a9955; }
                    .log-debug { color: #569cd6; }
                    .log-info { color: #d4d4d4; }
                    .log-warn { color: #dcdcaa; }
                    .log-error { color: #f44747; }
                    .log-fatal { color: #f44747; font-weight: bold; }
                    .log-time { color: #808080; }
                    .log-traceid { color: #c586c0; }
                    .auto-scroll { margin-left: auto; }
                    .stats {
                        background: #2d2d30;
                        padding: 10px 15px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                        display: flex;
                        gap: 20px;
                        font-size: 13px;
                    }
                    .stat-item { color: #808080; }
                    .stat-value { color: #4ec9b0; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>🔍 DMP 日志查看器 (Debug Mode)</h1>
                    <span class="status">● 实时连接</span>
                </div>
                
                <div class="stats">
                    <div class="stat-item">总日志: <span class="stat-value" id="total-count">0</span></div>
                    <div class="stat-item">错误: <span class="stat-value" id="error-count">0</span></div>
                    <div class="stat-item">警告: <span class="stat-value" id="warn-count">0</span></div>
                    <div class="stat-item">更新: <span class="stat-value" id="last-update">-</span></div>
                </div>

                <div class="controls">
                    <button id="btn-all" class="active" onclick="filterLogs('all')">全部</button>
                    <button id="btn-error" onclick="filterLogs('error')">错误</button>
                    <button id="btn-warn" onclick="filterLogs('warn')">警告</button>
                    <button id="btn-info" onclick="filterLogs('info')">信息</button>
                    <button id="btn-clear" onclick="clearLogs()">清空</button>
                    <button id="btn-autoscroll" class="auto-scroll active" onclick="toggleAutoScroll()">自动滚动 ✓</button>
                </div>

                <div class="log-container" id="log-container">
                    <div style="color: #808080; text-align: center; padding: 40px;">
                        等待日志加载...
                    </div>
                </div>

                <script>
                    let autoScroll = true;
                    let currentFilter = 'all';
                    let allLogs = [];

                    function filterLogs(level) {
                        currentFilter = level;
                        document.querySelectorAll('.controls button').forEach(btn => btn.classList.remove('active'));
                        document.getElementById('btn-' + level).classList.add('active');
                        renderLogs();
                    }

                    function toggleAutoScroll() {
                        autoScroll = !autoScroll;
                        const btn = document.getElementById('btn-autoscroll');
                        btn.classList.toggle('active');
                        btn.textContent = autoScroll ? '自动滚动 ✓' : '自动滚动';
                    }

                    function clearLogs() {
                        allLogs = [];
                        renderLogs();
                    }

                    function renderLogs() {
                        const container = document.getElementById('log-container');
                        const filteredLogs = currentFilter === 'all' 
                            ? allLogs 
                            : allLogs.filter(log => log.level.toLowerCase().includes(currentFilter));

                        if (filteredLogs.length === 0) {
                            container.innerHTML = '<div style="color: #808080; text-align: center; padding: 40px;">暂无日志</div>';
                            return;
                        }

                        container.innerHTML = filteredLogs.map(log => {
                            const levelClass = 'log-' + log.level.toLowerCase();
                            const traceIdHtml = log.traceId ? '<span class="log-traceid">[' + log.traceId + ']</span> ' : '';
                            return '<div class="log-entry ' + levelClass + '">' +
                                   '<span class="log-time">' + log.time + '</span> ' +
                                   traceIdHtml +
                                   '[' + log.level + '] ' +
                                   log.message +
                                   '</div>';
                        }).join('');

                        if (autoScroll) {
                            container.scrollTop = container.scrollHeight;
                        }

                        // 更新统计
                        document.getElementById('total-count').textContent = allLogs.length;
                        document.getElementById('error-count').textContent = allLogs.filter(l => l.level === 'ERROR' || l.level === 'FATAL').length;
                        document.getElementById('warn-count').textContent = allLogs.filter(l => l.level === 'WARN').length;
                        document.getElementById('last-update').textContent = new Date().toLocaleTimeString();
                    }

                    async function fetchLogs() {
                        try {
                            const response = await fetch('/api/logs/recent?count=100');
                            const data = await response.json();
                            allLogs = data.logs || [];
                            renderLogs();
                        } catch (e) {
                            console.error('获取日志失败:', e);
                        }
                    }

                    // 初始加载
                    fetchLogs();
                    // 每2秒刷新一次
                    setInterval(fetchLogs, 2000);
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    /**
     * 提供所有日志（JSON格式）
     */
    private fun serveLogs(): Response {
        val logs = getRecentLogs(1000)
        val json = logsToJson(logs)
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
    }

    /**
     * 提供最近日志（JSON格式）
     */
    private fun serveRecentLogs(session: IHTTPSession): Response {
        val params = hashMapOf<String, String>()
        session.parseBody(params)
        val queryParams = session.queryParameterString

        val count = try {
            queryParams?.split("count=")?.get(1)?.toIntOrNull() ?: 100
        } catch (e: Exception) {
            100
        }

        val logs = getRecentLogs(count)
        val json = logsToJson(logs)
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
    }

    /**
     * 提供服务器状态
     */
    private fun serveStatus(): Response {
        val status = """
            {
                "status": "running",
                "url": "$serverUrl",
                "mode": "debug",
                "timestamp": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())}"
            }
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", status)
    }

    /**
     * 获取本地IP地址
     */
    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) {
                // WiFi未连接，使用localhost
                "localhost"
            } else {
                @Suppress("DEPRECATION")
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                ip
            }
        } catch (e: Exception) {
            logger.error("获取本地IP失败: ${e.message}", file = "DMPLogServer.kt", function = "getLocalIpAddress", line = 0)
            "localhost"
        }
    }

    companion object {
        private val logBuffer = mutableListOf<DMPLogEntry>()
        private const val MAX_BUFFER_SIZE = 2000

        /**
         * 添加日志到缓冲区
         */
        fun addLog(entry: DMPLogEntry) {
            synchronized(logBuffer) {
                logBuffer.add(entry)
                if (logBuffer.size > MAX_BUFFER_SIZE) {
                    logBuffer.removeAt(0)
                }
            }
        }

        /**
         * 获取最近日志
         */
        fun getRecentLogs(count: Int): List<DMPLogEntry> {
            synchronized(logBuffer) {
                return logBuffer.takeLast(count).toList()
            }
        }

        /**
         * 日志转JSON
         */
        private fun logsToJson(logs: List<DMPLogEntry>): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            val jsonLogs = logs.joinToString(",\n") { entry ->
                """
                {
                    "time": "${dateFormat.format(entry.timestamp)}",
                    "level": "${entry.level.description}",
                    "tag": "${entry.tag}",
                    "traceId": "${entry.traceID ?: ""}",
                    "message": "${entry.message.replace("\"", "\\\"")}",
                    "file": "${entry.file}",
                    "function": "${entry.function}",
                    "line": ${entry.line}
                }
                """.trimIndent()
            }
            return """{"logs": [$jsonLogs], "count": ${logs.size}}"""
        }
    }
}