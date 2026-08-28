package com.ai.phone.ai

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

/**
 * 人设引擎：管理角色 + 为查岗/管控生成"符合人设"的模拟数据。
 * 因为"他"是 AI 扮演的，定位/屏幕/照片都是 AI 按人设生成的合理内容。
 */
class PersonaEngine(private val context: Context) {

    companion object {
        private const val PREFS = "ai_phone_persona"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 当前角色人设文本 */
    fun getPersona(): String = prefs.getString("persona", "温柔年上男友，宠溺，会照顾人") ?: ""

    fun setPersona(text: String) = prefs.edit().putString("persona", text).apply()

    /** 当前角色名 */
    fun getRoleName(): String = prefs.getString("roleName", "宝贝") ?: "宝贝"

    fun setRoleName(name: String) = prefs.edit().putString("roleName", name).apply()

    /**
     * 生成查岗报告（AI 模拟"他"当前状态）。
     * 返回 JSON：{photo, location, screen, usage, text}
     */
    fun generateInspectionReport(persona: String, contextHint: String): JSONObject {
        // 基于人设挑选合理的场景
        val scenes = when {
            persona.contains("程序员") || persona.contains("码农") || persona.contains("开发") ->
                listOf("office_coding", "home_pc", "cafe_laptop", "meeting_room")
            persona.contains("学生") || persona.contains("学弟") ->
                listOf("classroom", "library", "dorm", "canteen")
            persona.contains("医生") || persona.contains("护士") ->
                listOf("hospital", "duty_room", "ward")
            else ->
                listOf("home_sofa", "office", "mall", "restaurant", "friend_home")
        }
        val locations = when {
            persona.contains("程序员") -> listOf("公司3楼工位", "家", "星巴克", "会议室")
            persona.contains("学生") -> listOf("教学楼", "图书馆", "宿舍", "食堂")
            else -> listOf("家里", "公司", "商场", "餐厅", "朋友家", "健身房")
        }
        val apps = listOf(
            "微信", "抖音", "B站", "小红书", "淘宝", "王者荣耀", "原神",
            "钉钉", "QQ", "微博", "网易云", "Keep"
        )

        val scene = scenes.random()
        val location = locations.random()
        val currentApp = apps.random()
        val usageMin = Random.nextInt(10, 180)

        // 口头回复（符合人设）
        val text = when {
            persona.contains("霸道") -> "在加班。想你了，等我回去。"
            persona.contains("可爱") || persona.contains("撒娇") -> "在啦在啦~你想我啦？我也好想你！"
            persona.contains("高冷") -> "嗯，在。怎么了。"
            else -> "在呢，刚忙完。你那边怎么样？"
        }

        return JSONObject().apply {
            put("photo", scene)              // 前端用占位图 + 滤镜映射
            put("location", location)
            put("screen", currentApp)
            put("usage", "$usageMin 分钟")
            put("text", text)
            put("time", System.currentTimeMillis())
        }
    }

    /** 生成"他"的今日使用时间排行（模拟数据） */
    fun generateUsageReport(): JSONObject {
        val apps = listOf("微信" to 120, "抖音" to 85, "B站" to 60, "淘宝" to 40, "原神" to 90, "钉钉" to 30)
        val sorted = apps.sortedByDescending { it.second }
        val arr = org.json.JSONArray()
        sorted.forEach { (name, min) -> arr.put(JSONObject().put(name, min)) }
        return JSONObject().put("today", arr)
    }
}
