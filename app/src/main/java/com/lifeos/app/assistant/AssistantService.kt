package com.lifeos.app.assistant

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lifeos.app.LifeApp
import com.lifeos.app.R
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.NoteEntity
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.widget.WidgetUpdater
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Включает/выключает помощника из экранов приложения. */
object AssistantControl {
    fun startListening(context: Context) = start(context, AssistantService.ACTION_WAKE)

    /** Позвать робота прямо сейчас (кнопка в приложении). */
    fun summon(context: Context) = start(context, AssistantService.ACTION_SUMMON)

    fun stop(context: Context) {
        context.stopService(Intent(context, AssistantService::class.java))
    }

    fun hasMic(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun start(context: Context, action: String) {
        if (!hasMic(context)) return
        val intent = Intent(context, AssistantService::class.java).setAction(action)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }
}

/**
 * Фоновый помощник. Слушает микрофон, и когда слышит «LifeOS …», «OS …» или имя, которое дал
 * пользователь, показывает робота, выполняет команду и отвечает голосом. Работает и с выключенным
 * экраном. Пока пользователь не позвал, на экране робота нет.
 */
class AssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var listener: SpeechListener
    private lateinit var overlay: RobotOverlay
    private lateinit var executor: CommandExecutor

    private var mainJob: Job? = null
    private var sessionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        listener = SpeechListener(this)
        overlay = RobotOverlay(this)
        executor = CommandExecutor(this)
        overlay.onTap = {
            // Нажали на робота: он замолкает и уходит, но фоновое прослушивание продолжается
            container.tts.stop()
            listener.cancel()
            sessionJob?.cancel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !AssistantControl.hasMic(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        val summon = intent?.action == ACTION_SUMMON
        mainJob?.cancel()
        listener.cancel()
        mainJob = scope.launch { mainLoop(summon) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        listener.cancel()
        container.tts.stop()
        overlay.release()
        super.onDestroy()
    }

    // ---------- основной цикл ----------

    private suspend fun mainLoop(summonFirst: Boolean) {
        var summon = summonFirst
        while (currentCoroutineContext().isActive) {
            val s = container.settings.current()
            if (summon) {
                summon = false
                runSession(s, "")
                if (!s.wakeEnabled) {
                    stopSelf()
                    return
                }
                continue
            }
            if (!s.wakeEnabled) {
                stopSelf()
                return
            }
            when (val r = listener.listen()) {
                is SpeechResult.Heard -> {
                    val note = CommandParser.noteTrigger(r.text)
                    if (note != null) {
                        // «Сделать заметку …» без «LifeOS»: без робота и без экрана, сразу в запись
                        quickNote(s, note)
                    } else {
                        val rest = CommandParser.stripWakeWord(r.text, listOf(s.assistantName))
                        if (rest != null) runSession(s, rest)
                    }
                }
                SpeechResult.Silence -> delay(250)
                is SpeechResult.Error -> {
                    if (r.fatal) {
                        stopSelf()
                        return
                    }
                    delay(1500)
                }
            }
        }
    }

    /** Быстрая заметка голосом: сигнал, диктовка до паузы, потом текст сохраняется в заметки. Экран не нужен. */
    private suspend fun quickNote(s: AppSettings, inline: String) {
        var text = inline
        val dictate = text.isBlank()
        if (dictate) {
            Beeps.start()
            text = Dictation.record(listener)
        }
        if (text.isBlank()) {
            withTimeoutOrNull(10_000) { container.tts.speakAndWait("Ничего не слышно, заметка не записана", s) }
            return
        }
        saveNote(text)
        if (dictate) Beeps.done()
        withTimeoutOrNull(10_000) { container.tts.speakAndWait("Заметка записана", s) }
    }

    private suspend fun saveNote(text: String) {
        container.dao.insertNote(NoteEntity(text = text, isVoice = true))
        WidgetUpdater.refresh(this)
    }

    private suspend fun runSession(s: AppSettings, first: String) = coroutineScope {
        val job = launch { session(s, first) }
        sessionJob = job
        job.join()
        sessionJob = null
    }

    private suspend fun session(s: AppSettings, first: String) {
        val canShow = s.robotEnabled && Settings.canDrawOverlays(this)
        if (canShow) overlay.show(SkinCatalog.byId(s.skinId))
        overlay.setMood(RobotMood.LISTENING)
        try {
            var text = first
            val history = mutableListOf<Pair<String, String>>()
            if (text.isBlank()) say("Слушаю", s, RobotMood.TALKING)

            // Если молчать, робот уходит сам, и его нужно позвать заново. В разговоре с ИИ он остаётся,
            // пока вы отвечаете.
            while (currentCoroutineContext().isActive) {
                if (text.isBlank()) {
                    overlay.setMood(RobotMood.LISTENING)
                    text = hear()
                    if (text.isBlank()) break
                }

                val cmd = CommandParser.parse(text, nowMinutes())
                if (cmd is Command.Dismiss) {
                    say("Хорошо, я рядом", s, RobotMood.HAPPY)
                    break
                }

                // «Заметка»: сигнал, диктовка, запись текстом
                if (cmd is Command.Clarify && cmd.kind == "заметка") {
                    overlay.setMood(RobotMood.LISTENING)
                    overlay.say("Записываю… скажите «готово», когда закончите")
                    Beeps.start()
                    val note = Dictation.record(listener) { overlay.say(it) }
                    if (note.isBlank()) {
                        say("Ничего не слышно", s, RobotMood.THINKING)
                    } else {
                        saveNote(note)
                        Beeps.done()
                        say("Заметка записана", s, RobotMood.HAPPY)
                    }
                    break
                }

                val working = cmd is Command.SetAlarm || cmd is Command.SetTimer
                overlay.setMood(if (working) RobotMood.WORKING else RobotMood.THINKING)
                overlay.say(
                    when (cmd) {
                        is Command.SetAlarm -> "Ставлю будильник…"
                        is Command.SetTimer -> "Запускаю таймер…"
                        else -> "Думаю…"
                    },
                )
                // Короткая анимация «работы»: робот с часами в руках, потом команда выполняется по-настоящему
                if (working) delay(1500)

                val res = executor.run(cmd, s, history)
                history += "user" to text
                history += "assistant" to res.reply
                say(res.reply, s, RobotMood.TALKING)

                text = ""
                if (res.followUp != null) {
                    overlay.setMood(RobotMood.LISTENING)
                    val answer = hear()
                    if (answer.isBlank()) break
                    text = "${res.followUp} $answer"
                    continue
                }
                // В обычном разговоре можно отвечать без повторного «LifeOS»; после команды робот уходит
                if (cmd !is Command.Chat) break
            }
            overlay.setMood(RobotMood.HAPPY)
            delay(1200)
        } finally {
            overlay.hide()
        }
    }

    private suspend fun say(text: String, s: AppSettings, mood: RobotMood) {
        overlay.setMood(mood)
        overlay.say(text)
        withTimeoutOrNull(30_000) { container.tts.speakAndWait(text, s) }
        if (mood == RobotMood.TALKING) overlay.setMood(RobotMood.HAPPY)
    }

    /** Слушает фразу, показывая распознанное над роботом. Пустая строка — ничего не сказали. */
    private suspend fun hear(): String =
        when (val r = listener.listen { overlay.say(it) }) {
            is SpeechResult.Heard -> r.text
            else -> ""
        }

    private fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val note = PendingIntent.getActivity(
            this,
            2,
            Intent(this, QuickNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, LifeApp.CHANNEL_ASSISTANT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LifeOS слушает")
            .setContentText("Скажите «LifeOS» или «OS», чтобы позвать помощника")
            .setContentIntent(open)
            .addAction(0, "Заметка", note)
            .addAction(0, "Выключить", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_WAKE = "com.lifeos.app.WAKE"
        const val ACTION_SUMMON = "com.lifeos.app.SUMMON"
        const val ACTION_STOP = "com.lifeos.app.STOP"
        private const val NOTIF_ID = 1001
    }
}
