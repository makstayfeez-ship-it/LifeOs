package com.lifeos.app.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface SpeechResult {
    data class Heard(val text: String) : SpeechResult

    /** Ничего не сказали или не разобрали — это обычная ситуация, а не сбой. */
    data object Silence : SpeechResult

    data class Error(val code: Int) : SpeechResult {
        /** Без этого дальше слушать бессмысленно. */
        val fatal: Boolean
            get() = code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                code == SpeechRecognizer.ERROR_CLIENT
    }
}

/** Обёртка над SpeechRecognizer. Он работает только в главном потоке, поэтому всё идёт через Handler. */
class SpeechListener(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Слушает одну фразу. Отмена корутины останавливает распознавание. */
    suspend fun listen(onPartial: (String) -> Unit = {}): SpeechResult =
        suspendCancellableCoroutine { cont ->
            main.post {
                if (!cont.isActive) return@post
                releaseNow()
                if (!available()) {
                    cont.resume(SpeechResult.Error(SpeechRecognizer.ERROR_CLIENT))
                    return@post
                }
                val r = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = r

                fun finish(result: SpeechResult) {
                    if (cont.isActive) cont.resume(result)
                    main.post { if (recognizer === r) releaseNow() }
                }

                r.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!t.isNullOrBlank()) onPartial(t)
                    }

                    override fun onResults(results: Bundle?) {
                        val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        finish(if (t.isNullOrBlank()) SpeechResult.Silence else SpeechResult.Heard(t))
                    }

                    override fun onError(error: Int) {
                        finish(
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechResult.Silence
                                else -> SpeechResult.Error(error)
                            },
                        )
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                r.startListening(intent)
            }
            cont.invokeOnCancellation { main.post { releaseNow() } }
        }

    fun cancel() {
        main.post { releaseNow() }
    }

    private fun releaseNow() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }
}
