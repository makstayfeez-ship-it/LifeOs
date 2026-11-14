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
import android.os.PowerManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Включает/выключает помощника из экранов приложения. */
object AssistantControl {
    fun startListening(context: Context) = start(context, AssistantService.ACTION_WAKE)

    /** Позвать робота прямо сейчас (кнопка в приложении). */
    fun summon(context: Context) = start(context, AssistantService.ACTION_SUMMON)

    /** Сразу открыть напоминалку: «что и когда напомнить?» (плитка в шторке, кнопка в уведомлении). */
    fun remind(context: Context) = start(context, AssistantService.ACTION_REMIND)

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
    private var wakeLock: PowerManager.WakeLock? = null
    private val notepad by lazy { NotepadOverlay(this) }
    private val reminderCard by lazy { ReminderOverlay(this) }

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
            AssistantStatus.error("Android не разрешил запустить микрофонную службу. Откройте LifeOS и включите прослушивание заново")
            stopSelf()
            return START_NOT_STICKY
        }
        // С выключенным экраном процессор засыпает, и помощник перестаёт слышать. Держим его бодрствующим.
        if (wakeLock?.isHeld != true) {
            wakeLock = runCatching {
                getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeOS:listen")
                    .apply { acquire() }
            }.getOrNull()
        }
        AssistantStatus.running(true)
        val initial = when (intent?.action) {
            ACTION_SUMMON -> INIT_SUMMON
            ACTION_REMIND -> INIT_REMIND
            else -> INIT_NONE
        }
        mainJob?.cancel()
        listener.cancel()
        mainJob = scope.launch { mainLoop(initial) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AssistantStatus.running(false)
        notepad.hide()
        reminderCard.hide()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        scope.cancel()
        listener.cancel()
        container.tts.stop()
        overlay.release()
        super.onDestroy()
    }

    // ---------- основной цикл ----------

    private suspend fun mainLoop(initial: Int) {
        var pending = initial
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val s = container.settings.current()
            if (pending != INIT_NONE) {
                val what = pending
                pending = INIT_NONE
                if (what == INIT_REMIND) quickReminder(s, "") else runSession(s, "")
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
            // Постоянно слушать через службу Google нельзя: она пищит при каждом перезапуске. Ждём офлайн-модель.
            if (!listener.offline && !s.googleWake) {
                AssistantStatus.error("Постоянное прослушивание выключено, пока не скачана офлайн-модель речи (иначе будет пищать). Скачайте её в «Проверке помощника»")
                stopSelf()
                return
            }
            // null: микрофон понадобился окну заметки или разговора, ждём, пока оно закончит
            val r = listenWake() ?: continue
            when (r) {
                is SpeechResult.Heard -> {
                    failures = 0
                    AssistantStatus.heard(r.text)
                    val note = CommandParser.noteTrigger(r.text)
                    val remind = if (note == null) CommandParser.reminderTrigger(r.text) else null
                    if (note != null) {
                        // «Сделать заметку …» без «LifeOS»: без робота и без экрана, сразу в запись
                        quickNote(s, note)
                    } else if (remind != null) {
                        // «Напомни …» без «LifeOS»: карточка напоминания, дальше разбираем время и дело
                        quickReminder(s, remind)
                    } else {
                        val rest = CommandParser.stripWakeWord(r.text, listOf(s.assistantName))
                        if (rest != null) runSession(s, rest)
                    }
                }
                SpeechResult.Silence -> {
                    failures = 0
                    delay(if (listener.offline) 20L else 250L)
                }
                is SpeechResult.Error -> {
                    if (r.code != SpeechListener.NO_RECOGNIZER) AssistantStatus.error(AssistantStatus.explain(r.code))
                    failures++
                    // Много ошибок подряд: останавливаемся, чтобы не разряжать батарею впустую; причина видна в «Проверке помощника»
                    if (r.fatal || failures >= 12) {
                        stopSelf()
                        return
                    }
                    delay(if (failures < 4) 1500L else 5000L)
                }
            }
        }
    }

    /**
     * Фоновое ожидание «LifeOS». Слушает без перезапусков (только раз в минуту для порядка), чтобы не терять начало слов
     * в паузах между запусками микрофона. Если окну заметки или разговору нужен микрофон, сразу отпускает его.
     */
    private suspend fun listenWake(): SpeechResult? {
        if (MicArbiter.isBusy) {
            MicArbiter.busy.first { !it }
            return null
        }
        return coroutineScope {
            val job = async { listener.listen(idleMs = WAKE_IDLE_MS) }
            val watcher = launch {
                MicArbiter.busy.first { it }
                job.cancel()
            }
            try {
                job.await()
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                null
            } finally {
                watcher.cancel()
            }
        }
    }

    /** Быстрая заметка голосом: сигнал, диктовка до паузы, потом текст сохраняется в заметки. Экран не нужен. */
    private suspend fun quickNote(s: AppSettings, inline: String) {
        var text = inline
        if (text.isBlank()) text = dictateNote(s)
        else saveNote(text)
        if (text.isBlank()) {
            withTimeoutOrNull(10_000) { container.tts.speakAndWait("Ничего не слышно, заметка не записана", s) }
            return
        }
        withTimeoutOrNull(10_000) { container.tts.speakAndWait("Заметка записана", s) }
    }

    /**
     * Диктовка с блокнотом: на экране появляется листок, слова вписываются по мере речи, а слово «остановить»
     * (или кнопка «Сохранить») закрывает запись, и заметка сохраняется. Без разрешения «Поверх других приложений» то же самое без листка.
     */
    private suspend fun dictateNote(s: AppSettings): String {
        val canShow = s.robotEnabled && Settings.canDrawOverlays(this)
        if (canShow) notepad.show(SkinCatalog.byId(s.skinId))
        // Сигнал «говорите» звучит, когда микрофон правда включился, а не раньше: иначе начало фразы пропадёт
        val d = DictationSession(
            listener,
            accurate = s.googleDictation,
            onReady = {
                Beeps.ready()
                notepad.ready()
            },
        )
        coroutineScope {
            val job = launch { d.run { notepad.update(it) } }
            notepad.onStop = { job.cancel() }
            job.join()
        }
        notepad.onStop = {}
        val text = d.text
        if (text.isNotBlank()) {
            saveNote(text)
            Beeps.done()
        }
        if (canShow) {
            notepad.update(text)
            notepad.finish(text.isNotBlank())
            delay(1300)
            notepad.hide()
        }
        return text
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
            // Раньше тут звучало «Слушаю», и пока оно говорилось, микрофон был закрыт: вы уже говорите, а вас не слышат.
            // Теперь сначала включается микрофон, и только потом короткий сигнал.

            // Если молчать, робот уходит сам, и его нужно позвать заново. В разговоре с ИИ он остаётся,
            // пока вы отвечаете.
            while (currentCoroutineContext().isActive) {
                if (text.isBlank()) {
                    overlay.setMood(RobotMood.LISTENING)
                    text = hear(s, retryOnSilence = true)
                    if (text.isBlank()) break
                }

                val cmd = CommandParser.parse(text, nowMinutes())
                if (cmd is Command.Dismiss) {
                    say("Хорошо, я рядом", s, RobotMood.HAPPY)
                    break
                }

                // «Заметка»: сигнал, диктовка, запись текстом
                if (cmd is Command.Clarify && cmd.kind == "заметка") {
                    // Робот уступает место блокноту, в котором он сам сидит с карандашом
                    overlay.hide()
                    val note = dictateNote(s)
                    val phrase = if (note.isBlank()) "Ничего не слышно" else "Заметка записана"
                    withTimeoutOrNull(10_000) { container.tts.speakAndWait(phrase, s) }
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
                    val answer = hear(s, retryOnSilence = true)
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

    /**
     * Слушает фразу, показывая распознанное над роботом. Пустая строка — ничего не сказали.
     * Сначала открывается микрофон, потом звучит короткий сигнал и появляется «Говорите…». Если вас не слышно,
     * помощник один раз переспрашивает, а не молчит.
     */
    private suspend fun hear(s: AppSettings, retryOnSilence: Boolean): String {
        overlay.say("Включаю микрофон…")
        return listenPhrase(
            s,
            retries = if (retryOnSilence) 1 else 0,
            onPartial = { overlay.say(it) },
            onReady = {
                Beeps.ready()
                overlay.say("Говорите…")
            },
            onRetry = { overlay.say("Не слышу вас. Говорите ближе к микрофону…") },
        )
    }

    /** Одна фраза с повторными попытками: тишина или сбой микрофона не должны сразу заканчивать разговор. */
    private suspend fun listenPhrase(
        s: AppSettings,
        retries: Int,
        onPartial: (String) -> Unit,
        onReady: () -> Unit,
        onRetry: () -> Unit = {},
    ): String {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val r = listener.listen(accurate = s.googleDictation, onPartial = onPartial, onReady = onReady)
            when (r) {
                is SpeechResult.Heard -> return r.text
                SpeechResult.Silence -> Unit
                is SpeechResult.Error -> {
                    if (r.code != SpeechListener.NO_RECOGNIZER) AssistantStatus.error(AssistantStatus.explain(r.code))
                    if (r.fatal) return ""
                    delay(300)
                }
            }
            if (attempt++ >= retries) return ""
            onRetry()
        }
        return ""
    }

    // ---------- напоминалка ----------

    /**
     * «Напомни завтра в 15:00 сходить к стоматологу»: открывается карточка напоминания, фраза разбирается на день,
     * время и дело. Если чего-то не хватает, помощник спрашивает. Перед сохранением карточка показывает итог.
     */
    private suspend fun quickReminder(s: AppSettings, inline: String) {
        val canShow = Settings.canDrawOverlays(this)
        if (canShow) reminderCard.show(SkinCatalog.byId(s.skinId))
        try {
            var known = ""
            var heard = inline
            var question = ""
            var task: Command.AddTask? = null
            var failure: String? = null
            var rounds = 0
            while (task == null && failure == null && currentCoroutineContext().isActive) {
                if (++rounds > 3) {
                    failure = "Не получилось понять напоминание. Скажите, например: «напомни завтра в 15:00 сходить к стоматологу»"
                    break
                }
                if (heard.isBlank()) {
                    reminderCard.preparing()
                    if (question.isNotBlank() && !canShow) withTimeoutOrNull(10_000) { container.tts.speakAndWait(question, s) }
                    heard = listenPhrase(
                        s,
                        retries = 1,
                        onPartial = { reminderCard.listening((known + " " + it).trim(), question) },
                        onReady = {
                            Beeps.ready()
                            reminderCard.listening(known, question)
                        },
                    )
                    if (heard.isBlank()) {
                        failure = "Ничего не слышно, напоминание не создано"
                        break
                    }
                }
                val full = (known + " " + heard).trim()
                reminderCard.listening(full, question)
                when (val parsed = CommandParser.parse("напомни $full", nowMinutes())) {
                    is Command.AddTask -> if (parsed.timeMinutes == null) {
                        known = full
                        question = "Во сколько напомнить?"
                        heard = ""
                    } else {
                        task = parsed
                    }
                    is Command.Clarify -> {
                        known = full
                        question = parsed.question
                        heard = ""
                    }
                    else -> failure = "Не понял, о чём напомнить"
                }
            }

            val t = task
            if (t == null) {
                reminderCard.fail(failure ?: "Напоминание не создано")
                if (failure != null) withTimeoutOrNull(10_000) { container.tts.speakAndWait(failure, s) }
                delay(1200)
                return
            }

            // Итог на карточке: можно сразу сохранить или отменить, без ответа сохранится само через пару секунд
            var save = true
            if (canShow) {
                val decision = CompletableDeferred<Boolean>()
                reminderCard.onSave = { decision.complete(true) }
                reminderCard.onCancel = { decision.complete(false) }
                reminderCard.confirm(whenLabel(t), t.title)
                save = withTimeoutOrNull(3_500) { decision.await() } ?: true
            }
            if (!save) {
                reminderCard.fail("Отменено")
                delay(700)
                return
            }
            val res = executor.run(t, s, emptyList())
            Beeps.done()
            reminderCard.done(whenLabel(t), t.title)
            withTimeoutOrNull(10_000) { container.tts.speakAndWait(res.reply, s) }
            delay(900)
        } finally {
            reminderCard.hide()
        }
    }

    private fun whenLabel(t: Command.AddTask): String {
        val day = when (t.dayOffset) {
            0 -> "Сегодня"
            1 -> "Завтра"
            2 -> "Послезавтра"
            else -> com.lifeos.app.ui.Fmt.dayMonth(java.time.LocalDate.now().plusDays(t.dayOffset.toLong()))
        }
        val tm = t.timeMinutes?.let { String.format(java.util.Locale.ROOT, "%02d:%02d", it / 60, it % 60) }
        return if (tm != null) "$day, $tm" else day
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
        val remind = PendingIntent.getForegroundService(
            this,
            3,
            Intent(this, AssistantService::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, LifeApp.CHANNEL_ASSISTANT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LifeOS слушает")
            .setContentText("Скажите «LifeOS», «заметка» или «напомни…»")
            .setContentIntent(open)
            .addAction(0, "Заметка", note)
            .addAction(0, "Напомнить", remind)
            .addAction(0, "Выключить", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_WAKE = "com.lifeos.app.WAKE"
        const val ACTION_SUMMON = "com.lifeos.app.SUMMON"
        const val ACTION_STOP = "com.lifeos.app.STOP"
        const val ACTION_REMIND = "com.lifeos.app.REMIND"
        private const val NOTIF_ID = 1001
        private const val INIT_NONE = 0
        private const val INIT_SUMMON = 1
        private const val INIT_REMIND = 2

        /** Раз в минуту тишины микрофон ожидания перезапускается: так он не копит лишнего. */
        private const val WAKE_IDLE_MS = 60_000L
    }
}
