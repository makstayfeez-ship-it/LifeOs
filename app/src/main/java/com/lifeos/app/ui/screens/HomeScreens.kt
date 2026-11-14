package com.lifeos.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.QuickNoteActivity
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.rememberMicRequest
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.time.LocalDate

@Composable
private fun greetingWord(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..11 -> "Доброе утро,"
        in 12..17 -> "Добрый день,"
        in 18..22 -> "Добрый вечер,"
        else -> "Доброй ночи,"
    }
}

/** Плитка раздела: иконка в акцентном цвете и подпись. */
@Composable
fun AppTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(30.dp))
        Text(label, color = Snow, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, go: (String) -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val user by vm.user.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val goals = remember(settings.goalsJson) { com.lifeos.app.data.GoalsCodec.decode(settings.goalsJson) }
    val activeGoal = goals.firstOrNull { it.result == 0 && it.year >= LocalDate.now().year }
    var reviewGoal by remember { mutableStateOf<com.lifeos.app.data.Goal?>(null) }
    // В конце года сам спрашиваем: цель выполнена?
    LaunchedEffect(settings.goalsJson) {
        dueGoal(goals)?.let { if (reviewGoal == null) reviewGoal = it }
    }
    val accent = LocalAccent.current
    val callRobot = rememberMicRequest { AssistantControl.summon(context) }
    val remindVoice = rememberMicRequest { AssistantControl.remind(context) }
    // Мгновенная голосовая заметка: одно нажатие, говорите, окно само закроется
    val quickNote = rememberMicRequest {
        context.startActivity(Intent(context, QuickNoteActivity::class.java))
    }

    val today = LocalDate.now()
    val todays = tasks.filter { it.epochDay == today.toEpochDay() }
    val events = todays.count { it.isEvent }
    val open = todays.count { !it.isEvent && !it.done }

    Column(
        Modifier
            .fillMaxSize()
            .lifeBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greetingWord(), color = GrayText, style = MaterialTheme.typography.bodyLarge)
                Text(
                    user?.name?.ifBlank { "друг" } ?: "друг",
                    color = Snow,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(CardColor).clickable { quickNote() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.StickyNote2, contentDescription = "Быстрая голосовая заметка", tint = accent)
            }
            AssistantCallButton(
                accent = accent,
                onVoice = { callRobot() },
                onText = { com.lifeos.app.assistant.TextAssist.open(context) },
                onRemind = { remindVoice() },
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Box(Modifier.padding(bottom = 16.dp))

        // Если помощнику чего-то не хватает, говорим об этом сразу, а не оставляем его молча не работать
        val resumeTick = com.lifeos.app.ui.rememberResumeTick()
        val problems = remember(resumeTick, settings.wakeEnabled) { assistantProblems(context, settings.wakeEnabled) }
        if (problems > 0) {
            OutlineCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), onClick = { go("assistant_check") }) {
                Text("Помощник пока не готов", color = Snow, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Не хватает: $problems. Нажмите, чтобы проверить и включить",
                    color = GrayText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        OutlineCard(Modifier.fillMaxWidth(), onClick = { go("calendar") }) {
            Text("Сегодня · ${Fmt.dayMonth(today)}", color = GrayText)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.plural(todays.size, "дело", "дела", "дел"),
                    color = Snow,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = accent)
            }
            Text(
                "${Fmt.plural(events, "событие", "события", "событий")} • ${Fmt.plural(open, "задача", "задачи", "задач")}",
                color = GrayText,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTile("Календарь", Icons.Rounded.CalendarMonth, { go("calendar") }, Modifier.weight(1f))
            AppTile("Привычки", Icons.Rounded.Loop, { go("habits") }, Modifier.weight(1f))
            AppTile("Финансы", Icons.Rounded.AccountBalanceWallet, { go("finance") }, Modifier.weight(1f))
            AppTile("Заметки", Icons.Rounded.StickyNote2, { go("notes") }, Modifier.weight(1f))
        }

        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardColor)
                .clickable { go("goals") }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Цель на год", color = GrayText, style = MaterialTheme.typography.bodySmall)
                Text(
                    activeGoal?.let { it.emoji + " " + it.title } ?: "Поставьте цель на год",
                    color = Snow,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (activeGoal != null && activeGoal.hasTarget) {
                ProgressRing(activeGoal.fraction, "${activeGoal.percent}%", Modifier.size(56.dp))
            }
        }

        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardColor)
                .clickable { go("morning") }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Утренний обзор", color = Snow, style = MaterialTheme.typography.titleMedium)
                Text("Погода, дела и расходы на сегодня", color = GrayText, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = GrayText)
        }
    }

    reviewGoal?.let { g -> GoalReviewDialog(vm, g, onClose = { reviewGoal = null }) }
}

