package com.lifeos.app.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.MainActivity
import com.lifeos.app.ui.Perms
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LifeTheme
import com.lifeos.app.ui.theme.Snow
import kotlinx.coroutines.delay

/**
 * Делает LifeOS системным голосовым помощником: когда его выбирают в настройках Android
 * («Приложения по умолчанию → Цифровой помощник»), удержание кнопки питания (или жест от угла экрана) зовёт робота.
 * Это самый надёжный способ вызова: он не зависит от того, слышит ли телефон слово «LifeOS».
 */
class LifeVoiceInteractionService : VoiceInteractionService()

class LifeVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LifeVoiceSession(this)
}

private class LifeVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        SystemAssistant.mark(context, SystemAssistant.KEY_SESSION)
        val intent = Intent(context, AssistLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Окно запускаем из сеанса помощника: так Android разрешает включить микрофон, даже когда экран заблокирован
        val ok = runCatching { startAssistantActivity(intent) }.isSuccess
        if (!ok) {
            runCatching { context.startActivity(intent) }.onFailure { runCatching { AssistantControl.summon(context) } }
        }
    }
}

/**
 * Окно помощника: открывается кнопкой питания, жестом от угла экрана или кнопкой гарнитуры, зовёт робота.
 * Если робот не может появиться поверх экрана (нет разрешения), окно остаётся и объясняет, что делать, чтобы реакция была всегда.
 */
class AssistLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        SystemAssistant.mark(this, SystemAssistant.KEY_ACTIVITY)
        if (!AssistantControl.hasMic(this)) {
            // Нет доступа к микрофону: открываем приложение, оно попросит разрешение
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        AssistantControl.summon(this)
        val overlay = Settings.canDrawOverlays(this)
        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            val skin = remember(settings.skinId) { SkinCatalog.byId(settings.skinId) }
            LaunchedEffect(Unit) {
                // Робот появится поверх экрана сам, это окно можно закрыть. Без разрешения оно остаётся с подсказкой.
                if (overlay) {
                    delay(900)
                    finish()
                }
            }
            LifeTheme(settings.accent, settings.glow) {
                Box(
                    Modifier.fillMaxSize().clickable { finish() }.padding(24.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Bg)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RobotView(skin, RobotMood.LISTENING, Modifier.size(110.dp))
                        Text("LifeOS слушает…", color = Snow, style = MaterialTheme.typography.titleLarge)
                        if (!overlay) {
                            Text(
                                "Чтобы робот появлялся поверх экрана, разрешите «Поверх других приложений». Голосом помощник отвечает и без этого.",
                                color = GrayText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                            )
                            PillButton("Разрешить", {
                                startActivity(Perms.overlaySettings(this@AssistLaunchActivity))
                                finish()
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

object SystemAssistant {
    const val KEY_SESSION = "session"
    const val KEY_ACTIVITY = "activity"
    private const val PREFS = "system_assistant"

    /** Запоминает, когда система позвала помощника: по этому на экране проверки видно, доходит ли вызов кнопкой питания. */
    fun mark(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun last(context: Context, key: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, 0L)

    /** Выбран ли LifeOS цифровым помощником по умолчанию. */
    fun isDefault(context: Context): Boolean = runCatching {
        val cr = context.contentResolver
        listOf("assistant", "voice_interaction_service").any {
            Settings.Secure.getString(cr, it).orEmpty().startsWith(context.packageName + "/")
        }
    }.getOrDefault(false)

    /** Открывает настройки, где выбирается помощник по умолчанию. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
