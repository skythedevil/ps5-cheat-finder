package com.sky.ps5cheatfinder

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiAssistant(private val apiKey: String) {
    private val client = OkHttpClient()
    fun suggestNextStep(history: String): String {
        val prompt = "You help a user find a game value in PS5 memory. History of their searches:\n$history\nSuggest the next value to search for and what to change in-game. Keep it short and simple."
        val body = JSONObject().put("model", "claude-3-5-haiku-latest").put("max_tokens", 300).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://api.anthropic.com/v1/messages").addHeader("x-api-key", apiKey).addHeader("anthropic-version", "2023-06-01").post(body).build()
        return try { client.newCall(request).execute().use { resp -> val json = JSONObject(resp.body?.string() ?: ""); if (!resp.isSuccessful) "AI request failed: HTTP ${resp.code}" else json.getJSONArray("content").getJSONObject(0).getString("text") } } catch (e: Exception) { "AI request failed: ${e.message}" }
    }
}
