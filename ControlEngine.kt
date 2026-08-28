package com.ai.phone.control

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ai.phone.ai.PersonaEngine
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore

/**
 * 管控引擎：封装所有系统级管控能力。
 *
 * ⚠️ 说明：真实的 App 锁定/使用统计需要 UsageStatsManager + AccessibilityService + DeviceAdmin，
 * 这些在运行时需用户手动授权。本引擎在授权后提供真实能力；未授权时降级为"模拟模式"
 * （数据由 PersonaEngine 按人设生成），保证功能始终可用。
 */
class ControlEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val personaEngine = PersonaEngine(context)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    /** 已锁定的 App 列表 + 解锁时间 */
    private val lockedApps = mutableMapOf<String, Long>()

    // ========== 锁 App ==========
    fun lockApp(packageName: String, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60 * 1000L
        lockedApps[packageName] = until
        // 真实锁定需配合 AccessibilityService 拦截启动；这里记录状态供前端查询 + 通知栏常驻提醒
        showLockNotification(packageName, until)
    }

    fun isAppLocked(packageName: String): Boolean {
        val until = lockedApps[packageName] ?: return false
        if (System.currentTimeMillis() > until) {
            lockedApps.remove(packageName)
            return false
        }
        return true
    }

    // ========== 使用统计（真实，需 USAGE_STATS 权限）==========
    fun getUsageStats(): String {
        return try {
            if (!hasUsageStatsPermission()) {
                // 未授权 → 返回模拟数据（PersonaEngine 生成）
                return personaEngine.generateUsageReport().toString()
            }
            // 真实统计（Android 5.0+）
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24 * 60 * 60 * 1000L
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            val arr = org.json.JSONArray()
            stats.filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(10)
                .forEach { s ->
                    try {
                        val name = context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(s.packageName, 0)
                        ).toString()
                        val min = (s.totalTimeInForeground / 60000).toInt()
                        arr.put(JSONObject().put(name, min))
                    } catch (_: Exception) {}
                }
            JSONObject().put("today", arr).put("real", true).toString()
        } catch (e: Exception) {
            personaEngine.generateUsageReport().toString()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    // ========== 定位（真实，需定位权限）==========
    fun getLocation(): String {
        return try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                return JSONObject().put("location", "未知（未授权定位）").put("real", false).toString()
            }
            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            val loc = locationManager.getLastKnownLocation(provider)
            if (loc != null) {
                JSONObject().put("lat", loc.latitude).put("lng", loc.longitude)
                    .put("location", "(${loc.latitude}, ${loc.longitude})").put("real", true).toString()
            } else {
                JSONObject().put("location", "获取中...").put("real", false).toString()
            }
        } catch (e: Exception) {
            JSONObject().put("location", "定位失败").put("real", false).toString()
        }
    }

    // ========== 拍照（真实，需相机权限）==========
    fun takePhoto(activity: androidx.appcompat.app.AppCompatActivity, callback: (String) -> Unit) {
        // 简化实现：真实拍照需 Camera2 API + SurfaceView，这里用 MediaStore 触发系统相机
        // 完整实现应 startActivityForResult 拍照后回调。此处提供回调框架，照片路径回传前端
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            // 实际需在 Activity 中处理 onActivityResult → callback(path)
            // 这里仅占位，真实集成见 README
            callback("")  // 未配置时不崩，前端走模拟照片
        } catch (e: Exception) {
            callback("")
        }
    }

    // ========== 禁言/勿扰 ==========
    fun setMute(enabled: Boolean, minutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // 勿扰模式（需 NotificationPolicyAccess）
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.notificationPolicy = NotificationPolicy(
                        if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            } catch (_: Exception) {}
        }
        // 静音
        audioManager.ringerMode = if (enabled) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        if (enabled && minutes > 0) {
            // 定时恢复
            Handler(Looper.getMainLooper()).postDelayed({
                setMute(false, 0)
            }, minutes * 60 * 1000L)
        }
    }

    // ========== 锁屏 ==========
    fun lockScreen() {
        // 真实锁屏需 DeviceAdmin 权限
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val compName = ComponentName(context, AdminReceiver::class.java)
            if (devicePolicyManager.isAdminActive(compName)) {
                devicePolicyManager.lockNow()
            }
        } catch (_: Exception) {}
    }

    // ========== 查岗报告（AI 模拟"他"）==========
    fun generateInspectionReport(persona: String, contextHint: String): String {
        return personaEngine.generateInspectionReport(persona, contextHint).apply {
            // 真实定位补充（如有权限）
            if (hasUsageStatsPermission()) {
                // 可在此合并真实使用数据
            }
        }.toString()
    }

    // ========== 通知 ==========
    fun showNotification(title: String, content: String) {
        val channelId = "ai_phone_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AI 小手机", NotificationManager.IMPORTANCE_HIGH)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(Random().nextInt(), notif)
    }

    private fun showLockNotification(packageName: String, until: Long) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        showNotification("🔒 已锁定 $packageName", "解锁时间：${sdf.format(Date(until))}")
    }

    // ========== 震动 ==========
    fun vibrate(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    // ========== 盯防前台服务 ==========
    fun startWatchService() {
        val intent = Intent(context, WatchService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopWatchService() {
        context.stopService(Intent(context, WatchService::class.java))
    }
}
