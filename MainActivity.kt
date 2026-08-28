package com.ai.phone

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ai.phone.ai.ApiClient
import com.ai.phone.control.ControlEngine
import com.ai.phone.call.VoiceEngine
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var apiClient: ApiClient
    private lateinit var controlEngine: ControlEngine
    private lateinit var voiceEngine: VoiceEngine

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.entries.all { it.value }
        webView.evaluateJavascript("window.onPermissionsResult && window.onPermissionsResult($granted);", null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }
        }
        setContentView(webView)

        apiClient = ApiClient(this)
        controlEngine = ControlEngine(this)
        voiceEngine = VoiceEngine(this)

        setupJsBridge()

        // 加载前端（assets 里的 index.html）
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun setupJsBridge() {
        // JS → Native 桥接对象，挂载到 window.Android
        webView.addJavascriptInterface(object {

            // ===== API 调用（OpenAI 兼容）=====
            @JavascriptInterface
            fun callAI(configJson: String, messagesJson: String): String {
                return try {
                    apiClient.call(configJson, messagesJson)
                } catch (e: Exception) {
                    JSONObject().apply {
                        put("error", e.message)
                        put("fallback", true)
                    }.toString()
                }
            }

            // ===== 权限请求 =====
            @JavascriptInterface
            fun requestPermissions(permsJson: String) {
                val perms = JSONObject(permsJson)
                val list = mutableListOf<String>()
                if (perms.optBoolean("mic")) list.add(Manifest.permission.RECORD_AUDIO)
                if (perms.optBoolean("location")) {
                    list.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (perms.optBoolean("camera")) list.add(Manifest.permission.CAMERA)
                if (perms.optBoolean("storage")) {
                    list.add(Manifest.permission.READ_MEDIA_IMAGES)
                    list.add(Manifest.permission.READ_MEDIA_AUDIO)
                }
                if (perms.optBoolean("notifications")) list.add(Manifest.permission.POST_NOTIFICATIONS)
                runOnUiThread { permissionLauncher.launch(list.toTypedArray()) }
            }

            // ===== 管控：锁 App =====
            @JavascriptInterface
            fun lockApp(packageName: String, minutes: Int) {
                controlEngine.lockApp(packageName, minutes)
            }

            @JavascriptInterface
            fun isAppLocked(packageName: String): Boolean {
                return controlEngine.isAppLocked(packageName)
            }

            // ===== 管控：使用统计 =====
            @JavascriptInterface
            fun getUsageStats(): String {
                return controlEngine.getUsageStats()
            }

            // ===== 定位 =====
            @JavascriptInterface
            fun getLocation(): String {
                return controlEngine.getLocation()
            }

            // ===== 拍照（查岗）=====
            @JavascriptInterface
            fun takePhoto(callback: String) {
                runOnUiThread { controlEngine.takePhoto(this@MainActivity) { path -> 
                    webView.evaluateJavascript("$callback('$path');", null)
                } }
            }

            // ===== 禁言/勿扰 =====
            @JavascriptInterface
            fun setMute(enabled: Boolean, minutes: Int) {
                controlEngine.setMute(enabled, minutes)
            }

            // ===== 锁屏 =====
            @JavascriptInterface
            fun lockScreen() {
                controlEngine.lockScreen()
            }

            // ===== 语音合成（TTS）=====
            @JavascriptInterface
            fun speak(text: String, voice: String) {
                runOnUiThread { voiceEngine.speak(text, voice) }
            }

            @JavascriptInterface
            fun stopSpeak() {
                runOnUiThread { voiceEngine.stop() }
            }

            // ===== 音频播放（音乐/语音条）=====
            @JavascriptInterface
            fun playAudio(base64OrUrl: String) {
                voiceEngine.playAudio(base64OrUrl)
            }

            @JavascriptInterface
            fun stopAudio() {
                voiceEngine.stopAudio()
            }

            // ===== 查岗：生成查岗报告（AI 模拟"他"的数据）=====
            @JavascriptInterface
            fun generateInspectionReport(persona: String, context: String): String {
                return controlEngine.generateInspectionReport(persona, context)
            }

            // ===== 通知（失踪催/查岗提醒）=====
            @JavascriptInterface
            fun showNotification(title: String, content: String) {
                controlEngine.showNotification(title, content)
            }

            // ===== 震动 =====
            @JavascriptInterface
            fun vibrate(ms: Long) {
                controlEngine.vibrate(ms)
            }

            // ===== 设置项持久化（原生 SharedPreferences）=====
            @JavascriptInterface
            fun getSetting(key: String): String {
                val prefs = getSharedPreferences("ai_phone", MODE_PRIVATE)
                return prefs.getString(key, "") ?: ""
            }

            @JavascriptInterface
            fun setSetting(key: String, value: String) {
                getSharedPreferences("ai_phone", MODE_PRIVATE)
                    .edit().putString(key, value).apply()
            }

            @JavascriptInterface
            fun toast(msg: String) {
                runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
            }

            // ===== 盯防：启动/停止前台服务 =====
            @JavascriptInterface
            fun startWatch() {
                runOnUiThread { controlEngine.startWatchService() }
            }

            @JavascriptInterface
            fun stopWatch() {
                runOnUiThread { controlEngine.stopWatchService() }
            }

            // ===== 定时查岗：AlarmManager 定时触发 =====
            @JavascriptInterface
            fun setAlarm(hour: Int, minute: Int, label: String) {
                runOnUiThread {
                    try {
                        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        val intent = Intent(this@MainActivity, WatchService::class.java).apply {
                            action = "INSPECTION_ALARM"
                        }
                        val pi = PendingIntent.getService(
                            this@MainActivity, 1002, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, hour)
                            set(java.util.Calendar.MINUTE, minute)
                            set(java.util.Calendar.SECOND, 0)
                            if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                        alarmMgr.setRepeating(
                            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                            AlarmManager.INTERVAL_DAY, pi
                        )
                        Toast.makeText(this@MainActivity, "定时查岗已设置: $hour:$minute", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        }, "Android")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
