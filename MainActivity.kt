package com.ai.sxtj

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var personaEngine: PersonaEngine
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var controlEngine: ControlEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWebView()

        personaEngine = PersonaEngine(this)
        voiceEngine = VoiceEngine(this)
        controlEngine = ControlEngine(this)

        loadFrontend()
    }

    private fun setupWebView() {
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(NativeBridge(), "AndroidBridge")
        }
        setContentView(webView)
    }

    private fun loadFrontend() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class NativeBridge {

        @JavascriptInterface
        fun getApiConfig(): String {
            return try {
                val prefs = getSharedPreferences("ai_phone", Context.MODE_PRIVATE)
                JSONObject().apply {
                    put("apiUrl", prefs.getString("apiUrl", "") ?: "")
                    put("apiKey", prefs.getString("apiKey", "") ?: "")
                    put("model", prefs.getString("model", "gpt-3.5-turbo") ?: "")
                }.toString()
            } catch (e: Exception) { "{}" }
        }

        @JavascriptInterface
        fun saveApiConfig(json: String) {
            try {
                val obj = JSONObject(json)
                val prefs = getSharedPreferences("ai_phone", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("apiUrl", obj.optString("apiUrl"))
                    putString("apiKey", obj.optString("apiKey"))
                    putString("model", obj.optString("model"))
                    apply()
                }
            } catch (e: Exception) { Log.e("NativeBridge", "saveApiConfig", e) }
        }

        @JavascriptInterface
        fun callOpenAI(json: String): String {
            return ApiClient.callOpenAI(this@MainActivity, json)
        }

        @JavascriptInterface
        fun getPersona(): String = personaEngine.getCurrentPersona()

        @JavascriptInterface
        fun setPersona(persona: String) = personaEngine.setPersona(persona)

        @JavascriptInterface
        fun speak(text: String, voice: String) = voiceEngine.speak(text, voice)

        @JavascriptInterface
        fun stopSpeaking() = voiceEngine.stop()

        @JavascriptInterface
        fun getVoices(): String = voiceEngine.getVoicesJson()

        @JavascriptInterface
        fun lockApp(packageName: String): String = controlEngine.lockApp(packageName)

        @JavascriptInterface
        fun setMute(enabled: Boolean): String = controlEngine.setMute(enabled)

        @JavascriptInterface
        fun lockScreen(): String = controlEngine.lockScreen()

        @JavascriptInterface
        fun getLocation(): String = controlEngine.getLocation()

        @JavascriptInterface
        fun getUsageStats(): String = controlEngine.getUsageStats()

        @JavascriptInterface
        fun startWatchService(config: String) = WatchService.start(this@MainActivity, config)

        @JavascriptInterface
        fun requestPermission(permission: String): String {
            return when (permission) {
                "usage" -> {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    "opened_settings"
                }
                "location" -> {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    "opened_settings"
                }
                "overlay" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    "opened_settings"
                }
                "admin" -> {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        ComponentName(this@MainActivity, AdminReceiver::class.java))
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "用于远程锁屏管控")
                    startActivity(intent)
                    "opened_settings"
                }
                else -> "unknown_permission"
            }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
