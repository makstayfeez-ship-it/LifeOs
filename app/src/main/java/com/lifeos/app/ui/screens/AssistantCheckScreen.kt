package com.lifeos.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.AssistantStatus
import com.lifeos.app.assistant.QuickNoteActivity
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Perms
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.rememberMicRequest
import com.lifeos.app.ui.rememberResumeTick
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow

private const val OK_COLOR = 0xFF4CD964
private const val BAD_COLOR = 0xFFFF5A4F

/** Сколько обязательных условий для работы помощника сейчас не выполнено (для баннера на главном экране). */
fun assistantProblems(context: android.content.Context, wakeEnabled: Boolean): Int {
    var n = 0
    if (!AssistantControl.hasMic(context)) n++
    if (!SpeechRecognizer.isRecognitionAvailable(context)) n++
    if (!Perms.canOverlay(context)) n++
    if (!wakeEnabled) n++
    return n
}

/** Показывает, что нужно для работы помощника, что он слышит и почему мог замолчать. */
@Composable
fun AssistantCheckScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val status by AssistantStatus.state.collectAsState()
    val tick = rememberResumeTick()

    val micOk = remember(tick, status.updatedAt) { AssistantControl.hasMic(context) }
    val recognizerOk = remember(tick) { SpeechRecognizer.isRecognitionAvailable(context) }
    val overlayOk = remember(tick) { Perms.canOverlay(context) }
    val batteryOk = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    val askMic = rememberMicRequest {
        vm.update { it.copy(wakeEnabled = true) }
        AssistantControl.startListening(context)
    }

    LifeScreen("Проверка помощника", onBack = onBack) {
        Text(
            "Если помощник или «заметка» не отзываются на голос, здесь видно, чего не хватает. Всё, что с красным крестиком, нужно исправить.",
            color = GrayText,
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionTitle("Что нужно для работы")
        InfoCard(Modifier.fillMaxWidth()) {
            CheckRow("Доступ к микрофону", micOk, if (micOk) "Разрешён" else "Не разрешён") {
                PillButton("Разрешить", { askMic() }, Modifier.padding(top = 6.dp))
            }
            CheckRow(
                "Распознавание речи",
                recognizerOk,
                if (recognizerOk) "Есть" else "На телефоне нет службы распознавания речи. Установите или включите приложение «Google» (голосовой ввод) в Google Play",
            )
            CheckRow(
                "Прослушивание включено",
                s.wakeEnabled && status.running,
                when {
                    !s.wakeEnabled -> "Выключено в настройках"
                    status.running -> "Слушаю"
                    else -> "Включено, но служба не работает"
                },
            ) {
                PillButton(
                    if (status.running) "Перезапустить" else "Включить прослушивание",
                    {
                        vm.update { it.copy(wakeEnabled = true) }
                        if (AssistantControl.hasMic(context)) AssistantControl.startListening(context) else askMic()
                    },
                    Modifier.padding(top = 6.dp),
                )
            }
            CheckRow(
                "Робот поверх экрана",
                overlayOk,
                if (overlayOk) "Разрешено" else "Без этого робота не видно: помощник будет только отвечать голосом",
            ) {
                PillButton("Разрешить", { context.startActivity(Perms.overlaySettings(context)) }, Modifier.padding(top = 6.dp))
            }
            CheckRow(
                "Без ограничений батареи",
                batteryOk,
                if (batteryOk) "Да" else "Иначе Android выключит прослушивание, когда экран погаснет",
                last = true,
            ) {
                PillButton(
                    "Открыть настройки",
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    Modifier.padding(top = 6.dp),
                )
            }
        }

        SectionTitle("Что слышит помощник")
        InfoCard(Modifier.fillMaxWidth()) {
            Text("Последняя фраза", color = GrayText, style = MaterialTheme.typography.bodySmall)
            Text(
                status.lastHeard.ifBlank { "Пока ничего. Скажите что-нибудь вслух" },
                color = Snow,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Text("Последняя ошибка", color = GrayText, style = MaterialTheme.typography.bodySmall)
            Text(
                status.lastError.ifBlank { "Нет" },
                color = if (status.lastError.isBlank()) Snow else androidx.compose.ui.graphics.Color(BAD_COLOR),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            "Чтобы позвать помощника, скажите «LifeOS …» или «OS …». Чтобы записать заметку, скажите «заметка» или «сделай заметку». " +
                "Если ваша фраза появилась выше, но ничего не произошло, пришлите мне, как она записалась.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )

        SectionTitle("Попробовать вручную")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                "Позвать помощника",
                { if (micOk) AssistantControl.summon(context) else askMic() },
                Modifier.weight(1f),
            )
            PillButton(
                "Записать заметку",
                { context.startActivity(Intent(context, QuickNoteActivity::class.java)) },
                Modifier.weight(1f),
                outlined = true,
            )
        }
        Text(
            "Ещё помощника можно позвать из шторки: плитка «Позвать помощника» (добавьте её через значок карандаша в быстрых настройках Android).",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun CheckRow(
    title: String,
    ok: Boolean,
    detail: String,
    last: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ok) "✓" else "✗",
                color = androidx.compose.ui.graphics.Color(if (ok) OK_COLOR else BAD_COLOR),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(title, color = Snow, style = MaterialTheme.typography.titleSmall)
        }
        Text(detail, color = GrayText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 26.dp))
        if (!ok && action != null) Column(Modifier.padding(start = 26.dp)) { action() }
    }
}
