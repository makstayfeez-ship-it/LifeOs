package com.lifeos.app.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifeos.app.LifeApp
import com.lifeos.app.R
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.ui.theme.LifeTheme
import com.lifeos.app.ui.theme.LocalAccent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Что можно сделать с напоминанием кнопками: отметить сделанным или отложить. */
object ReminderActions {
    suspend fun markDone(context: Context, id: Long) {
        val app = context.applicationContext
        val task = app.container.dao.taskById(id) ?: return
        app.container.dao.updateTask(task.copy(done = true))
        ReminderScheduler.cancel(app, id)
        cancelNotifications(app, id)
    }

    fun snooze(context: Context, id: Long, title: String, minutes: Int = 10) {
        val app = context.applicationContext
        ReminderScheduler.snooze(app, id, title, minutes)
        cancelNotifications(app, id)
    }

    fun cancelNotifications(context: Context, id: Long) {
        val nm = NotificationManagerCompat.from(context)
        runCatching { nm.cancel(3000 + (id % 1000).toInt()) }
        runCatching { nm.cancel(ReminderAlarm.notifId(id)) }
    }
}

/** Кнопки «Готово» и «Через 10 минут» в обычном уведомлении. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val title = intent.getStringExtra("title").orEmpty()
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> ReminderActions.markDone(app, id)
                    ACTION_SNOOZE -> ReminderActions.snooze(app, id, title)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.lifeos.app.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.lifeos.app.REMINDER_SNOOZE"
    }
}

/**
 * «Необычное» напоминание: полноэкранное окно со звонком, которое вылезает и на заблокированном экране.
 * Работает двумя путями сразу: полноэкранное уведомление и, если выдано «Поверх других приложений», прямой запуск окна.
 */
object ReminderAlarm {
    fun notifId(id: Long) = 4000 + (id % 1000).toInt()

    fun show(app: Context, id: Long, title: String, hidePrivate: Boolean = false) {
        val intent = Intent(app, ReminderAlarmActivity::class.java)
            .putExtra("id", id)
            .putExtra("title", title)
            .putExtra("hide", hidePrivate)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            val pi = PendingIntent.getActivity(
                app,
                7_000_000 + (id % 1_000_000).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(app, LifeApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Напоминание")
                .setContentText(if (hidePrivate) "Откройте, чтобы посмотреть" else title)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(5 * 60_000L)
                .build()
            NotificationManagerCompat.from(app).notify(notifId(id), n)
        }
        if (Settings.canDrawOverlays(app)) runCatching { app.startActivity(intent) }
    }
}

class ReminderAlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stopRinging() }

    private var taskId by mutableStateOf(0L)
    private var title by mutableStateOf("")
    private var hidePrivate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readIntent(intent)
        startRinging()

        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            LifeTheme(settings.accent, settings.glow) {
                AlarmContent(
                    title = if (hidePrivate) "Откройте телефон, чтобы посмотреть" else title,
                    onDone = {
                        stopRinging()
                        val id = taskId
                        CoroutineScope(Dispatchers.Default).launch { ReminderActions.markDone(applicationContext, id) }
                        finish()
                    },
                    onSnooze = {
                        stopRinging()
                        ReminderActions.snooze(applicationContext, taskId, title, 10)
                        finish()
                    },
                    onClose = {
                        stopRinging()
                        ReminderActions.cancelNotifications(applicationContext, taskId)
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        startRinging()
    }

    private fun readIntent(i: Intent?) {
        taskId = i?.getLongExtra("id", 0L) ?: 0L
        title = i?.getStringExtra("title").orEmpty().ifBlank { "Напоминание" }
        hidePrivate = i?.getBooleanExtra("hide", false) ?: false
    }

    private fun startRinging() {
        stopRinging()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        }
        runCatching {
            val v = getSystemService(Vibrator::class.java)
            if (v != null && v.hasVibrator()) {
                val pattern = longArrayOf(0, 700, 500)
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
                vibrator = v
            }
        }
        // Если вы не отреагировали, звонок сам стихнет через две минуты, а обычное уведомление останется
        handler.removeCallbacks(stopper)
        handler.postDelayed(stopper, 120_000L)
    }

    private fun stopRinging() {
        handler.removeCallbacks(stopper)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun AlarmContent(title: String, onDone: () -> Unit, onSnooze: () -> Unit, onClose: () -> Unit) {
    val accent = LocalAccent.current
    val pulse by rememberInfiniteTransition(label = "bell").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "scale",
    )
    val now = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0B0D), Color(0xFF241608)))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(now, color = Color(0xFF9A9AA3), fontSize = 20.sp)
                Box(
                    Modifier.padding(top = 32.dp).size(112.dp).scale(pulse).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Notifications, null, tint = Color(0xFF0B0B0D), modifier = Modifier.size(64.dp)) }
                Text("Напоминание", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp))
                Text(
                    title,
                    color = Color(0xFFF4F4F6),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .clickable { onDone() }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Готово", color = Color(0xFF0B0B0D), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .border(1.5.dp, accent, RoundedCornerShape(50))
                        .clickable { onSnooze() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Напомнить через 10 минут", color = Color(0xFFF4F4F6), fontSize = 17.sp) }
                Box(Modifier.fillMaxWidth().clickable { onClose() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text("Закрыть", color = Color(0xFF9A9AA3), fontSize = 15.sp)
                }
            }
        }
    }
}
