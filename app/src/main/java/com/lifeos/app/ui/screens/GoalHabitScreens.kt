package com.lifeos.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.data.Goal
import com.lifeos.app.data.GoalEntry
import com.lifeos.app.data.GoalsCodec
import com.lifeos.app.data.HabitMeta
import com.lifeos.app.data.HabitMetaCodec
import com.lifeos.app.data.Levels
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.HoldEmojiPicker
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

// ---------------------------------------------------------------- Конфетти

private val ConfettiColors = listOf(
    Color(0xFFFF7A1A), Color(0xFFFFD23F), Color(0xFF3DDC97), Color(0xFF4DA3FF), Color(0xFFFF5C8A), Color(0xFFB388FF),
)

private class Piece(val x: Float, val drift: Float, val speed: Float, val size: Float, val turn: Float, val color: Color)

/** Праздничное конфетти поверх экрана. Запускается при каждом новом значении [trigger] больше нуля. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) {
        List(80) {
            Piece(
                x = Random.nextFloat(),
                drift = (Random.nextFloat() - 0.5f) * 0.5f,
                speed = 0.7f + Random.nextFloat() * 0.9f,
                size = 8f + Random.nextFloat() * 10f,
                turn = Random.nextFloat() * 720f,
                color = ConfettiColors.random(),
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2800, easing = LinearEasing))
        }
    }
    if (progress.value < 1f) {
        Canvas(modifier.fillMaxSize()) {
            val t = progress.value
            pieces.forEach { p ->
                val x = (p.x + p.drift * t) * size.width
                val y = (-0.08f + p.speed * t * 1.25f) * size.height
                rotate(p.turn * t, pivot = Offset(x, y)) {
                    drawRect(p.color.copy(alpha = (1f - t * 0.6f)), Offset(x, y), Size(p.size, p.size * 0.55f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Привычки

private val HabitEmojis = listOf(
    "💪", "🏃", "📚", "💧", "🧘", "😴",
    "🥗", "✍️", "🎸", "🚭", "🧹", "🦷",
    "🚴", "🏊", "🍎", "🧠", "🙏", "🎨",
    "🧘‍♀️", "☀️", "🐕", "💊", "📵", "🌱",
)
private val HabitDurations = listOf("Неделя" to 7, "Месяц" to 30, "3 месяца" to 90, "Год" to 365, "Без срока" to 0)
private val Cheers = listOf("Отлично!", "Так держать!", "Ещё один шаг к цели!", "Огонь!", "Вы молодец!", "Сегодня зачёт!")

private fun streakOf(days: Set<Long>, today: Long): Int {
    var d = if (today in days) today else today - 1
    var n = 0
    while (d in days) {
        n++
        d--
    }
    return n
}

@Composable
fun HabitsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val habits by vm.habits.collectAsState()
    val logs by vm.habitLogs.collectAsState()
    val settings by vm.settings.collectAsState()
    val accent = LocalAccent.current
    var dialog by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }
    var cheer by remember { mutableStateOf<String?>(null) }

    val meta = remember(settings.habitMetaJson) { HabitMetaCodec.decode(settings.habitMetaJson) }
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val doneToday = habits.count { h -> logs.any { it.habitId == h.id && it.epochDay == todayDay } }
    val fraction = if (habits.isEmpty()) 0f else doneToday.toFloat() / habits.size
    val points = logs.size

    Box(Modifier.fillMaxSize()) {
        LifeScreen("Привычки", onBack = onBack) {
            // Уровень: каждая отметка даёт очко
            InfoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Уровень ${Levels.level(points) + 1} · ${Levels.name(points)}", color = Snow, style = MaterialTheme.typography.titleMedium)
                        Text(
                            Levels.next(points)?.let { "Ещё ${it - points} отметок до следующего уровня" } ?: "Высший уровень!",
                            color = GrayText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ProgressRing(fraction, "$doneToday/${habits.size}", Modifier.size(64.dp), stroke = 6f)
                }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A40))) {
                    Box(Modifier.fillMaxWidth(Levels.progress(points).coerceAtLeast(0.02f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                }
            }

            cheer?.let {
                Text(it, color = accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }

            if (habits.isEmpty()) {
                EmptyHint("Добавьте привычку и срок: например, «Отжимания», 3 раза в неделю, на месяц. Потом каждый день заходите и отмечайте, что сделали.")
            }

            val monday = today.with(DayOfWeek.MONDAY)
            habits.forEach { h ->
                val m = meta[h.id] ?: HabitMeta(startDay = todayDay)
                val days = logs.filter { it.habitId == h.id }.map { it.epochDay }.toSet()
                val done = todayDay in days
                val streak = streakOf(days, todayDay)
                val weekDone = (0..6).count { monday.plusDays(it.toLong()).toEpochDay() in days }
                val planned = m.plannedTotal
                val doneInPlan = days.count { it >= m.startDay && (m.endDay == null || it <= m.endDay!!) }
                val finished = m.endDay != null && todayDay > m.endDay!!
                val daysLeft = m.endDay?.let { (it - todayDay).coerceAtLeast(0) }

                InfoCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, color = Snow, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${m.perWeek} р/нед" + (if (m.durationDays > 0) " · ${durationName(m.durationDays)}" else " · без срока"),
                                color = GrayText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (streak > 0) LabelPill("🔥 $streak", modifier = Modifier.padding(end = 4.dp))
                        IconButton(onClick = { vm.deleteHabit(h) }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
                    }

                    // Неделя: какие дни выполнены
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0..6) {
                            val d = monday.plusDays(i.toLong())
                            val ok = d.toEpochDay() in days
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (ok) accent else Color.Transparent)
                                        .border(1.5.dp, if (d == today) Snow else if (ok) accent else Color(0xFF3A3A40), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (ok) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                                Text(com.lifeos.app.ui.Fmt.weekdayShort(d.dayOfWeek), color = GrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                    Text(
                        "На этой неделе: $weekDone из ${m.perWeek}" + if (weekDone >= m.perWeek) " ⭐" else "",
                        color = if (weekDone >= m.perWeek) accent else GrayText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    if (planned != null) {
                        val share = (doneInPlan.toFloat() / planned).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A40))) {
                            Box(Modifier.fillMaxWidth(share.coerceAtLeast(0.02f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                        }
                        Text(
                            if (finished) "План завершён: выполнено $doneInPlan из $planned"
                            else "План: $doneInPlan из $planned, осталось ${daysLeft} дн.",
                            color = GrayText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    PillButton(
                        if (done) "Сегодня выполнено ✓" else "Сегодня я выполнил!",
                        {
                            vm.toggleHabitToday(h, done)
                            if (!done) {
                                val newStreak = streak + 1
                                val planDone = planned != null && doneInPlan + 1 >= planned
                                cheer = when {
                                    planDone -> "🏆 План по привычке «${h.title}» выполнен! Вы молодец!"
                                    newStreak == 7 -> "🔥 Неделя без пропусков!"
                                    newStreak == 14 -> "🔥 Две недели подряд!"
                                    newStreak == 30 -> "🔥 Целый месяц! Это уже характер."
                                    newStreak == 100 -> "🔥 100 дней подряд!"
                                    weekDone + 1 == m.perWeek -> "⭐ Недельная цель выполнена!"
                                    newStreak == 1 -> "Первый шаг сделан!"
                                    else -> Cheers.random() + " Серия: $newStreak"
                                }
                                confetti++
                            }
                        },
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        outlined = done,
                        enabled = !finished,
                    )
                }
            }
            PillButton("+ Новая привычка", { dialog = true }, Modifier.fillMaxWidth().padding(vertical = 16.dp), outlined = true)
        }
        Confetti(confetti)
    }

    if (dialog) {
        var title by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf(HabitEmojis.first()) }
        var perWeek by remember { mutableIntStateOf(3) }
        var duration by remember { mutableIntStateOf(30) }
        FormDialog("Новая привычка", { dialog = false }, {
            vm.addHabitPlan(title.trim(), emoji, perWeek, duration)
            dialog = false
        }, confirmEnabled = title.isNotBlank()) {
            LifeField(title, { title = it }, "Например: отжимания")
            HoldEmojiPicker(emoji, HabitEmojis, { emoji = it })
            Text("Сколько раз в неделю", color = GrayText, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { n -> LifeChip("$n", perWeek == n, { perWeek = n }) }
            }
            Text("На какой срок", color = GrayText, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitDurations.forEach { (label, days) -> LifeChip(label, duration == days, { duration = days }) }
            }
        }
    }
}

private fun durationName(days: Int): String = HabitDurations.firstOrNull { it.second == days }?.first?.lowercase() ?: "$days дн."

// ---------------------------------------------------------------- Цели на год

private val GoalEmojis = listOf(
    "🎯", "💰", "🚗", "🏠", "✈️", "📚",
    "💪", "🎓", "💼", "❤️", "🏆", "🌍",
    "👶", "💍", "🎸", "🏔️", "🐶", "🛠️",
)
private val GoalUnits = listOf("₽", "км", "раз", "кг", "книг", "")

private fun fmtAmount(v: Double): String =
    if (v % 1.0 == 0.0) String.format(java.util.Locale.ROOT, "%,d", v.toLong()).replace(',', ' ')
    else String.format(java.util.Locale.ROOT, "%.1f", v)

/** Цель, по которой пора подвести итог: наступил конец года (с 25 декабря) или год уже прошёл. */
fun dueGoal(goals: List<Goal>, today: LocalDate = LocalDate.now()): Goal? =
    goals.firstOrNull { it.result == 0 && (today.year > it.year || today >= LocalDate.of(it.year, 12, 25)) }

