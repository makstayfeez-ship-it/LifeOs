package com.lifeos.app.assistant

import kotlin.math.sqrt

/**
 * Нечёткое сравнение слов. Распознавание речи часто путает одну-две буквы («лайфус» вместо «лайфос»,
 * «заблакируй» вместо «заблокируй»), поэтому слова сравниваются по похожести, а не только точно.
 * Идея позаимствована у проекта Jarvis (поиск команды по похожим словам); код написан заново.
 */
object Fuzzy {
    /** Похожесть двух строк от 0 до 100 по расстоянию Левенштейна. */
    fun ratio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 100.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return (1.0 - distance(a, b).toDouble() / maxOf(a.length, b.length)) * 100.0
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    /** Какая доля слов фразы нашлась в образце (слово считается найденным, если оно похоже хотя бы на 70%). */
    private fun wordOverlap(input: List<String>, sample: List<String>): Double {
        var matched = 0.0
        for (w in input) {
            val best = sample.maxOf { ratio(w, it) }
            if (best > 70.0) matched += best / 100.0
        }
        return matched / maxOf(input.size, sample.size) * 100.0
    }

    /** Общая оценка: 60% похожесть по буквам и 40% по словам. 100 — фразы совпали. */
    fun score(input: String, sample: String): Double {
        val a = input.split(" ").filter { it.isNotEmpty() }
        val b = sample.split(" ").filter { it.isNotEmpty() }
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 0.6 * ratio(input, sample) + 0.4 * wordOverlap(a, b)
    }

    /**
     * Ближайший образец к фразе или null. Берётся только явный победитель: если два разных образца почти одинаково
     * похожи («включи» и «выключи» отличаются парой букв), лучше не делать ничего, чем сделать обратное.
     */
    fun closest(input: String, samples: List<String>, minScore: Double = 82.0, margin: Double = 5.0): String? {
        val words = input.split(" ").count { it.isNotEmpty() }
        if (words == 0 || words > 5 || input.length < 6) return null
        val scored = samples.map { it to score(input, it) }.sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        if (best.second < minScore) return null
        val second = scored.getOrNull(1)
        if (second != null && best.second - second.second < margin) return null
        return best.first
    }
}

/**
 * Автоусиление тихого голоса перед распознаванием: если говорят вполголоса или далеко от телефона, звук подтягивается
 * до удобного уровня. Тишина и шум не усиливаются, чтобы не «слышать» то, чего не говорили.
 */
class AutoGain {
    private var gain = 1f

    fun apply(buf: ShortArray, n: Int) {
        if (n <= 0) return
        var sum = 0.0
        for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
        val rms = sqrt(sum / n).toFloat()
        // Тишина, фоновый шум или уже громкий звук: ничего не меняем, усиление плавно возвращается к единице
        val target = if (rms < NOISE_FLOOR || rms > TARGET_RMS) 1f else (TARGET_RMS / rms).coerceIn(1f, MAX_GAIN)
        gain = gain * 0.6f + target * 0.4f
        if (gain < 1.05f) return
        for (i in 0 until n) buf[i] = (buf[i] * gain).coerceIn(-32768f, 32767f).toInt().toShort()
    }

    private companion object {
        const val NOISE_FLOOR = 150f
        const val TARGET_RMS = 3000f
        const val MAX_GAIN = 2.5f
    }
}
