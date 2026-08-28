package com.ai.sxtj

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class VoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = (status == TextToSpeech.SUCCESS)
            if (ready) {
                tts?.language = Locale.CHINESE
            }
        }
    }

    /**
     * 声线映射：前端传 "gentle_male" 等 → 调整语速/音调
     */
    fun speak(text: String, voice: String) {
        if (!ready) return
        val params = when (voice) {
            "cute_female" -> Pair(1.15f, 1.4f)
            "loli" -> Pair(1.25f, 1.7f)
            "royal_sister" -> Pair(0.95f, 1.15f)
            "cool_male" -> Pair(0.85f, 0.7f)
            "gentle_male" -> Pair(0.95f, 1.0f)
            else -> Pair(1.0f, 1.0f)
        }
        tts?.setSpeechRate(params.first)
        tts?.setPitch(params.second)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun stop() {
        tts?.stop()
    }

    fun getVoicesJson(): String {
        if (!ready || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return JSONArray().toString()
        }
        val voices = tts?.voices ?: return JSONArray().toString()
        val arr = JSONArray()
        voices.filter { it.locale.language == "zh" || it.name.contains("zh", true) }
            .take(10)
            .forEach { v: Voice ->
                arr.put(JSONObject().apply {
                    put("name", v.name)
                    put("locale", v.locale.toString())
                })
            }
        return arr.toString()
    }
}
