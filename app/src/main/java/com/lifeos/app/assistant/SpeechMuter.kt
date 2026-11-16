package com.lifeos.app.assistant

import android.content.Context
import android.media.AudioManager

/**
 * Служба распознавания Google пищит при каждом запуске и остановке. Когда помощник слушает постоянно, это раздражает,
 * поэтому на время прослушивания мы приглушаем звук мультимедиа и системные сигналы. Если что-то играет, ничего не глушим.
 * Звуки уведомлений и будильников не трогаем: напоминания должны звонить.
 * Что именно приглушили, записываем на диск, чтобы вернуть звук, даже если приложение внезапно закроется.
 */
object SpeechMuter {
    @Volatile var enabled = true

    private val streams = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM)
    private const val PREFS = "speech_muter"
    private const val KEY = "muted"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun mute(c: Context) {
        if (!enabled) return
        val am = c.getSystemService(AudioManager::class.java) ?: return
        if (am.isMusicActive) return
        val done = mutableSetOf<String>()
        for (s in streams) {
            runCatching {
                if (!am.isStreamMute(s)) {
                    am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                    done += s.toString()
                }
            }
        }
        if (done.isNotEmpty()) prefs(c).edit().putStringSet(KEY, prefs(c).getStringSet(KEY, emptySet()).orEmpty() + done).commit()
    }

    /** Возвращает звук тем потокам, которые приглушили мы. Безопасно вызывать сколько угодно раз. */
    @Synchronized
    fun restore(c: Context) {
        val p = prefs(c)
        val saved = p.getStringSet(KEY, emptySet()).orEmpty()
        if (saved.isEmpty()) return
        val am = c.getSystemService(AudioManager::class.java) ?: return
        for (s in saved) runCatching { am.adjustStreamVolume(s.toInt(), AudioManager.ADJUST_UNMUTE, 0) }
        p.edit().remove(KEY).commit()
    }
}
