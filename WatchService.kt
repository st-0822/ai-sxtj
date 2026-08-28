package com.ai.phone.control

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

/**
 * 盯防服务：前台常驻，检测异常行为（关权限/卸载/失联）并通知。
 * 真实实现需配合 AccessibilityService + DeviceAdmin + 开机自启。
 */
class WatchService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var watchActive = false

    private val checkTask = object : TimerTask() {
        override fun run() {
            if (!watchActive) return
            // 此处可检测：权限是否被撤销、定位是否关闭、App 是否被卸载意图
            // 检测到异常 → controlEngine.showNotification(...)
            Log.d("WatchService", "盯防中...")
        }
    }
    private val timer = Timer()

    override fun onCreate() {
        super.onCreate()
        startForeground(1001, buildNotification())
        watchActive = true
        timer.schedule(checkTask, 0, 30 * 1000L) // 每 30 秒检查
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        watchActive = false
        timer.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "watch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "盯防服务", android.app.NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("👀 盯防中")
            .setContentText("AI 小手机正在保护你的设置")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setOngoing(true)
            .build()
    }
}
