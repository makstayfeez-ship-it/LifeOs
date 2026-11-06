package com.lifeos.app.brief

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifeos.app.LifeApp
import com.lifeos.app.R
import com.lifeos.app.container
import com.lifeos.app.ui.MainActivity
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Ставит и снимает ежедневный будильник утреннего обзора. */
object BriefScheduler {
    private const val REQUEST = 4101

    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val at = nextTrigger(hour, minute)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    /** Ближайший момент hour:minute: сегодня, если ещё не прошло, иначе завтра. */
    fun nextTrigger(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, MorningBriefReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Срабатывает в выбранное утреннее время: собирает сводку, показывает уведомление и (по желанию) озвучивает. */
class MorningBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val c = app.container
                val s = c.settings.current()
                if (!s.morningEnabled) return@launch
                // Следующий запуск ставим сразу, чтобы сбой в сборке сводки не оборвал расписание
                BriefScheduler.schedule(app, s.morningHour, s.morningMinute)

                val name = c.auth.user.value?.name.orEmpty()
                val brief = withTimeoutOrNull(8_000) { c.brief.build(name, s) } ?: return@launch
                notify(app, brief, s.hideLockNotifs)
                // Красивый обзор покажется на экране, когда вы включите телефон
                if (s.briefOnLock) MorningState.markPending(app)
                if (s.speakBrief) c.tts.speak(brief.spoken(), s)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, brief: Brief, hideOnLock: Boolean) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, "morning")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lines = brief.lines().ifEmpty { listOf("На сегодня всё спокойно") }
        val n = NotificationCompat.Builder(context, LifeApp.CHANNEL_BRIEF)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(brief.greeting)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                if (hideOnLock) {
                    // На заблокированном экране вместо дел и сумм показывается нейтральный текст
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, LifeApp.CHANNEL_BRIEF)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("Утренний обзор")
                            .setContentText("Разблокируйте телефон, чтобы посмотреть")
                            .build(),
                    )
                }
            }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(2001, n) }
    }
}

/** После перезагрузки телефона или обновления приложения возвращает утренний обзор в расписание. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = app.container.settings.current()
                if (s.morningEnabled) BriefScheduler.schedule(app, s.morningHour, s.morningMinute)
                com.lifeos.app.reminder.ReminderScheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
