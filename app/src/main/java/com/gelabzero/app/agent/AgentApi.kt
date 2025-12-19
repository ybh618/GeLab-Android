package com.gelabzero.app.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AgentApi(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun chat(config: AgentConfig, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val url = buildUrl(config.apiBase)
        val payload = JSONObject().apply {
            put("model", config.model.ifBlank { AgentConfig.DEFAULT_MODEL })
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("API error ${response.code}: $body")
            }
            return@withContext parseResponse(body)
        }
    }

    private fun parseResponse(body: String): String {
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices") ?: throw IOException("Missing choices in response")
        if (choices.length() == 0) {
            throw IOException("Empty choices in response")
        }
        val first = choices.optJSONObject(0) ?: throw IOException("Invalid choice item")
        val messageContent = first.optJSONObject("message")?.optString("content").orEmpty()
        if (messageContent.isNotBlank()) {
            return messageContent
        }
        val textContent = first.optString("text").orEmpty()
        if (textContent.isNotBlank()) {
            return textContent
        }
        val deltaContent = first.optJSONObject("delta")?.optString("content").orEmpty()
        if (deltaContent.isNotBlank()) {
            return deltaContent
        }
        throw IOException("No content in response")
    }

    private fun buildUrl(apiBase: String): String {
        val trimmed = apiBase.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
