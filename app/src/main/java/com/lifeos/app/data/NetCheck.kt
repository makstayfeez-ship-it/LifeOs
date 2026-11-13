package com.lifeos.app.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Проверка, какие серверы открываются в вашей сети без VPN. Любой ответ сервера считается «доступен». */
object NetCheck {
    data class Row(val title: String, val ok: Boolean, val detail: String)

    private val targets = listOf(
        "Интернет в целом (Яндекс)" to "https://ya.ru/",
        "Вход и регистрация (Google)" to "https://identitytoolkit.googleapis.com/",
        "Обновление входа (Google)" to "https://securetoken.googleapis.com/",
        "Облачное хранилище (Google)" to "https://firestore.googleapis.com/",
        "GitHub (скачивание моделей)" to "https://github.com/",
    )

    suspend fun run(extra: List<Pair<String, String>> = emptyList()): List<Row> = coroutineScope {
        (targets + extra).map { (title, url) -> async(Dispatchers.IO) { probe(title, url) } }.awaitAll()
    }

    private suspend fun probe(title: String, url: String): Row = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000
                readTimeout = 7_000
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            val code = try { c.responseCode } finally { c.disconnect() }
            Row(title, true, "открывается (${System.currentTimeMillis() - start} мс, ответ $code)")
        } catch (e: java.net.SocketTimeoutException) {
            Row(title, false, "не отвечает (таймаут)")
        } catch (e: java.net.UnknownHostException) {
            Row(title, false, "адрес не находится")
        } catch (e: Exception) {
            Row(title, false, "не открывается (${e.javaClass.simpleName})")
        }
    }

    /** Короткий вывод для человека. */
    fun verdict(rows: List<Row>): String {
        val internet = rows.firstOrNull()?.ok == true
        val google = rows.filter { it.title.contains("Google") }
        return when {
            !internet -> "Интернет сейчас не работает или очень слабый. Проверьте Wi‑Fi или мобильные данные."
            google.all { it.ok } -> "Серверы Google открываются. Если вход всё равно не проходит, пришлите мне этот список."
            else -> "Интернет есть, но серверы Google (вход и облако) в вашей сети не открываются без VPN. Это ограничение сети, приложение само его не обходит. " +
                "Можно пользоваться без облака или включить VPN только на время входа."
        }
    }
}
