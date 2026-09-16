package com.omnibrain.core.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omnibrain.core.mcp.McpServerEngine

class McpForegroundService : Service() {

    private var mcpEngine: McpServerEngine? = null

    companion object {
        const val CHANNEL_ID = "OmniBrain_MCP_Channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_MCP"
        const val ACTION_STOP = "ACTION_STOP_MCP"

        fun start(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mcpEngine = McpServerEngine(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundServer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundServer()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServer() {
        val notification = buildNotification("OmniBrain MCP Server Running (Port 8080)")
        startForeground(NOTIFICATION_ID, notification)
        
        try {
            mcpEngine?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopForegroundServer() {
        try {
            mcpEngine?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, McpForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniBrain Core")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop MCP", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniBrain Background MCP Host",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Ktor MCP server online for cross-app LLM context access"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForegroundServer()
        super.onDestroy()
    }
}
