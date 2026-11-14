package com.lifeos.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.util.UUID

/** Запись голосовой заметки в файл (AAC/m4a) во внутренней памяти приложения. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val recording: Boolean get() = recorder != null

    fun start(): Boolean {
        stopQuietly()
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val f = File(dir, UUID.randomUUID().toString() + ".m4a")
        val r = MediaRecorder(context)
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    /** Текущая громкость 0..1 для волны на экране. */
    fun level(): Float = ((recorder?.let { runCatching { it.maxAmplitude }.getOrDefault(0) } ?: 0) / 20000f).coerceIn(0f, 1f)

    /** Останавливает запись. Возвращает имя файла или null, если запись не получилась. */
    fun stop(): String? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || f == null) {
            f?.delete()
            return null
        }
        return f.name
    }

    fun release() {
        stopQuietly()
    }

    private fun stopQuietly() {
        val name = stop()
        if (name != null) File(File(context.filesDir, "voice"), name).delete()
    }

    companion object {
        fun file(context: Context, name: String?): File? =
            name?.takeIf { it.isNotBlank() }?.let { File(File(context.filesDir, "voice"), it) }?.takeIf { it.exists() }

        fun delete(context: Context, name: String?) {
            file(context, name)?.delete()
        }
    }
}

/** Воспроизведение записанных заметок: одна запись за раз. */
class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(name: String, onDone: () -> Unit): Boolean {
        stop()
        val f = VoiceRecorder.file(context, name) ?: return false
        return try {
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener {
                stop()
                onDone()
            }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}
