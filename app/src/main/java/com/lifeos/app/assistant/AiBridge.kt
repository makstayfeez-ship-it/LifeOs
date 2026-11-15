package com.lifeos.app.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * Перевод между форматом Claude (в нём устроен цикл помощника: текст, tool_use, tool_result, картинки) и общим форматом
 * OpenAI chat/completions, который понимают OpenAI, Gemini, Groq, OpenRouter и другие сервисы.
 */
object AiBridge {
    private const val MAX_IMAGES = 5

    /** Запрос в формате Claude → запрос OpenAI. */
    fun toOpenAi(body: JSONObject, model: String, strictOpenAi: Boolean): JSONObject {
        val messages = JSONArray()
        body.optString("system").takeIf { it.isNotBlank() }?.let {
            messages.put(JSONObject().put("role", "system").put("content", it))
        }
        val src = body.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until src.length()) {
            val m = src.getJSONObject(i)
            val role = m.getString("role")
            val content = m.opt("content")
            if (content is String) {
                messages.put(JSONObject().put("role", role).put("content", content))
                continue
            }
            val blocks = content as? JSONArray ?: JSONArray()
            if (role == "assistant") {
                val text = StringBuilder()
                val calls = JSONArray()
                for (j in 0 until blocks.length()) {
                    val b = blocks.getJSONObject(j)
                    when (b.optString("type")) {
                        "text" -> text.append(b.optString("text"))
                        "tool_use" -> {
                            val call = JSONObject()
                                .put("id", b.getString("id"))
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject().put("name", b.getString("name")).put("arguments", (b.optJSONObject("input") ?: JSONObject()).toString()),
                                )
                            // Gemini просит вернуть «подпись размышлений» вместе с вызовом инструмента
                            b.optJSONObject("extra_content")?.let { call.put("extra_content", it) }
                            calls.put(call)
                        }
                    }
                }
                val msg = JSONObject().put("role", "assistant")
                if (text.isNotEmpty()) msg.put("content", text.toString()) else msg.put("content", JSONObject.NULL)
                if (calls.length() > 0) msg.put("tool_calls", calls)
                messages.put(msg)
            } else {
                val text = StringBuilder()
                val pics = mutableListOf<JSONObject>()
                for (j in 0 until blocks.length()) {
                    val b = blocks.getJSONObject(j)
                    when (b.optString("type")) {
                        "tool_result" -> messages.put(
                            JSONObject().put("role", "tool").put("tool_call_id", b.getString("tool_use_id")).put("content", b.opt("content")?.toString().orEmpty()),
                        )
                        "text" -> text.append(b.optString("text"))
                        "image" -> {
                            val s = b.optJSONObject("source")
                            if (s != null && s.optString("type") == "base64" && s.optString("data").isNotEmpty()) pics += s
                        }
                    }
                }
                if (pics.isNotEmpty()) {
                    val parts = JSONArray()
                    pics.take(MAX_IMAGES).forEach { s ->
                        val mime = s.optString("media_type").ifBlank { "image/jpeg" }
                        parts.put(
                            JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,${s.getString("data")}")),
                        )
                    }
                    parts.put(JSONObject().put("type", "text").put("text", text.toString().ifBlank { "Посмотри на изображение." }))
                    messages.put(JSONObject().put("role", "user").put("content", parts))
                } else if (text.isNotEmpty()) {
                    messages.put(JSONObject().put("role", "user").put("content", text.toString()))
                }
            }
        }
        val out = JSONObject().put("model", model).put("messages", messages)
        val tokens = body.optInt("max_tokens", 1024)
        // Новые модели OpenAI не принимают max_tokens; остальные сервисы знают только его
        out.put(if (strictOpenAi) "max_completion_tokens" else "max_tokens", tokens)
        val tools = body.optJSONArray("tools")
        if (tools != null && tools.length() > 0) {
            val list = JSONArray()
            for (i in 0 until tools.length()) {
                val t = tools.getJSONObject(i)
                list.put(
                    JSONObject().put("type", "function").put(
                        "function",
                        JSONObject()
                            .put("name", t.getString("name"))
                            .put("description", t.optString("description"))
                            .put("parameters", t.optJSONObject("input_schema") ?: JSONObject().put("type", "object").put("properties", JSONObject())),
                    ),
                )
            }
            out.put("tools", list).put("tool_choice", "auto")
        }
        if (model.contains("gpt-oss", ignoreCase = true)) out.put("reasoning_effort", "low")
        return out
    }

    /** Ответ OpenAI → ответ в формате Claude (content и stop_reason), как его ждёт цикл помощника. */
    fun fromOpenAi(data: JSONObject): JSONObject {
        val msg = data.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: JSONObject()
        val content = JSONArray()
        val text = msg.optString("content", "")
        if (text.isNotBlank() && text != "null") content.put(JSONObject().put("type", "text").put("text", text))
        else msg.optString("refusal", "").takeIf { it.isNotBlank() && it != "null" }?.let { content.put(JSONObject().put("type", "text").put("text", it)) }
        val calls = msg.optJSONArray("tool_calls")
        var n = 0
        if (calls != null) {
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val fn = call.optJSONObject("function") ?: continue
                val input = runCatching { JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" }) }.getOrDefault(JSONObject())
                val block = JSONObject()
                    .put("type", "tool_use")
                    .put("id", call.optString("id").ifBlank { "call_${System.currentTimeMillis()}_${n++}" })
                    .put("name", fn.optString("name"))
                    .put("input", input)
                call.optJSONObject("extra_content")?.let { block.put("extra_content", it) }
                content.put(block)
            }
        }
        var hasTools = false
        for (i in 0 until content.length()) if (content.getJSONObject(i).optString("type") == "tool_use") hasTools = true
        return JSONObject().put("type", "message").put("role", "assistant").put("content", content)
            .put("stop_reason", if (hasTools) "tool_use" else "end_turn")
    }
}
