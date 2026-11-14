package com.lifeos.app.assistant

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Звуковые сигналы начала и конца записи заметки: понятно, что телефон слушает, даже без экрана. */
object Beeps {
    /** Можно выключить в настройках помощника. */
    @Volatile var enabled = true

    suspend fun start() = play(ToneGenerator.TONE_PROP_BEEP, 150)

    /** Короткий сигнал «микрофон включён, говорите»: не ждёт окончания, чтобы не съесть начало фразы. */
    fun ready() {
        if (!enabled) return
        Thread {
            runCatching {
                val g = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                g.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                Thread.sleep(300)
                g.release()
            }
        }.start()
    }

    suspend fun done() {
        play(ToneGenerator.TONE_PROP_ACK, 200)
    }

    private suspend fun play(tone: Int, ms: Int) {
        if (!enabled) return
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            g.startTone(tone, ms)
            delay(ms + 250L)
            g.release()
        }
    }
}

/**
 * Диктовка заметки. Слушает фразу за фразой и склеивает их в один текст, пока вы не скажете «остановить»
 * (или «стоп», «готово», «закончить»), не нажмёте «Сохранить» на блокноте либо не помолчите долго (40 секунд).
 * Паузы между фразами не мешают: можно думать вслух.
 */
class DictationSession(
    private val listener: SpeechListener,
    private val accurate: Boolean = false,
    private val idleMs: Long = 40_000,
    private val maxMs: Long = 600_000,
    /** Вызывается один раз, когда микрофон правда включился (для сигнала «говорите»). */
    private val onReady: () -> Unit = {},
) {
    private val parts = mutableListOf<String>()

    /** Всё, что записано к этому моменту. Остаётся доступным, даже если запись прервали. */
    val text: String
        get() = synchronized(parts) { parts.joinToString(" ").trim().replaceFirstChar { it.uppercase() } }

    suspend fun run(onChange: (String) -> Unit = {}) {
        val started = SystemClock.elapsedRealtime()
        var lastSpeech = started
        var errors = 0
        var readyFired = false
        while (currentCoroutineContext().isActive &&
            SystemClock.elapsedRealtime() - started < maxMs &&
            SystemClock.elapsedRealtime() - lastSpeech < idleMs
        ) {
            val r = listener.listen(
                accurate,
                onPartial = { partial -> onChange((text + " " + partial).trim()) },
                onReady = {
                    if (!readyFired) {
                        readyFired = true
                        onReady()
                    }
                },
            )
            when (r) {
                is SpeechResult.Heard -> {
                    errors = 0
                    lastSpeech = SystemClock.elapsedRealtime()
                    val words = r.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val drop = stopLength(words)
                    val keep = words.dropLast(drop)
                    if (keep.isNotEmpty()) synchronized(parts) { parts += keep.joinToString(" ") }
                    onChange(text)
                    if (drop > 0) return
                }
                SpeechResult.Silence -> errors = 0
                is SpeechResult.Error -> {
                    if (r.fatal || ++errors >= 5) return
                    delay(400)
                }
            }
        }
    }

    companion object {
        private val stopWords = setOf(
            "остановить", "останови", "остановись", "остановите", "стоп", "стоп.", "заверши", "завершить", "закончи", "закончить",
            "готово", "хватит", "конец", "сохрани", "сохранить", "финиш", "всё", "все",
        )
        private val tails = setOf("запись", "заметку", "заметка", "диктовку", "диктовка")

        /** Сколько слов в конце фразы — команда остановки: «… остановить» → 1, «… остановить запись» → 2. */
        fun stopLength(words: List<String>): Int {
            fun norm(w: String) = w.lowercase().trim('.', ',', '!', '?')
            val last = words.lastOrNull()?.let(::norm) ?: return 0
            if (last in stopWords) return 1
            val prev = words.getOrNull(words.size - 2)?.let(::norm)
            if (last in tails && prev != null && prev in stopWords) return 2
            return 0
        }
    }
}

/** Короткая запись одной заметки без блокнота (для окон, у которых есть свой вид). */
object Dictation {
    suspend fun record(
        listener: SpeechListener,
        accurate: Boolean = false,
        onReady: () -> Unit = {},
        onPartial: (String) -> Unit = {},
    ): String {
        val d = DictationSession(listener, accurate, onReady = onReady)
        d.run(onPartial)
        return d.text
    }
}
