package com.lifeos.app.assistant

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import kotlinx.coroutines.delay

/** Звуковые сигналы начала и конца записи заметки: понятно, что телефон слушает, даже без экрана. */
object Beeps {
    suspend fun start() = play(ToneGenerator.TONE_PROP_BEEP, 150)

    suspend fun done() {
        play(ToneGenerator.TONE_PROP_ACK, 200)
    }

    private suspend fun play(tone: Int, ms: Int) {
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            g.startTone(tone, ms)
            delay(ms + 250L)
            g.release()
        }
    }
}

/**
 * Диктовка заметки. Слушает фразу за фразой и склеивает их в один текст. Запись заканчивается, когда вы
 * замолчали на несколько секунд, сказали «готово» или «стоп», либо прошло три минуты.
 */
object Dictation {
    private val stopWords = setOf("готово", "стоп", "конец", "хватит", "закончить", "сохрани", "сохранить", "всё", "все")

    suspend fun record(
        listener: SpeechListener,
        maxMs: Long = 180_000,
        onPartial: (String) -> Unit = {},
    ): String {
        val parts = mutableListOf<String>()
        val started = SystemClock.elapsedRealtime()
        var emptyTries = 0
        while (SystemClock.elapsedRealtime() - started < maxMs) {
            when (val r = listener.listen { onPartial((parts + it).joinToString(" ")) }) {
                is SpeechResult.Heard -> {
                    emptyTries = 0
                    val words = r.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
                    val stop = words.isNotEmpty() && words.last().lowercase().trim('.', ',', '!') in stopWords
                    if (stop) words.removeAt(words.lastIndex)
                    if (words.isNotEmpty()) parts += words.joinToString(" ")
                    if (stop) break
                }
                SpeechResult.Silence -> {
                    // Пока ничего не сказали, даём вторую попытку; после начала речи пауза означает конец заметки
                    if (parts.isNotEmpty() || ++emptyTries >= 2) break
                }
                is SpeechResult.Error -> break
            }
        }
        val text = parts.joinToString(" ").trim()
        return text.replaceFirstChar { it.uppercase() }
    }
}
