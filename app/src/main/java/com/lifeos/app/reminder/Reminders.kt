package com.lifeos.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifeos.app.LifeApp
import com.lifeos.app.R
import com.lifeos.app.container
import com.lifeos.app.data.TaskEntity
import com.lifeos.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Ставит будильник-напоминание на каждую задачу с временем, чтобы телефон напомнил в нужную минуту. */
object ReminderScheduler {
    private const val REQUEST_BASE = 100_000

    private fun triggerAt(task: TaskEntity): Long? {
        val minutes = task.timeMinutes ?: return null
        return LocalDate.ofEpochDay(task.epochDay)
            .atTime(LocalTime.of(minutes / 60, minutes % 60))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun pending(context: Context, id: Long, title: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + (id % 1_000_000).toInt(),
            Intent(context, ReminderReceiver::class.java)
                .putExtra("id", id)
                .apply { if (title != null) putExtra("title", title) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(context: Context, task: TaskEntity) {
        cancel(context, task.id)
        if (task.done) return
        val at = triggerAt(task) ?: return
        if (at <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java)
        val op = pending(context, task.id, task.title)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, id, null))
    }

    /** После перезагрузки, обновления или восстановления из облака: заново ставит все будущие напоминания. */
    suspend fun rescheduleAll(context: Context) {
        val tasks = context.container.dao.tasks().first()
        tasks.forEach { schedule(context, it) }
    }
}

/** Срабатывает в назначенное время: показывает уведомление со звуком. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Напоминание" }
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val c = app.container
                val hide = c.settings.current().hideLockNotifs
                val open = PendingIntent.getActivity(
                    app,
                    3,
                    Intent(app, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_ROUTE, "calendar")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(app, LifeApp.CHANNEL_REMINDER)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Напоминание")
                    .setContentText(title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(title))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .apply {
                        if (hide) {
                            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            setPublicVersion(
                                NotificationCompat.Builder(app, LifeApp.CHANNEL_REMINDER)
                                    .setSmallIcon(R.drawable.ic_notification)
                                    .setContentTitle("Напоминание")
                                    .setContentText("Разблокируйте телефон, чтобы посмотреть")
                                    .build(),
                            )
                        }
                    }
                    .build()
                runCatching { NotificationManagerCompat.from(app).notify(3000 + (id % 1000).toInt(), n) }
            } finally {
                pending.finish()
            }
        }
    }
}
