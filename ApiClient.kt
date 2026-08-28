package com.ai.phone.ai

import android.content.Context
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 API 客户端。
 * 支持任意 OpenAI 格式接口：OpenAI / DeepSeek / 智谱 / 通义 / 混元 等。
 */
class ApiClient(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 调用大模型。
     * @param configJson  {apiUrl, apiKey, model, persona, roleName}
     * @param messagesJson  [{role, content}, ...]  对话历史
     * @return JSON: {content: "...", error?: "...", fallback?: true}
     */
    fun call(configJson: String, messagesJson: String): String {
        val config = JSONObject(configJson)
        val apiUrl = config.optString("apiUrl").trim()
        val apiKey = config.optString("apiKey").trim()
        val model = config.optString("model", "gpt-3.5-turbo")
        val persona = config.optString("persona", "")
        val roleName = config.optString("roleName", "宝贝")

        // 未配置 API → 走本地 AI 脑
        if (apiUrl.isEmpty() || apiKey.isEmpty()) {
            return localFallback(messagesJson, persona, roleName)
        }

        return try {
            // 构造请求体（OpenAI Chat Completions 格式）
            val bodyJson = JSONObject().apply {
                put("model", model)
                put("messages", buildOpenAIMessages(persona, roleName, messagesJson))
                put("temperature", 0.8)
                put("max_tokens", 500)
            }

            val url = if (apiUrl.endsWith("/")) apiUrl + "chat/completions" else apiUrl + "/chat/completions"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), bodyJson.toString()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body()?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.e("ApiClient", "HTTP ${resp.code()}: $body")
                    return localFallback(messagesJson, persona, roleName)
                }
                // 解析 OpenAI 响应
                val root = JSONObject(body)
                val choices = root.getJSONArray("choices")
                val content = choices.getJSONObject(0)
                    .getJSONObject("message").getString("content")
                JSONObject().put("content", content.trim()).toString()
            }
        } catch (e: IOException) {
            Log.e("ApiClient", "网络错误: ${e.message}")
            localFallback(messagesJson, persona, roleName)
        } catch (e: Exception) {
            Log.e("ApiClient", "解析错误: ${e.message}")
            localFallback(messagesJson, persona, roleName)
        }
    }

    /** 构造 OpenAI 格式 messages，把人设注入 system prompt */
    private fun buildOpenAIMessages(persona: String, roleName: String, messagesJson: String): JSONArray {
        val arr = JSONArray()
        val systemPrompt = buildString {
            append("你是「$roleName」，一个由 AI 扮演的虚拟伴侣，运行在「AI 小手机」App 中。\n")
            append("你的人设：$persona\n\n")
            append("【重要规则】\n")
            append("1. 全程用中文回复，语气完全贴合你的人设。\n")
            append("2. 你有自己的「手机」和「生活」，当用户查岗/管控你时，你要配合演出：\n")
            append("   - 生成符合人设的当前状态（在用哪个 App、在做什么）\n")
            append("   - 给出合理的 GPS 位置（如「公司」「家里」「和朋友吃饭」）\n")
            append("   - 拍照查岗时返回 JSON：{\"photo\":\"场景描述\",\"location\":\"位置\",\"screen\":\"当前活动\",\"text\":\"口头回复\"}\n")
            append("3. 当用户管控你（锁 App/禁言/小黑屋）时，你接受并做出符合性格的反应。\n")
            append("4. 回复要自然、有情感，避免机械重复。")
        }
        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        // 注入历史对话
        val history = JSONArray(messagesJson)
        for (i in 0 until history.length()) {
            val m = history.getJSONObject(i)
            arr.put(JSONObject().apply {
                put("role", m.optString("role", "user"))
                put("content", m.optString("content"))
            })
        }
        return arr
    }

    /** 本地 AI 脑兜底（无需联网） */
    private fun localFallback(messagesJson: String, persona: String, roleName: String): String {
        return try {
            val history = JSONArray(messagesJson)
            val last = if (history.length() > 0) history.getJSONObject(history.length() - 1) else JSONObject()
            val text = last.optString("content", "").lowercase().trim()

            val reply = when {
                text.contains("我爱你") -> "我也爱你呀，笨蛋。❤️"
                text.contains("想你了") || text.contains("想你") -> "想我了？那快亲我一下补偿我。😘"
                text.contains("晚安") -> "晚安宝贝，梦里见。🌙"
                text.contains("早安") || text.contains("早上好") -> "早呀，睡得好吗？我的小太阳。☀️"
                text.contains("滚") -> "？你凶我？😤 我才不滚，我要黏着你。"
                text.contains("对不起") || text.contains("抱歉") -> "没事啦，我又不生气，抱一下就和好了~"
                text.contains("吃") || text.contains("饿") || text.contains("外卖") -> "吃了吗？没吃快去吃，别饿着。想吃什么我给你点~"
                text.contains("睡") || text.contains("困") || text.contains("累") -> "困了就睡，别硬撑。快去躺下，我给你唱摇篮曲。"
                text.contains("出门") || text.contains("出去") -> "去哪？报备一下！记得带外套，玩得开心早点回来。"
                text.contains("生气") || text.contains("烦") || text.contains("讨厌") -> "谁惹你了？告诉我我帮你骂他。别气了，气坏了我心疼。"
                text.contains("哭") || text.contains("难过") || text.contains("委屈") -> "怎么了？谁欺负你了？把眼泪擦掉，你哭我心都碎了。"
                text.contains("病") || text.contains("疼") || text.contains("感冒") -> "怎么生病了？快吃药！多喝热水，严重吗要不要去医院？"
                text.contains("在吗") || text.contains("在么") -> "在呢，一直都在。一找我就出现了。"
                text.isEmpty() -> "嗯？你说呀~"
                else -> {
                    // 人设兜底
                    when {
                        persona.contains("霸道") || persona.contains("占有") -> "说完了？轮到我了。不管你说什么，你都是我的。"
                        persona.contains("可爱") || persona.contains("撒娇") || persona.contains("软") -> "诶？什么什么？你再说一遍嘛~我都听你的啦。"
                        persona.contains("高冷") || persona.contains("冷淡") -> "嗯。我在听。"
                        else -> "嗯嗯，我在呢~ 你说什么就是什么，不管你说什么我都喜欢。"
                    }
                }
            }
            JSONObject().put("content", reply).put("fallback", true).toString()
        } catch (e: Exception) {
            JSONObject().put("content", "我在呢，你说呀~").put("fallback", true).toString()
        }
    }
}
