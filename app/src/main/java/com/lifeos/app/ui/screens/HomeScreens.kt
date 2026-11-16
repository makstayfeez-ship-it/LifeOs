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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.QuickNoteActivity
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LocalQuickAdd
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.rememberMicRequest
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.CardBorder
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.LifePalette
import com.lifeos.app.ui.theme.LifeStyle
import com.lifeos.app.ui.theme.onAccent
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val expenses by vm.expenses.collectAsState()
    val habits by vm.habits.collectAsState()
    val logs by vm.habitLogs.collectAsState()
    val notes by vm.notes.collectAsState()
    val goals = remember(settings.goalsJson) { com.lifeos.app.data.GoalsCodec.decode(settings.goalsJson) }
    val activeGoal = goals.firstOrNull { it.result == 0 && it.year >= LocalDate.now().year }
    var reviewGoal by remember { mutableStateOf<com.lifeos.app.data.Goal?>(null) }
    // В конце года сам спрашиваем: цель выполнена?
    LaunchedEffect(settings.goalsJson) {
        dueGoal(goals)?.let { if (reviewGoal == null) reviewGoal = it }
    }
    val callRobot = rememberMicRequest { AssistantControl.summon(context) }
    val remindVoice = rememberMicRequest { AssistantControl.remind(context) }
    // Мгновенная голосовая заметка: одно нажатие, говорите, окно само закроется
    val quickNote = rememberMicRequest {
        context.startActivity(Intent(context, QuickNoteActivity::class.java))
    }
    val addQuick = LocalQuickAdd.current

    val today = LocalDate.now()
    val todays = tasks.filter { it.epochDay == today.toEpochDay() }
    val zone = ZoneId.systemDefault()
    val spent = expenses
        .filter { !it.income && Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today }
        .sumOf { it.amountRub }
    val habitIds = habits.map { it.id }.toSet()
    val habitsDone = logs.count { it.epochDay == today.toEpochDay() && it.habitId in habitIds }

    // Если помощнику чего-то не хватает, говорим об этом сразу, а не оставляем его молча не работать
    val resumeTick = com.lifeos.app.ui.rememberResumeTick()
    val problems = remember(resumeTick, settings.wakeEnabled) { assistantProblems(context, settings.wakeEnabled) }

    val state = HomeState(
        name = user?.name?.ifBlank { "друг" } ?: "друг",
        todays = todays,
        events = todays.count { it.isEvent },
        open = todays.count { !it.isEvent && !it.done },
        spent = spent,
        habitsDone = habitsDone,
        habitsTotal = habits.size,
        notesCount = notes.size,
        goal = activeGoal,
        blocks = homeBlockSet(settings.homeBlocks),
        notesButton = settings.notesButton,
        problems = problems,
        go = go,
        toggleTask = { vm.toggleTask(it) },
        onVoice = { callRobot() },
        onText = { com.lifeos.app.assistant.TextAssist.open(context) },
        onRemind = { remindVoice() },
        onQuickNote = { quickNote() },
        onPrompt = { com.lifeos.app.assistant.TextAssist.openWith(context, it) },
        onAdd = addQuick,
    )
    when (LifePalette.style) {
        LifeStyle.DARK -> HomeDark(state)
        LifeStyle.WARM -> HomeWarm(state)
        LifeStyle.LIGHT -> HomeLight(state)
    }

    reviewGoal?.let { g -> GoalReviewDialog(vm, g, onClose = { reviewGoal = null }) }
}

/**
 * Кнопка вызова помощника. Нажатие зовёт голосом. Если зажать, появляется меню: говорить, писать бесшумно
 * (внизу экрана строка для текста, ответ приходит текстом) или поставить напоминание.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AssistantCallButton(
    accent: Color,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    bg: Color = accent,
    tint: Color = onAccent(accent),
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(modifier) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .combinedClickable(
                    onClick = onVoice,
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        menu = true
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Позвать помощника", tint = tint)
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
            drawArc(CardBorder, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(
                accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(w, cap = StrokeCap.Round),
            )
        }
        Text(label, color = Snow, fontSize = 13.sp)
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
