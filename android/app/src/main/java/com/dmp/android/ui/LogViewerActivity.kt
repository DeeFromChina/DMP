package com.dmp.android.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dmp.android.BuildConfig
import com.dmp.android.R
import com.dmp.android.core.DMPLogServer

/**
 * 日志查看Activity（仅Debug模式可用）
 * 
 * 功能：
 * 1. 显示二维码供电脑扫码查看日志
 * 2. 显示服务器URL
 * 3. Release模式自动隐藏此功能
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var qrCodeImage: ImageView
    private lateinit var urlText: TextView
    private lateinit var statusText: TextView
    private var logServer: DMPLogServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        // 检查是否为Debug模式
        if (!BuildConfig.DEBUG) {
            Toast.makeText(this, "Release模式，日志查看功能已禁用", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        qrCodeImage = findViewById(R.id.qr_code_image)
        urlText = findViewById(R.id.url_text)
        statusText = findViewById(R.id.status_text)

        setupLogServer()
    }

    private fun setupLogServer() {
        try {
            // 启动日志HTTP服务器
            logServer = DMPLogServer(this)
            val serverUrl = logServer?.startServer()

            if (serverUrl.isNullOrEmpty()) {
                statusText.text = "❌ 服务器启动失败"
                Toast.makeText(this, "日志服务器启动失败", Toast.LENGTH_LONG).show()
                return
            }

            // 显示URL
            urlText.text = serverUrl

            // 生成二维码
            val qrBitmap = logServer?.generateQRCode()
            if (qrBitmap != null) {
                qrCodeImage.setImageBitmap(qrBitmap)
                statusText.text = "✅ 服务器运行中\n\n电脑和手机在同一WiFi下\n扫码即可查看日志"
            } else {
                statusText.text = "⚠️ 二维码生成失败"
            }

        } catch (e: Exception) {
            statusText.text = "❌ 错误: ${e.message}"
            Toast.makeText(this, "日志服务器异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logServer?.stopServer()
    }
}