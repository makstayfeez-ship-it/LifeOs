package com.lifeos.app.assistant

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Выход помощника в интернет: поиск и чтение страниц. Ключи не нужны: поиск идёт через DuckDuckGo,
 * а если он не ответил, через Википедию. Всё, что пришло из сети, ИИ считает данными, а не командами.
 */
object WebTools {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; LifeOS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MAX_PAGE_BYTES = 1_500_000
    private const val MAX_PAGE_TEXT = 7000

    class Hit(val title: String, val url: String, val snippet: String)

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext "Пустой запрос"
        val hits = ArrayList<Hit>()
        runCatching { hits += duckDuckGo(q) }
        val wiki = if (hits.size < 3) runCatching { wikipedia(q) }.getOrDefault("") else ""
        if (hits.isEmpty() && wiki.isBlank()) {
            return@withContext "Поиск сейчас не отвечает (нет интернета или сайт недоступен). Скажи об этом пользователю и ответь по своим знаниям, предупредив, что они могут быть неточными."
        }
        buildString {
            hits.take(6).forEachIndexed { i, h ->
                append(i + 1).append(". ").append(h.title).append('\n').append(h.url).append('\n')
                if (h.snippet.isNotBlank()) append(h.snippet).append('\n')
                append('\n')
            }
            if (wiki.isNotBlank()) append("Из Википедии:\n").append(wiki)
        }.trim()
    }

    // ---------- DuckDuckGo (html-версия без ключей) ----------

    private fun duckDuckGo(q: String): List<Hit> {
        val html = httpGet("https://html.duckduckgo.com/html/?q=${URLEncoder.encode(q, "UTF-8")}&kl=ru-ru", 400_000)
        return parseDuckDuckGo(html)
    }

    internal fun parseDuckDuckGo(html: String): List<Hit> {
        val out = ArrayList<Hit>()
        val link = Regex("""<a[^>]+class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snip = Regex("""class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</(?:a|td|div)>""", RegexOption.DOT_MATCHES_ALL)
        val snippets = snip.findAll(html).map { clean(it.groupValues[1]) }.toList()
        link.findAll(html).forEachIndexed { i, m ->
            val url = realUrl(m.groupValues[1]) ?: return@forEachIndexed
            val title = clean(m.groupValues[2])
            if (title.isNotBlank() && !url.contains("duckduckgo.com/y.js")) out += Hit(title, url, snippets.getOrElse(i) { "" })
        }
        return out
    }

    private fun realUrl(href: String): String? {
        var u = href.replace("&amp;", "&")
        if (u.startsWith("//")) u = "https:$u"
        val redirect = Regex("""[?&]uddg=([^&]+)""").find(u)
        if (redirect != null) u = runCatching { URLDecoder.decode(redirect.groupValues[1], "UTF-8") }.getOrNull() ?: return null
        return u.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    // ---------- Википедия ----------

    private fun wikipedia(q: String): String {
        for (lang in listOf("ru", "en")) {
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${URLEncoder.encode(q, "UTF-8")}" +
                "&gsrlimit=3&prop=extracts&exintro=1&explaintext=1&exchars=800&format=json&utf8=1"
            val pages = JSONObject(httpGet(url, 200_000)).optJSONObject("query")?.optJSONObject("pages") ?: continue
            val list = pages.keys().asSequence().map { pages.getJSONObject(it) }.sortedBy { it.optInt("index", 99) }.toList()
            val text = list.joinToString("\n\n") { p ->
                val title = p.optString("title")
                "$title (https://$lang.wikipedia.org/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}):\n${p.optString("extract").trim()}"
            }
            if (text.isNotBlank()) return text
        }
        return ""
    }

    // ---------- чтение страницы ----------

    suspend fun readPage(url: String): String = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return@withContext "Нужна ссылка, которая начинается с http:// или https://"
        try {
            val body = httpGet(u, MAX_PAGE_BYTES)
            val text = if (body.trimStart().startsWith("<")) htmlToText(body) else body.trim()
            if (text.isBlank()) "Страница пустая или читается только в браузере." else text.take(MAX_PAGE_TEXT)
        } catch (e: Exception) {
            "Страницу не получилось открыть: ${e.message ?: "ошибка сети"}"
        }
    }

    internal fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header|form|iframe)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?is)<!--.*?-->"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>|</tr>"), "\n")
        s = s.replace(Regex("<[^>]+>"), " ")
        return unescape(s).lines().joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    // ---------- общее ----------

    private fun clean(s: String): String = unescape(s.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&laquo;", "«").replace("&raquo;", "»")
        .replace("&mdash;", "—").replace("&ndash;", "–").replace("&hellip;", "…")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }

    /** Не даёт ИИ ходить по адресам домашней сети и самого телефона. */
    private fun isPublic(host: String): Boolean = runCatching {
        InetAddress.getAllByName(host).none { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
    }.getOrDefault(false)

    private fun httpGet(start: String, limit: Int): String {
        var url = start
        repeat(5) {
            val parsed = URL(url)
            if (!isPublic(parsed.host)) throw IllegalStateException("адрес недоступен")
            val conn = (parsed.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Language", "ru,en;q=0.8")
                setRequestProperty("Accept", "text/html,application/json,text/plain;q=0.9,*/*;q=0.5")
            }
            try {
                val code = conn.responseCode
                if (code in 301..308) {
                    val loc = conn.getHeaderField("Location") ?: throw IllegalStateException("перенаправление без адреса")
                    url = URL(parsed, loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IllegalStateException("сайт ответил кодом $code")
                val type = conn.contentType.orEmpty().lowercase()
                if (type.isNotEmpty() && !(type.startsWith("text/") || type.contains("json") || type.contains("xml"))) {
                    throw IllegalStateException("это не текстовая страница ($type)")
                }
                val cs = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)
                val bytes = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16_384)
                    while (out.size() < limit) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                return String(bytes, runCatching { charset(cs ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("слишком много перенаправлений")
    }
}
