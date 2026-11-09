package com.lifeos.app.assistant

import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Что сейчас делает помощник. Нужно экрану «Проверка помощника», чтобы было видно, что он слышит и почему молчит. */
object AssistantStatus {
    data class State(
        val running: Boolean = false,
        val lastHeard: String = "",
        val lastError: String = "",
        val updatedAt: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun running(on: Boolean) {
        _state.value = _state.value.copy(running = on, updatedAt = System.currentTimeMillis())
    }

    fun heard(text: String) {
        _state.value = _state.value.copy(lastHeard = text, lastError = "", updatedAt = System.currentTimeMillis())
    }

    fun error(text: String) {
        _state.value = _state.value.copy(lastError = text, updatedAt = System.currentTimeMillis())
    }

    /** Понятное объяснение кода ошибки распознавания речи. */
    fun explain(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Нет интернета для распознавания речи. Включите интернет или скачайте русский язык для офлайн-распознавания в настройках голосового ввода Google"
        SpeechRecognizer.ERROR_AUDIO -> "Не удалось записать звук: микрофон занят другим приложением"
        SpeechRecognizer.ERROR_SERVER, 11 -> "Служба распознавания речи Google не отвечает"
        SpeechRecognizer.ERROR_CLIENT -> "Сбой распознавания речи, пробую ещё раз"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание речи занято, пробую ещё раз"
        10 -> "Слишком много запросов к распознаванию речи"
        12, 13 -> "Русский язык недоступен для распознавания. Установите его в настройках «Голосовой ввод Google»"
        else -> "Ошибка распознавания речи (код $code)"
    }
}
