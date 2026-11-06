package com.lifeos.app.brief

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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

/** Запоминает, что утренний обзор готов и ещё не показан на экране блокировки. */
object MorningState {
    private fun prefs(c: Context) = c.getSharedPreferences("morning_state", Context.MODE_PRIVATE)

    fun markPending(c: Context) {
        prefs(c).edit().putLong("pending", LocalDate.now().toEpochDay()).apply()
    }

    fun markShown(c: Context) {
        prefs(c).edit().putLong("shown", LocalDate.now().toEpochDay()).apply()
    }

    /** Показываем один раз в день, с момента утреннего сигнала и до двух часов дня. */
    fun shouldShow(c: Context): Boolean {
        val today = LocalDate.now().toEpochDay()
        val p = prefs(c)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return p.getLong("pending", -1) == today && p.getLong("shown", -1) != today && hour < 14
    }
}

/** Когда включается экран, показывает красивый утренний обзор поверх блокировки (один раз за утро). */
class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return
        val app = context.applicationContext
        if (!MorningState.shouldShow(app)) return
        // Запуск окна из фона разрешён, когда выдано «Поверх других приложений»
        if (!Settings.canDrawOverlays(app)) return
        runCatching {
            app.startActivity(Intent(app, MorningLockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Полноэкранный утренний обзор: время, погода, дела дня, привычки и цель. */
class MorningLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked

        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            var brief by remember { mutableStateOf<Brief?>(null) }
            LaunchedEffect(Unit) {
                val s = container.settings.current()
                val name = container.auth.user.value?.name.orEmpty()
                brief = container.brief.build(name, s)
            }
            LifeTheme(settings.accent, settings.glow) {
                MorningLockContent(
                    brief = brief,
                    settings = settings,
                    // Пока телефон заблокирован, личные данные скрываем, если так выбрано в «Безопасности»
                    hidePrivate = locked && settings.hideLockNotifs,
                    onDone = {
                        MorningState.markShown(this@MorningLockActivity)
                        finish()
                    },
                )
            }
        }
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
