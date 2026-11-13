package com.lifeos.app.assistant

import android.content.Context
import com.lifeos.llm.LlmRunner
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Маленькая ИИ-модель прямо в телефоне (Qwen 2.5, 1,5 млрд параметров, открытая лицензия Apache 2.0). Работает без интернета и без лимитов,
 * но слабее облачного ИИ: отвечает на простые вопросы и разговаривает, а действий на телефоне не выполняет (их делают команды).
 * Скачивается один раз, около 1,6 ГБ.
 */
object LocalModel {
    private const val FILE = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
    /** Примерный размер файла (около 1,6 ГБ): точная проверка идёт по размеру, который назвал сервер при скачивании. */
    private const val APPROX_BYTES = 1_590_000_000L
    private const val MIN_BYTES = 1_400_000_000L
    private const val MAX_BYTES = 1_900_000_000L
    private val URLS = listOf(
        "https://github.com/makstayfeez-ship-it/LifeOs/releases/download/llm-model-qwen/$FILE",
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/$FILE",
    )

    data class State(val downloading: Boolean = false, val percent: Int = 0, val megabytes: Int = 0, val error: String = "")

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun dir(c: Context) = File(c.filesDir, "llm").apply { mkdirs() }
    private fun model(c: Context) = File(dir(c), FILE)
    private fun part(c: Context) = File(dir(c), "$FILE.part")

    fun isReady(c: Context): Boolean = model(c).let { it.exists() && it.length() in MIN_BYTES..MAX_BYTES }

    fun startDownload(c: Context) {
        val app = c.applicationContext
        if (isReady(app) || job?.isActive == true) return
        job = scope.launch { download(app) }
    }

    fun cancelDownload() {
        job?.cancel()
        _state.value = State()
    }

    fun delete(c: Context) {
        val app = c.applicationContext
        job?.cancel()
        LocalLlm.close()
        dir(app).deleteRecursively()
        _state.value = State()
    }

    private fun download(app: Context) {
        if (dir(app).usableSpace < APPROX_BYTES + 400_000_000L) {
            _state.value = State(error = "Не хватает места: нужно примерно 2 ГБ свободной памяти")
            return
        }
        _state.value = State(downloading = true)
        var lastError = ""
        for (url in URLS) {
            try {
                downloadFrom(app, url)
                _state.value = State()
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message?.take(80) ?: e.javaClass.simpleName
            }
        }
        _state.value = State(error = "Не удалось скачать ($lastError). Подключитесь к Wi‑Fi и нажмите «Скачать» ещё раз: докачка продолжится с того же места")
    }

    private fun downloadFrom(app: Context, url: String) {
        val tmp = part(app)
        var expectedTotal = -1L
        var have = if (tmp.exists()) tmp.length() else 0L
        if (have >= MAX_BYTES) { tmp.delete(); have = 0 }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("ответ сервера $code")
            val resumed = code == 206
            if (!resumed) have = 0
            // Полный размер файла: из Content-Range при докачке или из Content-Length
            val total: Long = if (resumed) {
                conn.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
            } else {
                conn.contentLengthLong
            }
            val expected = if (total > 0) total else APPROX_BYTES
            var read = have
            var shown = -1
            expectedTotal = total
            conn.inputStream.use { input ->
                FileOutputStream(tmp, resumed).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        val p = (read * 100 / expected).toInt().coerceIn(0, 99)
                        if (p != shown) {
                            shown = p
                            _state.value = State(downloading = true, percent = p, megabytes = (read / 1_000_000).toInt())
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val got = tmp.length()
        if ((expectedTotal > 0 && got != expectedTotal) || got !in MIN_BYTES..MAX_BYTES) {
            if (got > MAX_BYTES || (expectedTotal > 0 && got > expectedTotal)) tmp.delete()
            error("файл неполный ($got байт)")
        }
        val target = model(app)
        target.delete()
        if (!tmp.renameTo(target)) error("не удалось сохранить модель")
    }
}

/** Запуск модели в телефоне. Загруженная в память модель занимает около 2 ГБ, поэтому через 3 минуты простоя выгружается. */
object LocalLlm {
    private var engine: LlmRunner? = null
    private var lastUse = 0L
    private var idleJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Ответ модели или null, если модель не скачана или не смогла запуститься. Вызывать не из главного потока. */
    @Synchronized
    fun answer(c: Context, system: String, history: List<Pair<String, String>>, userText: String): String? {
        val app = c.applicationContext
        if (!LocalModel.isReady(app)) return null
        return try {
            val e = engine ?: LlmRunner(
                File(File(app.filesDir, "llm"), "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm").absolutePath,
                app.cacheDir.path,
            ).also { engine = it }
            val prompt = buildString {
                history.takeLast(6).forEach { (role, text) ->
                    append(if (role == "user") "Пользователь: " else "Помощник: ").append(text.take(400)).append('\n')
                }
                if (isNotEmpty()) append("\nНовое сообщение пользователя: ")
                append(userText)
            }
            val reply = e.ask(system, prompt).trim()
            lastUse = System.currentTimeMillis()
            scheduleRelease()
            reply.ifBlank { null }
        } catch (t: Throwable) {
            close()
            null
        }
    }

    @Synchronized
    fun close() {
        idleJob?.cancel()
        runCatching { engine?.close() }
        engine = null
    }

    private fun scheduleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(180_000)
            synchronized(this@LocalLlm) {
                if (System.currentTimeMillis() - lastUse >= 170_000) {
                    runCatching { engine?.close() }
                    engine = null
                }
            }
        }
    }
}
