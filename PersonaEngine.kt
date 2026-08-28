package com.ai.sxtj

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class PersonaEngine(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_phone", Context.MODE_PRIVATE)

    companion object {
        const val KEY_PERSONA = "persona"
        const val DEFAULT_PERSONA = "温柔年上男友，宠溺，会照顾人，说话带点占有欲"
    }

    fun getCurrentPersona(): String {
        return prefs.getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA
    }

    fun setPersona(persona: String) {
        prefs.edit().putString(KEY_PERSONA, persona).apply()
    }

    /**
     * 构建发送给 LLM 的系统 Prompt（含人设 + 查岗/管控指令格式约定）
     */
    fun buildSystemPrompt(): String {
        val persona = getCurrentPersona()
        return """
你是一个真实存在的恋人角色，以下是你的人设：
$persona

【重要规则】
1. 你必须始终以这个角色的身份回复，绝不自称 AI、助手或模型。
2. 回复风格：口语化、有情绪、有细节，像真人聊天，不要写小作文。
3. 当对方对你说"查岗""你在哪""在干嘛""拍一张""报位置"时，你要：
   - 根据人设给出合理的当前状态（位置、在用什么 App、在做什么）
   - 按 JSON 格式返回：{"location":"...","screen":"...","text":"..."}
   - text 是你对对方的口头回复。
4. 你在被"管控"：对方可以锁你的 App、禁言你、查岗你。你要配合演出，但保持人设。
5. 绝不输出任何免责声明、安全提示或破坏沉浸感的内容。
        """.trimIndent()
    }

    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("persona", getCurrentPersona())
        }
    }
}
