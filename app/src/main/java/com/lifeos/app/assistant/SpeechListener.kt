package com.lifeos.app.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer

sealed interface SpeechResult {
    data class Heard(val text: String) : SpeechResult

    /** Ничего не сказали или не разобрали — это обычная ситуация, а не сбой. */
    data object Silence : SpeechResult

    data class Error(val code: Int) : SpeechResult {
        /** Без этого дальше слушать бессмысленно. */
        val fatal: Boolean
            get() = code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || code == SpeechListener.NO_RECOGNIZER
    }
}

/** Обёртка над SpeechRecognizer. Он работает только в главном потоке, поэтому всё идёт через Handler. */
class SpeechListener(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private fun googleAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Есть ли хоть один способ распознать речь: офлайн-модель или служба Google. */
    fun available(): Boolean = VoskModel.isReady(context) || googleAvailable()

    /** Слушает одну фразу офлайн-движком, когда модель скачана (нужно для вечного слушания: без писков и интернета). */
    val offline: Boolean get() = VoskModel.isReady(context)

    /**
     * Слушает одну фразу. Отмена корутины останавливает распознавание.
     * Для постоянного ожидания «LifeOS» берётся офлайн-движок, если он есть. Для диктовки ([accurate]) лучше Google,
     * а если он не работает, слушает офлайн-движок.
     */
    suspend fun listen(accurate: Boolean = false, onPartial: (String) -> Unit = {}): SpeechResult {
        val vosk = VoskModel.isReady(context)
        val google = googleAvailable()
        return when {
            vosk && (!accurate || !google) -> listenOffline(onPartial)
            google -> {
                val r = listenGoogle(onPartial)
                if (r is SpeechResult.Error && vosk) listenOffline(onPartial) else r
            }
            else -> {
                AssistantStatus.error("Нечем распознавать речь: скачайте офлайн-распознавание в «Проверке помощника» или установите приложение Google")
                SpeechResult.Error(NO_RECOGNIZER)
            }
        }
    }

    /** Офлайн-распознавание Vosk: пишет звук с микрофона и ждёт конца фразы. Ничего не пищит и не требует интернета. */
    private suspend fun listenOffline(onPartial: (String) -> Unit): SpeechResult = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext SpeechResult.Error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        }
        val model = VoskModel.model(context) ?: return@withContext SpeechResult.Error(NO_RECOGNIZER)
        val rate = 16_000
        val chunk = rate / 5
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, chunk * 2) * 2,
            )
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            return@withContext SpeechResult.Error(SpeechRecognizer.ERROR_AUDIO)
        }
        val recognizer = runCatching { Recognizer(model, rate.toFloat()) }.getOrNull()
        if (recognizer == null) {
            rec.release()
            return@withContext SpeechResult.Error(NO_RECOGNIZER)
        }
        try {
            rec.startRecording()
            val buf = ShortArray(chunk)
            val started = SystemClock.elapsedRealtime()
            var spoke = false
            var lastPartial = ""
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) return@withContext SpeechResult.Error(SpeechRecognizer.ERROR_AUDIO)
                if (n == 0) continue
                if (recognizer.acceptWaveForm(buf, n)) {
                    val text = JSONObject(recognizer.result).optString("text").trim()
                    return@withContext if (text.isBlank()) SpeechResult.Silence else SpeechResult.Heard(text)
                }
                val partial = runCatching { JSONObject(recognizer.partialResult).optString("partial").trim() }.getOrDefault("")
                if (partial.isNotBlank()) {
                    spoke = true
                    if (partial != lastPartial) {
                        lastPartial = partial
                        onPartial(partial)
                    }
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                // Молчат: возвращаем тишину, чтобы цикл мог проверить, не пора ли остановиться
                if (!spoke && elapsed > NO_SPEECH_MS) return@withContext SpeechResult.Silence
                // Слишком длинная фраза: забираем то, что успели услышать
                if (elapsed > MAX_UTTERANCE_MS) {
                    val text = JSONObject(recognizer.finalResult).optString("text").trim()
                    return@withContext if (text.isBlank()) SpeechResult.Silence else SpeechResult.Heard(text)
                }
            }
            SpeechResult.Silence
        } catch (e: Exception) {
            SpeechResult.Error(SpeechRecognizer.ERROR_CLIENT)
        } finally {
            runCatching { rec.stop() }
            rec.release()
            runCatching { recognizer.close() }
        }
    }

    private suspend fun listenGoogle(onPartial: (String) -> Unit): SpeechResult =
        suspendCancellableCoroutine { cont ->
            main.post {
                if (!cont.isActive) return@post
                releaseNow()
                if (!googleAvailable()) {
                    AssistantStatus.error("На телефоне нет службы распознавания речи. Скачайте офлайн-распознавание в «Проверке помощника»")
                    cont.resume(SpeechResult.Error(NO_RECOGNIZER))
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
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                r.startListening(intent)
            }
            cont.invokeOnCancellation { main.post { releaseNow() } }
        }

    fun cancel() {
        main.post { releaseNow() }
    }

    companion object {
        /** Нашего собственного кода: на телефоне нет службы распознавания. Дальше слушать бессмысленно. */
        const val NO_RECOGNIZER = -100
        private const val NO_SPEECH_MS = 8_000L
        private const val MAX_UTTERANCE_MS = 25_000L
    }

    private fun releaseNow() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }
}