/**
 * Кнопка вызова помощника. Нажатие зовёт голосом. Если зажать, появляется меню: говорить, писать бесшумно
 * (внизу экрана строка для текста, ответ приходит текстом) или поставить напоминание.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AssistantCallButton(
    accent: Color,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(modifier) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent)
                .combinedClickable(
                    onClick = onVoice,
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        menu = true
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Позвать помощника", tint = Color.White)
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Голосом") },
                leadingIcon = { Icon(Icons.Rounded.Mic, null) },
                onClick = { menu = false; onVoice() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Бесшумно: писать") },
                leadingIcon = { Icon(Icons.Rounded.Keyboard, null) },
                onClick = { menu = false; onText() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Напоминание") },
                leadingIcon = { Icon(Icons.Rounded.Notifications, null) },
                onClick = { menu = false; onRemind() },
            )
        }
    }
}

/** Круговой индикатор прогресса (используется для цели и привычек). */
@Composable
fun ProgressRing(fraction: Float, label: String, modifier: Modifier = Modifier, stroke: Float = 7f) {
    val accent = LocalAccent.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke * density
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(Color(0xFF3A3A40), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(
                accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(w, cap = StrokeCap.Round),
            )
        }
        Text(label, color = Snow, fontSize = 13.sp)
    }
}

@Composable
fun AppsScreen(go: (String) -> Unit) {
    LifeScreen("Приложения") {
        val items = listOf(
            Triple("Календарь", Icons.Rounded.CalendarMonth, "calendar"),
            Triple("Задачи", Icons.Rounded.CheckCircle, "calendar"),
            Triple("Финансы", Icons.Rounded.AccountBalanceWallet, "finance"),
            Triple("Покупки", Icons.Rounded.ShoppingCart, "shopping"),
            Triple("Привычки", Icons.Rounded.Loop, "habits"),
            Triple("Цели на год", Icons.Rounded.Flag, "goals"),
            Triple("Уведомления", Icons.Rounded.Notifications, "reminders"),
            Triple("Профиль", Icons.Rounded.Person, "profile"),
            Triple("Заметки", Icons.Rounded.StickyNote2, "notes"),
            Triple("ИИ-помощник", Icons.Rounded.AutoAwesome, "assistant"),
            Triple("Утренний обзор", Icons.Rounded.WbSunny, "morning"),
            Triple("Магазин роботов", Icons.Rounded.Storefront, "shop"),
            Triple("Оформление", Icons.Rounded.Palette, "theme"),
            Triple("Виджеты", Icons.Rounded.Widgets, "widgets"),
            Triple("Безопасность", Icons.Rounded.Lock, "security"),
            Triple("Аккаунт", Icons.Rounded.Settings, "account"),
        )
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, route) ->
                    AppTile(label, icon, { go(route) }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Вкладка «Уведомления»: ближайшие дела на неделю вперёд. */
@Composable
fun RemindersScreen(vm: AppViewModel, onOpenCalendar: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val today = LocalDate.now()
    val upcoming = tasks
        .filter { !it.done && it.epochDay >= today.toEpochDay() && it.epochDay <= today.toEpochDay() + 7 }
        .groupBy { it.epochDay }
        .toSortedMap()

    LifeScreen("Уведомления") {
        if (upcoming.isEmpty()) {
            EmptyHint("На ближайшую неделю дел нет. Добавьте их в календаре или скажите помощнику: «поставь задачу …»")
        }
        upcoming.forEach { (day, list) ->
            val date = LocalDate.ofEpochDay(day)
            SectionTitle(if (day == today.toEpochDay()) "Сегодня" else Fmt.dayMonth(date))
            list.forEach { t ->
                OutlineCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onOpenCalendar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        t.timeMinutes?.let { LabelPill(Fmt.time(it)) }
                    }
                    if (t.subtitle.isNotBlank()) Text(t.subtitle, color = GrayText)
                }
            }
        }
    }
}