@Composable
fun GoalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val goals = remember(settings.goalsJson) { GoalsCodec.decode(settings.goalsJson) }
    val accent = LocalAccent.current
    var addDialog by remember { mutableStateOf(false) }
    var progressFor by remember { mutableStateOf<Goal?>(null) }
    var reviewFor by remember { mutableStateOf<Goal?>(null) }
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LifeScreen("Цели на год", onBack = onBack) {
            Text(
                "Поставьте большую цель на год и каждый день отмечайте, сколько сделали. В конце года я спрошу, получилось ли.",
                color = GrayText,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val active = goals.filter { it.result == 0 }
            val finished = goals.filter { it.result != 0 }
            if (goals.isEmpty()) EmptyHint("Например: «Накопить миллион» или «Купить машину»")
            active.forEach { g ->
                val left = java.time.temporal.ChronoUnit.DAYS.between(today, g.endDay).coerceAtLeast(0)
                InfoCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.emoji, fontSize = 30.sp, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, color = Snow, style = MaterialTheme.typography.titleMedium)
                            Text("До конца ${g.year} года: $left дн.", color = GrayText, style = MaterialTheme.typography.bodySmall)
                        }
                        if (g.hasTarget) ProgressRing(g.fraction, "${g.percent}%", Modifier.size(58.dp))
                        IconButton(onClick = { vm.saveGoals { list -> list.filterNot { it.id == g.id } } }) {
                            Icon(Icons.Rounded.Close, "Удалить", tint = GrayText)
                        }
                    }
                    if (g.hasTarget) {
                        Text(
                            "${fmtAmount(g.current)} из ${fmtAmount(g.target)} ${g.unit}".trim(),
                            color = Snow,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text("Отметок: ${g.entries.size}", color = Snow, modifier = Modifier.padding(top = 8.dp))
                    }
                    g.entries.takeLast(3).reversed().forEach { e ->
                        Text(
                            com.lifeos.app.ui.Fmt.dayMonth(LocalDate.ofEpochDay(e.epochDay)) +
                                (if (g.hasTarget) " · +${fmtAmount(e.amount)} ${g.unit}" else "") +
                                (if (e.note.isNotBlank()) " · ${e.note}" else ""),
                            color = GrayText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Отметить сегодня", { progressFor = g }, Modifier.weight(1f))
                        PillButton("Итог", { reviewFor = g }, Modifier.weight(0.6f), outlined = true)
                    }
                }
            }
            if (finished.isNotEmpty()) {
                SectionTitle("Итоги")
                finished.forEach { g ->
                    InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.title, color = Snow, style = MaterialTheme.typography.titleMedium)
                                Text("${g.year}", color = GrayText, style = MaterialTheme.typography.bodySmall)
                            }
                            LabelPill(if (g.result == 1) "Достигнута 🏆" else "Не вышло", filled = g.result == 1)
                        }
                    }
                }
            }
            PillButton("+ Новая цель на год", { addDialog = true }, Modifier.fillMaxWidth().padding(vertical = 12.dp), outlined = true)
        }
        // Автоматически предлагаем подвести итог ближе к концу года
        val due = remember(goals) { dueGoal(goals) }
        LaunchedEffect(due?.id) { if (due != null && reviewFor == null) reviewFor = due }
    }

    if (addDialog) {
        var title by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf(GoalEmojis.first()) }
        var target by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("₽") }
        FormDialog(
            title = "Цель на ${today.year} год",
            onDismiss = { addDialog = false },
            confirmEnabled = title.isNotBlank(),
            onConfirm = {
                vm.saveGoals { list ->
                    list + Goal(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        year = today.year,
                        emoji = emoji,
                        unit = unit,
                        target = target.toDoubleOrNull() ?: 0.0,
                    )
                }
                addDialog = false
            },
        ) {
            LifeField(title, { title = it }, "Например: накопить миллион")
            HoldEmojiPicker(emoji, GoalEmojis, { emoji = it })
            LifeField(target, { target = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, "Сколько нужно (необязательно)", keyboardType = KeyboardType.Decimal)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GoalUnits.forEach { u -> LifeChip(u.ifBlank { "без ед." }, unit == u, { unit = u }) }
            }
        }
    }

    progressFor?.let { g ->
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        FormDialog(
            title = g.title,
            confirmText = "Записать",
            onDismiss = { progressFor = null },
            confirmEnabled = (g.hasTarget && (amount.toDoubleOrNull() ?: 0.0) != 0.0) || (!g.hasTarget && note.isNotBlank()),
            onConfirm = {
                vm.saveGoals { list ->
                    list.map {
                        if (it.id == g.id) it.copy(entries = it.entries + GoalEntry(LocalDate.now().toEpochDay(), amount.toDoubleOrNull() ?: 0.0, note.trim())) else it
                    }
                }
                progressFor = null
            },
        ) {
            if (g.hasTarget) {
                LifeField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(12) }, "Сколько сегодня, ${g.unit}".trimEnd(' ', ','), keyboardType = KeyboardType.Decimal)
            }
            LifeField(note, { note = it }, if (g.hasTarget) "Комментарий (необязательно)" else "Что сделали для цели")
        }
    }

    reviewFor?.let { g -> GoalReviewDialog(vm, g, onClose = { reviewFor = null }) }
}

