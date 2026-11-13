package com.lifeos.app.brief

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifeos.app.LifeApp
import com.lifeos.app.R
import com.lifeos.app.ui.MainActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LifeTheme
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

/** Запоминает, что утренний обзор готов и ещё не показан на экране блокировки. */
object MorningState {
    private fun prefs(c: Context) = c.getSharedPreferences("morning_state", Context.MODE_PRIVATE)

    /**
     * Обзор готов и ждёт, пока вы включите экран. Обычно ждёт до двух часов дня (но не меньше четырёх часов после сигнала).
     * [forMillis] задаёт срок вручную (для проверки), [resetShown] разрешает показать его ещё раз сегодня.
     */
    fun markPending(c: Context, forMillis: Long? = null, resetShown: Boolean = false) {
        val now = System.currentTimeMillis()
        val until = forMillis?.let { now + it } ?: run {
            val noon = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 14)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            maxOf(noon, now + 4 * 3_600_000L)
        }
        prefs(c).edit().apply {
            putLong("until", until)
            if (resetShown) remove("shown")
        }.apply()
    }

    fun markShown(c: Context) {
        prefs(c).edit().putLong("shown", LocalDate.now().toEpochDay()).remove("until").apply()
    }

    /** Показываем один раз в день: с утреннего сигнала и пока обзор не истёк. */
    fun shouldShow(c: Context): Boolean {
        val p = prefs(c)
        return p.getLong("until", 0) > System.currentTimeMillis() && p.getLong("shown", -1) != LocalDate.now().toEpochDay()
    }
}

/** Поднимает утренний обзор над экраном блокировки всеми способами, какие разрешает Android. */
object MorningLauncher {
    const val FSI_ID = 2003

    fun show(app: Context) {
        val intent = Intent(app, MorningLockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // 1) Полноэкранное уведомление: Android само открывает окно, пока экран выключен или заблокирован
        runCatching { postFullScreen(app, intent) }
        // 2) Прямой запуск, если выдано «Поверх других приложений»
        if (Settings.canDrawOverlays(app)) runCatching { app.startActivity(intent) }
    }

    private fun postFullScreen(app: Context, intent: Intent) {
        val pi = PendingIntent.getActivity(
            app,
            31,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, LifeApp.CHANNEL_BRIEF_LOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Утренний обзор готов")
            .setContentText("Нажмите, чтобы посмотреть день")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        NotificationManagerCompat.from(app).notify(FSI_ID, n)
    }

    fun cancelNotification(app: Context) {
        runCatching { NotificationManagerCompat.from(app).cancel(FSI_ID) }
    }
}

/** Когда включается экран, показывает красивый утренний обзор поверх блокировки (один раз за утро). */
class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return
        val app = context.applicationContext
        if (!MorningState.shouldShow(app)) return
        MorningLauncher.show(app)
    }
}

/**
 * Пока обзор ждёт показа, эта служба слушает включение экрана. Обычный приёмник в приложении для этого не годится:
 * Android выгружает приложение из памяти, и утром слушать уже некому. Служба живёт только от утреннего сигнала до показа обзора.
 */
class MorningWatchService : Service() {
    private var receiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val expire = object : Runnable {
        override fun run() {
            if (!MorningState.shouldShow(this@MorningWatchService)) stopSelf() else handler.postDelayed(this, 10 * 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            val n = notification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        }.isSuccess
        if (!started || !MorningState.shouldShow(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (receiver == null) {
            val r = ScreenOnReceiver()
            ContextCompat.registerReceiver(this, r, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
        }
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 10 * 60_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(expire)
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private fun notification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            32,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_ROUTE, "morning"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, LifeApp.CHANNEL_ASSISTANT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Утренний обзор ждёт вас")
            .setContentText("Появится, когда вы включите экран телефона")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 2004

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MorningWatchService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MorningWatchService::class.java)) }
        }
    }
}

