package com.ai.sxtj

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ApiClient {

    private val client = OkHttpClient()
    private const val TAG = "ApiClient"

    fun getConfig(context: Context): SharedPreferences =
        context.getSharedPreferences("ai_phone", Context.MODE_PRIVATE)

    fun callOpenAI(context: Context, requestJson: String): String {
        return try {
            val prefs = getConfig(context)
            val apiUrl = prefs.getString("apiUrl", "") ?: ""
            val apiKey = prefs.getString("apiKey", "") ?: ""
            val model = prefs.getString("model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"

            if (apiUrl.isEmpty() || apiKey.isEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "未配置 API 地址或 Key")
                }.toString()
            }

            val reqObj = JSONObject(requestJson)
            val messages = reqObj.optJSONArray("messages") ?: JSONArray()

            // 如果请求里没带 model，用本地配置的
            if (!reqObj.has("model")) reqObj.put("model", model)

            val body = JSONObject().apply {
                put("model", reqObj.optString("model", model))
                put("messages", messages)
                put("temperature", reqObj.optDouble("temperature", 0.7))
                put("max_tokens", reqObj.optInt("max_tokens", 800))
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return JSONObject().apply {
                        put("success", false)
                        put("error", "HTTP ${response.code}: $respBody")
                    }.toString()
                }
                // 解析 OpenAI 格式返回
                val json = JSONObject(respBody)
                val choices = json.optJSONArray("choices")
                val content = if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                } else ""
                JSONObject().apply {
                    put("success", true)
                    put("content", content)
                }.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "callOpenAI error", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "unknown")
            }.toString()
        }
    }
}
