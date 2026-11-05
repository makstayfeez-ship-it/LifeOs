package com.lifeos.app.assistant

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Обычный разговор с помощником идёт через Anthropic Messages API.
 * Ключ пользователь вводит сам в настройках помощника; он хранится только на телефоне.
 * Для публичного релиза ключ лучше убрать с устройства и ходить через свой сервер.
 */
class AiClient {
    suspend fun ask(
        apiKey: String,
        assistantName: String,
        history: List<Pair<String, String>>,
        userText: String,
    ): String {
        if (apiKey.isBlank()) {
            return "Чтобы я мог просто разговаривать, добавь ключ ИИ в настройках помощника. " +
                "Будильник, таймер, заметки, покупки и расходы работают и без него."
        }
        return withContext(Dispatchers.IO) {
            runCatching { request(apiKey, assistantName, history, userText) }
                .getOrElse { "Не получилось связаться с ИИ. Проверь интернет и ключ в настройках." }
        }
    }

    private fun request(
        apiKey: String,
        assistantName: String,
        history: List<Pair<String, String>>,
        userText: String,
    ): String {
        val messages = JSONArray()
        // API требует, чтобы диалог начинался с реплики пользователя
        history.takeLast(10).dropWhile { it.first != "user" }.forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 400)
            .put(
                "system",
                "Ты — $assistantName, дружелюбный голосовой помощник в приложении LifeOS. " +
                    "Отвечай по-русски, коротко (1–3 предложения), без списков и разметки: ответ будет озвучен.",
            )
            .put("messages", messages)

        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                return when (conn.responseCode) {
                    401, 403 -> "Ключ ИИ не подошёл. Проверь его в настройках помощника."
                    429 -> "Слишком много запросов, попробуй чуть позже."
                    else -> "ИИ сейчас недоступен (код ${conn.responseCode})."
                }
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val content = JSONObject(text).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") sb.append(block.optString("text"))
            }
            return sb.toString().trim().ifBlank { "Не нашёл, что ответить." }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-5-5"
    }
}