/** Итог цели: «Получилось?» Да: поздравление с роботом и конфетти. Нет: слова поддержки. */
@Composable
fun GoalReviewDialog(vm: AppViewModel, goal: Goal, onClose: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val skin = remember(settings.skinId) { SkinCatalog.byId(settings.skinId) }
    var answer by remember { mutableIntStateOf(0) } // 0 — спрашиваем, 1 — да, 2 — нет
    var confetti by remember { mutableIntStateOf(0) }

    fun resolve(result: Int) {
        vm.saveGoals { list -> list.map { if (it.id == goal.id) it.copy(result = result) else it } }
        answer = result
        if (result == 1) confetti++
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CardColor,
        title = {
            Text(
                when (answer) {
                    1 -> "Поздравляю! 🏆"
                    2 -> "Мне жаль"
                    else -> "Цель «${goal.title}»"
                },
                color = Snow,
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Box {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    when (answer) {
                        1 -> {
                            RobotView(skin, RobotMood.HAPPY, Modifier.size(150.dp), walking = true)
                            Text(
                                "Вы сделали это! ${goal.emoji}\nЦель «${goal.title}» достигнута. Я горжусь вами. Отметим это!",
                                color = Snow,
                                textAlign = TextAlign.Center,
                            )
                        }
                        2 -> {
                            RobotView(skin, RobotMood.THINKING, Modifier.size(130.dp))
                            Text(
                                "Бывает, не всё получается с первого раза. Вы старались, и это уже много. " +
                                    "Новый год даёт новый шанс: давайте поставим цель, которая точно получится.",
                                color = Snow,
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> {
                            RobotView(skin, RobotMood.LISTENING, Modifier.size(130.dp))
                            Text("Вы выполнили эту цель?", color = Snow, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                            if (goal.hasTarget) {
                                Text(
                                    "Сейчас: ${fmtAmount(goal.current)} из ${fmtAmount(goal.target)} ${goal.unit}".trim(),
                                    color = GrayText,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Confetti(confetti, Modifier.matchParentSize())
            }
        },
        confirmButton = {
            if (answer == 0) TextButton(onClick = { resolve(1) }) { Text("Да", color = LocalAccent.current) }
            else TextButton(onClick = onClose) { Text("Закрыть", color = LocalAccent.current) }
        },
        dismissButton = {
            if (answer == 0) {
                Row {
                    TextButton(onClick = { resolve(2) }) { Text("Нет", color = GrayText) }
                    TextButton(onClick = onClose) { Text("Позже", color = GrayText) }
                }
            }
        },
    )
}
