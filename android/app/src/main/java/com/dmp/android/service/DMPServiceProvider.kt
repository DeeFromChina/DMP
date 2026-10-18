package com.dmp.android.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.dmp.android.server.DMPServerHTTP

/**
 * DMP 服务配置 ContentProvider
 * 供第三方应用查询 DMP 服务地址和配置信息
 * 
 * URI:
 * - content://com.dmp.android.service/config - 查询服务配置
 * - content://com.dmp.android.service/status - 查询服务状态
 */
class DMPServiceProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.dmp.android.service"
        const val PATH_CONFIG = "config"
        const val PATH_STATUS = "status"
        
        val URI_CONFIG = Uri.parse("content://$AUTHORITY/$PATH_CONFIG")
        val URI_STATUS = Uri.parse("content://$AUTHORITY/$PATH_STATUS")
        
        // 静态引用，用于从 DMPServerHTTP 更新实例
        @Volatile
        var serverInstance: DMPServerHTTP? = null
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uri.path) {
            "/$PATH_CONFIG" -> queryConfig()
            "/$PATH_STATUS" -> queryStatus()
            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uri.path) {
            "/$PATH_CONFIG" -> "vnd.android.cursor.item/vnd.dmp.config"
            "/$PATH_STATUS" -> "vnd.android.cursor.item/vnd.dmp.status"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    /**
     * 查询服务配置
     */
    private fun queryConfig(): Cursor {
        val server = serverInstance
        val httpPort = server?.getActualPort() ?: 28473
        val serverIp = "127.0.0.1"
        val isRunning = server?.isServerRunning() == true

        return MatrixCursor(arrayOf(
            "http_port",
            "http_ip",
            "is_running",
            "aidl_service_name",
            "version",
            "endpoints",
            "capabilities"
        )).apply {
            addRow(arrayOf(
                httpPort,
                serverIp,
                if (isRunning) 1 else 0,
                "com.dmp.android.service.IDMPServer",
                "1.0.0",
                "/v1/chat/completions,/v1/chat/completions/stream,/v1/responses,/v1/responses/stream,/health",
                "chat,stream"
            ))
        }
    }

    /**
     * 查询服务状态
     */
    private fun queryStatus(): Cursor {
        val server = serverInstance
        
        return MatrixCursor(arrayOf(
            "is_running",
            "http_port",
            "http_ip"
        )).apply {
            addRow(arrayOf(
                if (server?.isServerRunning() == true) 1 else 0,
                server?.getActualPort() ?: 0,
                server?.getServerIp() ?: "127.0.0.1"
            ))
        }
    }
}