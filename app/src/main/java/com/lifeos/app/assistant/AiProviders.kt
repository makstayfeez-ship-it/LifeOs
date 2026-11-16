package com.lifeos.app.assistant

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Как говорить с нейросетью: родной формат Claude или общий формат OpenAI (его понимают OpenAI, Gemini, Groq и многие другие). */
enum class AiKind { ANTHROPIC, OPENAI }

/**
 * Один сервис нейросети. [models] — модели по порядку предпочтения: из списка, который сервис отдаёт для ключа,
 * берётся первая подходящая, поэтому устаревшее название не ломает ИИ.
 */
class AiProvider(
    val id: String,
    val title: String,
    val kind: AiKind,
    val url: String,
    val models: List<String>,
    val modelsUrl: String? = null,
    val keyPage: String = "",
) {
    val host: String get() = runCatching { URL(url).host }.getOrDefault(url)

    /** Модели OpenAI (кроме старых) не принимают max_tokens, им нужен max_completion_tokens. */
    val strictOpenAi: Boolean get() = id == "openai"
}

object AiProviders {
    val CLAUDE = AiProvider(
        "claude", "Claude", AiKind.ANTHROPIC, "https://api.anthropic.com/v1/messages",
        listOf("claude-sonnet-5-5", "claude-haiku-5-5"), null, "https://console.anthropic.com/settings/keys",
    )
    val OPENAI = AiProvider(
        "openai", "OpenAI (ChatGPT)", AiKind.OPENAI, "https://api.openai.com/v1/chat/completions",
        listOf("gpt-6.1-terra", "gpt-5.6-terra", "gpt-6-luna", "gpt-5.4-mini", "gpt-4.1"),
        "https://api.openai.com/v1/models", "https://platform.openai.com/api-keys",
    )
    val GEMINI = AiProvider(
        "gemini", "Google Gemini", AiKind.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        listOf("gemini-3.8-flash", "gemini-3-flash", "gemini-2.5-flash"),
        "https://generativelanguage.googleapis.com/v1beta/openai/models", "https://aistudio.google.com/apikey",
    )
    val GROQ = AiProvider(
        "groq", "Groq", AiKind.OPENAI, "https://api.groq.com/openai/v1/chat/completions",
        listOf("openai/gpt-oss-120b"), "https://api.groq.com/openai/v1/models", "https://console.groq.com/keys",
    )
    val OPENROUTER = AiProvider(
        "openrouter", "OpenRouter", AiKind.OPENAI, "https://openrouter.ai/api/v1/chat/completions",
        listOf("openrouter/auto"), "https://openrouter.ai/api/v1/models", "https://openrouter.ai/keys",
    )

    /** Любой другой сервис, совместимый с OpenAI: адрес вида https://сервис/v1 (или сразу …/chat/completions). */
    fun custom(baseUrl: String, model: String): AiProvider {
        val clean = baseUrl.trim().trimEnd('/')
        val url = if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
        val models = if (clean.endsWith("/chat/completions")) clean.removeSuffix("/chat/completions") + "/models" else "$clean/models"
        return AiProvider("custom", "Свой сервис", AiKind.OPENAI, url, listOfNotNull(model.trim().ifBlank { null }), models)
    }

    /** Сервис по виду ключа. Незнакомый ключ без адреса определить нельзя: null. */
    fun detect(key: String): AiProvider? {
        val k = key.trim()
        return when {
            k.startsWith("sk-ant-") -> CLAUDE
            k.startsWith("sk-or-") -> OPENROUTER
            k.startsWith("AIza") -> GEMINI
            k.startsWith("gsk_") -> GROQ
            k.startsWith("sk-") -> OPENAI
            else -> null
        }
    }

    /** Сервис для ключа с учётом своего адреса. */
    fun resolve(key: String, baseUrl: String, model: String): AiProvider? {
        if (baseUrl.isNotBlank()) return custom(baseUrl, model)
        return detect(key)
    }

    val all = listOf(OPENAI, CLAUDE, GEMINI)
}

/** Выбор модели: своя, запомненная или лучшая из доступных ключу. Вызывать не из главного потока. */
object AiModels {
    private fun prefs(context: Context) = context.getSharedPreferences("lifeos_ai", Context.MODE_PRIVATE)

    private fun cacheKey(p: AiProvider, key: String) = "model_${p.id}_${(p.url + key).hashCode()}"

    /** Забыть запомненный выбор модели (после смены ключа). */
    fun forget(context: Context) {
        val p = prefs(context)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("model_") }.forEach { e.remove(it) }
        e.apply()
    }

    fun pick(context: Context, p: AiProvider, key: String, override: String): String {
        if (override.isNotBlank()) return override.trim()
        if (p.kind == AiKind.ANTHROPIC || p.modelsUrl == null) return p.models.firstOrNull().orEmpty()
        val ck = cacheKey(p, key)
        prefs(context).getString(ck, null)?.let { return it }
        val available = runCatching { list(p, key) }.getOrNull().orEmpty()
        val chosen = choose(p, available) ?: return p.models.firstOrNull().orEmpty()
        prefs(context).edit().putString(ck, chosen).apply()
        return chosen
    }

    /** Из списка доступных берём первую из предпочтительных; если ни одной нет, подбираем по названию. */
    internal fun choose(p: AiProvider, available: List<String>): String? {
        if (available.isEmpty()) return null
        p.models.firstOrNull { it in available }?.let { return it }
        if (p.id == "gemini") {
            val bad = Regex("lite|image|tts|live|audio|embed|preview|exp|thinking|robotics|vision|imagen|veo|learnlm|gemma|aqa")
            return available.filter { it.contains("flash") && !bad.containsMatchIn(it) }.maxWithOrNull(compareBy<String>({ version(it) }, { -it.length }))
        }
        if (p.id == "openai") {
            val bad = Regex("audio|realtime|image|tts|transcribe|search|codex|instruct|embed|moderation|whisper|dall|sora|nano|deprecated|chat-latest|mini-")
            return available.filter { it.startsWith("gpt-") && !bad.containsMatchIn(it) }.maxWithOrNull(compareBy<String>({ version(it) }, { -it.length }))
        }
        return null
    }

    /** Версия из названия: gemini-3.8-flash → 3.8. */
    private fun version(id: String): Double = Regex("(\\d+(?:\\.\\d+)?)").find(id)?.value?.toDoubleOrNull() ?: 0.0

    private fun list(p: AiProvider, key: String): List<String> {
        val url = p.modelsUrl ?: return emptyList()
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("authorization", "Bearer $key")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            if (c.responseCode !in 200..299) return emptyList()
            val data = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return emptyList()
            return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.removePrefix("models/")?.takeIf { id -> id.isNotBlank() } }
        } finally {
            c.disconnect()
        }
    }
}