/** Полноэкранный утренний обзор: время, погода, дела дня, привычки и цель. */
class MorningLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        MorningLauncher.cancelNotification(this)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked

        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            var brief by remember { mutableStateOf<Brief?>(null) }
            LaunchedEffect(Unit) {
                val s = container.settings.current()
                val name = container.auth.user.value?.name.orEmpty()
                // Сначала дела и привычки (из памяти телефона, мгновенно), потом добавляем погоду, если интернет ответил
                brief = container.brief.build(name, s.copy(briefWeather = false))
                if (s.briefWeather) {
                    withTimeoutOrNull(6_000) { runCatching { container.brief.build(name, s) }.getOrNull() }?.let { brief = it }
                }
            }
            LifeTheme(settings.accent, settings.glow) {
                MorningLockContent(
                    brief = brief,
                    settings = settings,
                    // Пока телефон заблокирован, личные данные скрываем, если так выбрано в «Безопасности»
                    hidePrivate = locked && settings.hideLockNotifs,
                    onDone = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        // Закрыли обзор (кнопкой или назад): на сегодня он показан, ждать включения экрана больше не нужно
        if (isFinishing) {
            MorningState.markShown(this)
            MorningWatchService.stop(this)
        }
        super.onDestroy()
    }
}

@Composable
private fun MorningLockContent(brief: Brief?, settings: AppSettings, hidePrivate: Boolean, onDone: () -> Unit) {
    val accent = LocalAccent.current
    val now = LocalDateTime.now()
    val dateText = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru")).replaceFirstChar { it.uppercase() } +
        ", " + Fmt.dayMonth(LocalDate.now())
    val skin = remember(settings.skinId) { SkinCatalog.byId(settings.skinId) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.05f),
                        radius = size.width * 1.1f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.95f),
                        radius = size.width * 0.9f,
                    ),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute),
                        color = Snow,
                        fontSize = 64.sp,
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(dateText, color = GrayText, style = MaterialTheme.typography.titleMedium)
                }
                if (settings.robotEnabled) RobotView(skin, RobotMood.HAPPY, Modifier.size(110.dp))
            }

            Text(
                brief?.greeting ?: "Доброе утро!",
                color = Snow,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
            )

            if (brief == null) {
                Text("Собираю обзор…", color = GrayText)
            } else {
                brief.weather?.let { w ->
                    GlassCard {
                        Text("${w.tempC}°", color = Snow, fontSize = 44.sp, style = MaterialTheme.typography.displayMedium)
                        Text(w.description.replaceFirstChar { it.uppercase() }, color = Snow, style = MaterialTheme.typography.titleMedium)
                        Text("Днём от ${w.minC}° до ${w.maxC}°", color = GrayText)
                        brief.weatherNote?.let { Text(it, color = accent, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }

                if (hidePrivate) {
                    GlassCard {
                        Text("Разблокируйте телефон, чтобы увидеть дела, расходы и цель", color = GrayText)
                    }
                } else {
                    if (settings.briefEvents && brief.events.isNotEmpty()) {
                        GlassCard {
                            Text("События", color = accent, style = MaterialTheme.typography.titleMedium)
                            brief.events.forEach { Text("• $it", color = Snow, modifier = Modifier.padding(top = 4.dp)) }
                        }
                    }
                    if (settings.briefTasks && brief.tasks.isNotEmpty()) {
                        GlassCard {
                            Text("Сегодня нужно", color = accent, style = MaterialTheme.typography.titleMedium)
                            brief.tasks.forEach { Text("• $it", color = Snow, modifier = Modifier.padding(top = 4.dp)) }
                        }
                    }
                    if (brief.events.isEmpty() && brief.tasks.isEmpty()) {
                        GlassCard { Text("На сегодня дел нет. Хороший день, чтобы заняться собой.", color = Snow) }
                    }
                    brief.spentYesterday?.let {
                        GlassCard {
                            Text("Вчера потрачено", color = accent, style = MaterialTheme.typography.titleMedium)
                            Text(Fmt.money(it), color = Snow, fontSize = 22.sp)
                        }
                    }
                    if (brief.habitsTotal != null && brief.habitsTotal > 0) {
                        GlassCard {
                            Text("Привычки", color = accent, style = MaterialTheme.typography.titleMedium)
                            Text("Сегодня выполнено ${brief.habitsDone} из ${brief.habitsTotal}", color = Snow)
                        }
                    }
                    if (brief.goalTitle != null && brief.goalProgress != null) {
                        GlassCard {
                            Text("Цель на год", color = accent, style = MaterialTheme.typography.titleMedium)
                            Text("${brief.goalTitle}: ${brief.goalProgress}%", color = Snow)
                        }
                    }
                }
            }

            PillButton("Начать день", onDone, Modifier.fillMaxWidth().padding(top = 18.dp))
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x33FFFFFF))
            .padding(18.dp),
    ) { content() }
}
