package com.lifeos.app.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lifeos.app.container
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

/**
 * Офлайн-модель русской речи (Vosk, около 45 МБ). Скачивается один раз с сайта разработчиков Vosk и работает
 * без интернета и без служб Google, поэтому помощник слышит «LifeOS» и «заметка» и на телефонах, где голосового ввода Google нет.
 */
object VoskModel {
    private const val NAME = "vosk-model-small-ru-0.22"
    // Сначала зеркало на GitHub (оно надёжнее открывается), потом сайт разработчиков Vosk
    private val URLS = listOf(
        "https://github.com/makstayfeez-ship-it/LifeOs/releases/download/vosk-model-ru/$NAME.zip",
        "https://alphacephei.com/vosk/models/$NAME.zip",
    )
    private const val APPROX_BYTES = 47_000_000L

    data class State(val downloading: Boolean = false, val percent: Int = 0, val error: String = "")

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var model: Model? = null

    private fun root(c: Context) = File(c.filesDir, "vosk")
    private fun dir(c: Context) = File(root(c), NAME)
    private fun marker(c: Context) = File(dir(c), ".ready")

    fun isReady(c: Context): Boolean = marker(c).exists()

    /** Запускает скачивание в фоне. Если модель уже есть или скачивание идёт, ничего не делает. */
    fun startDownload(c: Context) {
        val app = c.applicationContext
        if (isReady(app) || job?.isActive == true) return
        job = scope.launch {
            download(app)
            if (isReady(app) && app.container.settings.current().wakeEnabled) AssistantControl.startListening(app)
        }
    }

    /** Удаляет модель и скачивает заново (если она повредилась). */
    fun reinstall(c: Context) {
        val app = c.applicationContext
        if (job?.isActive == true) return
        synchronized(this) {
            runCatching { model?.close() }
            model = null
        }
        root(app).deleteRecursively()
        startDownload(app)
    }

    /** Скачивать без вопросов можно только по безлимитному Wi‑Fi. */
    fun onUnmeteredNetwork(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Загруженная в память модель (первая загрузка занимает пару секунд). */
    @Synchronized
    fun model(c: Context): Model? {
        model?.let { return it }
        if (!isReady(c)) return null
        return runCatching {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(dir(c).absolutePath)
        }.onFailure {
            AssistantStatus.error("Не удалось загрузить офлайн-модель речи. Откройте «Проверку помощника» и скачайте её заново")
        }.getOrNull()?.also { model = it }
    }

    private fun download(app: Context) {
        _state.value = State(downloading = true)
        var lastError = ""
        for (url in URLS) {
            val ok = try {
                downloadFrom(app, url)
                true
            } catch (e: Exception) {
                lastError = e.message?.take(80) ?: e.javaClass.simpleName
                false
            }
            if (ok) {
                _state.value = State()
                return
            }
        }
        _state.value = State(error = "Не удалось скачать ($lastError). Проверьте интернет и нажмите «Скачать» ещё раз")
    }

    private fun downloadFrom(app: Context, url: String) {
        val tmp = File(root(app), "tmp")
        try {
            tmp.deleteRecursively()
            tmp.mkdirs()
            _state.value = State(downloading = true)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) error("ответ сервера ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: APPROX_BYTES
            var read = 0L
            var shown = -1
            val counting = object : FilterInputStream(conn.inputStream.buffered()) {
                private fun bump(n: Int) {
                    read += n
                    val p = (read * 100 / total).toInt().coerceIn(0, 99)
                    if (p != shown) {
                        shown = p
                        _state.value = State(downloading = true, percent = p)
                    }
                }

                override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) bump(it) }
            }
            unzip(counting, tmp)
            conn.disconnect()

            val extracted = File(tmp, NAME)
            if (!extracted.isDirectory) error("в архиве нет папки модели")
            dir(app).deleteRecursively()
            if (!extracted.renameTo(dir(app))) error("не удалось сохранить модель")
            marker(app).writeText("ok")
            tmp.deleteRecursively()
        } catch (e: Exception) {
            tmp.deleteRecursively()
            throw e
        }
    }

    private fun unzip(input: InputStream, into: File) {
        val base = into.canonicalPath + File.separator
        ZipInputStream(input).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(into, entry.name)
                // Защита от путей вида ../../ внутри архива
                if (!out.canonicalPath.startsWith(base)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zin.copyTo(it) }
                }
            }
        }
    }
}
