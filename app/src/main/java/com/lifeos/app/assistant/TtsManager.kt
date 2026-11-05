package com.lifeos.app.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.VoiceType
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Озвучка ответов. Три голоса помощника:
 *  - мужской и женский: на Android нет надёжного признака пола у голоса, поэтому берём подходящий
 *    голос по имени (если в названии есть male/female) и сдвигаем тон;
 *  - свой: пользователь сам выбирает голос из установленных, тон и скорость.
 */
class TtsManager(context: Context) {
    private val ru = Locale("ru", "RU")
    private var tts: TextToSpeech? = null

    // 0 — запускается, 1 — готов, 2 — не удалось запустить
    @Volatile private var state = 0
    private val queued = mutableListOf<() -> Unit>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.language = ru
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finished(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finished(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
                })
                state = 1
            } else {
                state = 2
            }
            val todo = synchronized(queued) { queued.toList().also { queued.clear() } }
            todo.forEach { it() }
        }
    }

    private fun finished(id: String?) {
        if (id != null) callbacks.remove(id)?.invoke()
    }

    fun speak(text: String, s: AppSettings, onDone: (() -> Unit)? = null) {
        val job: () -> Unit = {
            val engine = tts
            if (state != 1 || engine == null) {
                onDone?.invoke()
            } else {
                applyVoice(engine, s)
                val id = UUID.randomUUID().toString()
                if (onDone != null) callbacks[id] = onDone
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            }
        }
        when (state) {
            0 -> synchronized(queued) { queued += job }
            else -> job()
        }
    }

    /** Говорит и ждёт, пока фраза закончится (нужно, чтобы микрофон не слышал самого помощника). */
    suspend fun speakAndWait(text: String, s: AppSettings) {
        suspendCancellableCoroutine { cont ->
            speak(text, s) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { stop() }
        }
    }

    fun stop() {
        tts?.stop()
        callbacks.clear()
    }

    /** Названия установленных русских голосов — для выбора «своего» голоса. */
    fun russianVoices(): List<String> = runCatching {
        tts?.voices.orEmpty()
            .filter { it.locale.language == "ru" }
            .map { it.name }
            .sorted()
    }.getOrDefault(emptyList())

    private fun applyVoice(engine: TextToSpeech, s: AppSettings) {
        engine.language = ru // сбрасывает выбранный ранее голос на стандартный
        when (s.voiceType) {
            VoiceType.MALE -> {
                engine.setPitch(0.72f)
                engine.setSpeechRate(0.98f)
                pickByGender(engine, male = true)
            }
            VoiceType.FEMALE -> {
                engine.setPitch(1.12f)
                engine.setSpeechRate(1f)
                pickByGender(engine, male = false)
            }
            VoiceType.CUSTOM -> {
                engine.setPitch(s.customPitch)
                engine.setSpeechRate(s.customRate)
                if (s.customVoiceName.isNotBlank()) {
                    engine.voices.orEmpty().firstOrNull { it.name == s.customVoiceName }?.let { engine.voice = it }
                }
            }
        }
    }

    private fun pickByGender(engine: TextToSpeech, male: Boolean) {
        val candidates = engine.voices.orEmpty().filter { it.locale.language == "ru" }
        val match = candidates.firstOrNull {
            val n = it.name.lowercase()
            if (male) n.contains("male") && !n.contains("female") else n.contains("female")
        }
        if (match != null) engine.voice = match
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
