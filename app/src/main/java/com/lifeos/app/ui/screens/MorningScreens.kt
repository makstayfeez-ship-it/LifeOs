package com.lifeos.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifeos.app.brief.Brief
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.Perms
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.rememberResumeTick
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow

// ---------------------------------------------------------------- Утренний обзор

@Composable
fun MorningScreen(vm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val tasks by vm.tasks.collectAsState()
    var brief by remember { mutableStateOf<Brief?>(null) }

    // Пересобираем обзор, когда меняются настройки или дела на сегодня
    LaunchedEffect(settings, tasks) { brief = vm.buildBrief() }

    LifeScreen(
        "Утренний обзор",
        onBack = onBack,
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Настройки обзора", tint = Snow) } },
    ) {
        val b = brief
        if (b == null) {
            EmptyHint("Собираю обзор…")
            return@LifeScreen
        }
        Text(b.greeting, color = Snow, style = MaterialTheme.typography.headlineLarge)
        Text("Вот что важно сегодня:", color = GrayText, modifier = Modifier.padding(bottom = 12.dp))

        b.weather?.let { w ->
            OutlineCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, null, tint = LocalAccent.current, modifier = Modifier.size(32.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("${w.tempC}°, ${w.description}", color = Snow, style = MaterialTheme.typography.headlineMedium)
                        Text("Днём от ${w.minC}° до ${w.maxC}°", color = GrayText)
                    }
                }
            }
        }
        b.weatherNote?.let { Text(it, color = GrayText, modifier = Modifier.padding(bottom = 10.dp)) }

        if (settings.briefEvents) {
            BriefCard(Icons.Rounded.CalendarMonth, "События", b.events.ifEmpty { listOf("Событий нет") })
        }
        if (settings.briefTasks) {
            BriefCard(Icons.Rounded.CheckCircle, "Задачи", b.tasks.ifEmpty { listOf("Задач нет") })
        }
        b.spentYesterday?.let { BriefCard(Icons.Rounded.AccountBalanceWallet, "Расходы за вчера", listOf(Fmt.money(it))) }
        if (b.habitsTotal != null && b.habitsTotal > 0) {
            BriefCard(Icons.Rounded.Loop, "Привычки", listOf("Сегодня ${b.habitsDone} из ${b.habitsTotal}"))
        }
        if (b.goalTitle != null && b.goalProgress != null) {
            BriefCard(Icons.Rounded.Flag, "Цель на год", listOf("${b.goalTitle}: ${b.goalProgress}%"))
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Озвучить", { vm.speak(b.spoken()) }, Modifier.weight(1f), outlined = true)
            PillButton("Отлично!", onBack, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BriefCard(icon: ImageVector, title: String, lines: List<String>) {
    InfoCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = Snow, style = MaterialTheme.typography.titleMedium)
                lines.forEach { Text(it, color = GrayText, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

// ---------------------------------------------------------------- Настройки обзора

@Composable
fun MorningSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val exactOk = remember(tick) { Perms.canExactAlarms(context) }
    var city by remember(s.city) { mutableStateOf(s.city) }
    var cityMessage by remember { mutableStateOf<String?>(null) }

    LifeScreen("Настройки обзора", onBack = onBack) {
        SwitchRow(
            "Показывать на экране блокировки",
            s.briefOnLock,
            { on -> vm.update { it.copy(briefOnLock = on) } },
            subtitle = "Когда вы утром включите телефон, появится красивый обзор: погода, дела, привычки, цель. " +
                "Нужно разрешение «Поверх других приложений», и лучше включить прослушивание помощника, чтобы система не выгружала LifeOS.",
        )
        PillButton(
            "Показать, как это выглядит",
            { context.startActivity(android.content.Intent(context, com.lifeos.app.brief.MorningLockActivity::class.java)) },
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            outlined = true,
        )
        SwitchRow(
            "Показывать каждое утро",
            s.morningEnabled,
            { on -> vm.updateMorning { it.copy(morningEnabled = on) } },
            subtitle = "В выбранное время придёт уведомление с вашим обзором.",
        )

        InfoCard(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            onClick = {
                TimePickerDialog(
                    context,
                    { _, h, m -> vm.updateMorning { it.copy(morningHour = h, morningMinute = m) } },
                    s.morningHour,
                    s.morningMinute,
                    true,
                ).show()
            },
        ) {
            Text("Время, когда вы просыпаетесь", color = GrayText, style = MaterialTheme.typography.bodySmall)
            Text(Fmt.time(s.morningHour * 60 + s.morningMinute), color = LocalAccent.current, style = MaterialTheme.typography.headlineLarge)
        }

        if (s.morningEnabled && !exactOk) {
            InfoCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Точное время не разрешено", color = Snow, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Без этого разрешения Android может задержать обзор на несколько минут.",
                    color = GrayText,
                    style = MaterialTheme.typography.bodySmall,
                )
                PillButton("Разрешить", { context.startActivity(Perms.exactAlarmSettings(context)) }, Modifier.padding(top = 8.dp))
            }
        }

        SectionTitle("Город для погоды")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { LifeField(city, { city = it }, "Например: Москва") }
            PillButton(
                "Найти",
                { vm.findCity(city.trim()) { err -> cityMessage = err ?: "Город сохранён" } },
                Modifier.padding(start = 8.dp),
                enabled = city.isNotBlank(),
            )
        }
        val note = cityMessage ?: if (s.hasLocation) "Выбрано: ${s.city}" else "Город не выбран"
        Text(note, color = GrayText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))

        SectionTitle("Что показывать")
        SwitchRow("Погода", s.briefWeather, { on -> vm.update { it.copy(briefWeather = on) } })
        SwitchRow("События", s.briefEvents, { on -> vm.update { it.copy(briefEvents = on) } })
        SwitchRow("Задачи", s.briefTasks, { on -> vm.update { it.copy(briefTasks = on) } })
        SwitchRow("Расходы за вчера", s.briefSpending, { on -> vm.update { it.copy(briefSpending = on) } })
        SwitchRow("Привычки", s.briefHabits, { on -> vm.update { it.copy(briefHabits = on) } })
        SwitchRow("Цель года", s.briefGoal, { on -> vm.update { it.copy(briefGoal = on) } })

        SectionTitle("Озвучка")
        SwitchRow(
            "Читать обзор вслух",
            s.speakBrief,
            { on -> vm.update { it.copy(speakBrief = on) } },
            subtitle = "Голосом помощника, когда приходит уведомление.",
        )
    }
}
