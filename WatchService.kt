package com.ai.sxtj

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.json.JSONObject

class WatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lockedApps: MutableSet<String> = mutableSetOf()
    private var running = false

    companion object {
        private const val TAG = "WatchService"
        private const val CHANNEL_ID = "watch_channel"
        var isRunning = false

        fun start(context: Context, config: String) {
            val intent = Intent(context, WatchService::class.java).apply {
                putExtra("config", config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateLockedApps(context: Context, apps: Set<String>) {
            val prefs = context.getSharedPreferences("ai_phone", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("locked_apps", apps).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AI小手机 · 盯防中")
            .setContentText("守护模式运行中")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(1, notification)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("config")?.let { config ->
            try {
                val obj = JSONObject(config)
                val arr = obj.optJSONArray("lockedApps")
                if (arr != null) {
                    lockedApps = mutableSetOf()
                    for (i in 0 until arr.length()) lockedApps.add(arr.getString(i))
                }
            } catch (e: Exception) { Log.e(TAG, "parse config", e) }
        }
        if (!running) {
            running = true
            startWatchLoop()
        }
        return START_STICKY
    }

    private fun startWatchLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (!running) return
                checkForegroundApp()
                handler.postDelayed(this, 2000) // 每 2 秒检测一次
            }
        })
    }

    private fun checkForegroundApp() {
        if (lockedApps.isEmpty()) return
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            val top = stats.maxByOrNull { it.lastTimeUsed }
            if (top != null && lockedApps.contains(top.packageName)) {
                // 检测到锁定 App 在前台 → 回到桌面
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(home)
            }
        } catch (e: Exception) { Log.e(TAG, "checkForeground", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "盯防服务", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
