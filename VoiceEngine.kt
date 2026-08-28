package com.ai.phone.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 语音引擎：
 * - speak()：TTS 语音合成（AI 回复念出来），voice 参数映射声线
 * - playAudio()：播放 base64 或 URL 音频（音乐/语音条录音回放）
 */
class VoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var initialized = false

    init {
        tts = TextToSpeech(context) { status ->
            initialized = (status == TextToSpeech.SUCCESS)
            if (initialized) {
                // 默认中文
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            }
        }
    }

    /** 声线 → TTS 参数映射 */
    private fun applyVoice(voice: String) {
        if (!initialized) return
        when (voice) {
            "cute_female", "loli" -> {
                tts?.setSpeechRate(1.15f)
                tts?.setPitch(1.5f)
            }
            "royal_sister" -> {
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.15f)
            }
            "cool_male" -> {
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(0.7f)
            }
            "gentle_male" -> {
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
            }
            else -> {
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
            }
        }
    }

    fun speak(text: String, voice: String = "gentle_male") {
        if (!initialized) return
        applyVoice(voice)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai_reply")
    }

    fun stop() {
        tts?.stop()
    }

    /**
     * 播放音频：支持 http(s) URL 或 data:audio/xxx;base64,xxx 格式。
     * 用于：音乐播放、语音条录音回放。
     */
    fun playAudio(base64OrUrl: String) {
        try {
            stopAudio()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (base64OrUrl.startsWith("data:audio")) {
                    // base64 → 临时文件
                    val tmpFile = decodeBase64ToFile(base64OrUrl)
                    if (tmpFile != null) setDataSource(tmpFile.absolutePath)
                    else return
                } else {
                    setDataSource(base64OrUrl)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceEngine", "播放失败: ${e.message}")
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun decodeBase64ToFile(dataUri: String): File? = try {
        val comma = dataUri.indexOf(",")
        val b64 = dataUri.substring(comma + 1)
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val tmp = File(context.cacheDir, "audio_${System.currentTimeMillis()}.tmp")
        FileOutputStream(tmp).use { it.write(bytes) }
        tmp
    } catch (e: Exception) { null }

    fun destroy() {
        tts?.shutdown()
        stopAudio()
    }
}
