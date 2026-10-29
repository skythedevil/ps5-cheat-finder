package com.sky.ps5cheatfinder

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The AI services the user can choose from. */
enum class AiProviderType(val label: String, val defaultModel: String) {
    GEMINI("Gemini", "gemini-2.5-flash"),
    ANTHROPIC("Claude", "claude-haiku-5-5"),
    OPENROUTER("OpenRouter", "openrouter/auto"),
    OPENAI_COMPATIBLE("OpenAI / Groq / custom", "gpt-4o-mini")
}

/** Everything needed to call one AI service. Keys are passed in memory and never saved by this class. */
data class AiSettings(
    val type: AiProviderType,
    val apiKey: String,
    val model: String,
    val baseUrl: String = ""
)

/** Sends a question to the chosen AI service and returns its text reply. */
class AiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** imageBase64 is optional JPEG data encoded as base64 text. */
    fun ask(settings: AiSettings, prompt: String, imageBase64: String? = null): String {
        return try {
            when (settings.type) {
                AiProviderType.GEMINI -> askGemini(settings, prompt, imageBase64)
                AiProviderType.ANTHROPIC -> askAnthropic(settings, prompt, imageBase64)
                AiProviderType.OPENROUTER, AiProviderType.OPENAI_COMPATIBLE -> askOpenAi(settings, prompt, imageBase64)
            }
        } catch (e: Exception) {
            "AI error: ${e.message}"
        }
    }

    /** Send JSON to the service. Never include the API key in app logs. */
    private fun post(url: String, headers: Map<String, String>, json: JSONObject): String {
        val builder = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${text.take(300)}")
            return text
        }
    }

    /** Google Gemini uses the x-goog-api-key header. */
    private fun askGemini(s: AiSettings, prompt: String, image: String?): String {
        val parts = JSONArray()
        if (image != null) {
            parts.put(JSONObject().put("inline_data",
                JSONObject().put("mime_type", "image/jpeg").put("data", image)))
        }
        parts.put(JSONObject().put("text", prompt))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${s.model}:generateContent"
        val raw = post(url, mapOf("x-goog-api-key" to s.apiKey), body)
        val partsOut = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        return (0 until partsOut.length()).mapNotNull { i ->
            partsOut.getJSONObject(i).optString("text").takeIf { it.isNotBlank() }
        }.joinToString("\n").ifBlank { "The AI returned no text. Check the selected model and provider response." }
    }

    /** Anthropic Claude uses the x-api-key header. */
    private fun askAnthropic(s: AiSettings, prompt: String, image: String?): String {
        val content = JSONArray()
        if (image != null) {
            content.put(JSONObject().put("type", "image").put("source",
                JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", image)))
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", s.model)
            .put("max_tokens", 600)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val headers = mapOf("x-api-key" to s.apiKey, "anthropic-version" to "2023-06-01")
        val raw = post("https://api.anthropic.com/v1/messages", headers, body)
        val parts = JSONObject(raw).getJSONArray("content")
        return (0 until parts.length()).mapNotNull { i ->
            parts.getJSONObject(i).optString("text").takeIf { it.isNotBlank() }
        }.joinToString("\n")
    }

    /**
     * Checks an OpenRouter key without spending tokens by calling the key-status endpoint.
     * The key is passed only in the request header and is never logged or persisted.
     */
    fun testOpenRouterKey(apiKey: String): String {
        require(apiKey.isNotBlank()) { "Enter an OpenRouter API key first." }
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/key")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try {
                    JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                } catch (_: Exception) { "" }
                throw IllegalStateException(
                    "OpenRouter key test failed (HTTP ${response.code})" +
                        if (detail.isNotBlank()) ": ${detail.take(180)}" else "."
                )
            }
            val data = try { JSONObject(body).optJSONObject("data") } catch (_: Exception) { null }
            val remaining = data?.opt("limit_remaining")?.toString()
                ?.takeIf { it.isNotBlank() && it != "null" }
            return if (remaining != null) {
                "OpenRouter key is valid. Remaining key limit: $remaining."
            } else {
                "OpenRouter accepted the key. Authentication is working."
            }
        }
    }

    /** OpenAI-compatible endpoint: OpenAI, Groq, and OpenRouter. */
    private fun askOpenAi(s: AiSettings, prompt: String, image: String?): String {
        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", prompt))
        if (image != null) {
            content.put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$image")))
        }
        val body = JSONObject()
            .put("model", s.model)
            .put("max_tokens", 600)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val base = when (s.type) {
            AiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
            else -> s.baseUrl.trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        }
        val headers = mutableMapOf("Authorization" to "Bearer ${s.apiKey}")
        if (s.type == AiProviderType.OPENROUTER) {
            headers["HTTP-Referer"] = "https://github.com/skythedevil/ps5-cheat-finder"
            headers["X-OpenRouter-Title"] = "PS5 Cheat Finder"
        }
        val raw = post("$base/chat/completions", headers, body)
        return JSONObject(raw).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }
}
