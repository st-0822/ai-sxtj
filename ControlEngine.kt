package com.ai.sxtj

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ControlEngine(private val context: Context) {

    companion object { private const val TAG = "ControlEngine" }

    /** 锁定指定包名的 App（通过 UsageStats + 前台检测，需用户授予权限） */
    fun lockApp(packageName: String): String {
        // 实际锁定通过 WatchService 检测前台 App 并跳转桌面实现
        val prefs = context.getSharedPreferences("ai_phone", Context.MODE_PRIVATE)
        val locked = prefs.getStringSet("locked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        locked.add(packageName)
        prefs.edit().putStringSet("locked_apps", locked).apply()
        WatchService.updateLockedApps(context, locked)
        return JSONObject().apply { put("success", true); put("locked", packageName) }.toString()
    }

    fun setMute(enabled: Boolean): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.ringerMode = if (enabled) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            } else {
                am.setStreamMute(AudioManager.STREAM_MUSIC, enabled)
            }
            JSONObject().apply { put("success", true); put("muted", enabled) }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }

    fun lockScreen(): String {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
                JSONObject().apply { put("success", true) }.toString()
            } else {
                JSONObject().apply { put("success", false); put("error", "未激活设备管理员") }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }

    fun getLocation(): String {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var best: android.location.Location? = null
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    try {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null && (best == null || loc.accuracy < best!!.accuracy)) best = loc
                    } catch (e: SecurityException) { /* 权限未授予 */ }
                }
            }
            if (best != null) {
                JSONObject().apply {
                    put("success", true)
                    put("latitude", best!!.latitude)
                    put("longitude", best!!.longitude)
                    put("accuracy", best!!.accuracy)
                }.toString()
            } else {
                JSONObject().apply { put("success", false); put("error", "无定位数据，请确认已授权定位权限") }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }

    /** 获取真实使用统计（需 PACKAGE_USAGE_STATS 权限） */
    fun getUsageStats(): String {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return JSONArray().toString()
            }
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - 1000 * 60 * 60 * 24 // 最近 24 小时
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, now)
            val arr = JSONArray()
            for (s in stats) {
                if (s.totalTimeInForeground > 0) {
                    arr.put(JSONObject().apply {
                        put("package", s.packageName)
                        put("minutes", (s.totalTimeInForeground / 60000).toInt())
                        put("lastUsed", s.lastTimeUsed)
                    })
                }
            }
            arr.toString()
        } catch (e: Exception) {
            Log.e(TAG, "getUsageStats", e)
            JSONArray().toString()
        }
    }

    fun getForegroundApp(): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val tasks = am.getRunningAppProcesses()
                val top = tasks?.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                top?.processName ?: ""
            } else ""
        } catch (e: Exception) { "" }
    }
}
