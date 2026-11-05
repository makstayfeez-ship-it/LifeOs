package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.assistant.SpeechListener
import com.lifeos.app.assistant.SpeechResult
import com.lifeos.app.data.TaskEntity
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.rememberMicRequest
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Календарь

@Composable
fun CalendarScreen(vm: AppViewModel, onBack: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val accent = LocalAccent.current
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var filter by remember { mutableStateOf(0) } // 0 — всё, 1 — события, 2 — задачи
    var dialog by remember { mutableStateOf(false) }

    val monday = selected.with(DayOfWeek.MONDAY)
    val dayItems = tasks
        .filter { it.epochDay == selected.toEpochDay() }
        .filter { filter == 0 || (filter == 1 && it.isEvent) || (filter == 2 && !it.isEvent) }

    LifeScreen(Fmt.monthYear(selected), onBack = onBack) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = selected.minusWeeks(1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = GrayText, modifier = Modifier.size(28.dp))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0..6) {
                    val d = monday.plusDays(i.toLong())
                    val isSel = d == selected
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { selected = d }.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(Fmt.weekdayShort(d.dayOfWeek), color = GrayText, fontSize = 12.sp)
                        Box(
                            Modifier.padding(top = 4.dp).size(34.dp).clip(CircleShape).background(if (isSel) accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${d.dayOfMonth}", color = Snow, fontSize = 15.sp)
                        }
                    }
                }
            }
            IconButton(onClick = { selected = selected.plusWeeks(1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GrayText, modifier = Modifier.size(28.dp))
            }
        }

        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeChip("Все", filter == 0, { filter = 0 })
            LifeChip("События", filter == 1, { filter = 1 })
            LifeChip("Задачи", filter == 2, { filter = 2 })
        }

        SectionTitle(
            if (selected == LocalDate.now()) "Сегодня · ${Fmt.dayMonth(selected)}" else Fmt.dayMonth(selected),
        )
        if (dayItems.isEmpty()) EmptyHint("На этот день ничего нет")
        dayItems.sortedBy { it.timeMinutes ?: Int.MAX_VALUE }.forEach { t ->
            TaskRow(t, onToggle = { vm.toggleTask(t) }, onDelete = { vm.deleteTask(t) })
        }

        PillButton(
            "+ Добавить",
            { dialog = true },
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            outlined = true,
        )
    }

    if (dialog) {
        var title by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("") }
        var isEvent by remember { mutableStateOf(false) }
        val parsed = Fmt.parseTime(time)
        FormDialog(
            title = "Новое на ${Fmt.dayMonth(selected)}",
            onDismiss = { dialog = false },
            onConfirm = {
                vm.addTask(title.trim(), selected.toEpochDay(), parsed, isEvent)
                dialog = false
            },
            confirmEnabled = title.isNotBlank() && (time.isBlank() || parsed != null),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeChip("Задача", !isEvent, { isEvent = false })
                LifeChip("Событие", isEvent, { isEvent = true })
            }
            LifeField(title, { title = it }, "Название")
            LifeField(time, { time = it }, "Время, например 14:30 (необязательно)", keyboardType = KeyboardType.Number)
        }
    }
}

@Composable
private fun TaskRow(t: TaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            t.timeMinutes?.let { Fmt.time(it) } ?: "—",
            color = GrayText,
            modifier = Modifier.padding(end = 12.dp).size(width = 48.dp, height = 20.dp),
            fontSize = 14.sp,
        )
        val body: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        color = if (t.done) GrayText else Snow,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(if (t.isEvent) "Событие" else "Задача", color = GrayText, style = MaterialTheme.typography.bodySmall)
                }
                if (!t.isEvent) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (t.done) accent else Color.Transparent)
                            .border(1.5.dp, accent, CircleShape)
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (t.done) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
            }
        }
        if (t.isEvent) {
            OutlineCard(Modifier.weight(1f)) { body() }
        } else {
            InfoCard(Modifier.weight(1f)) { body() }
        }
    }
}

// ---------------------------------------------------------------- Покупки

@Composable
fun ShoppingScreen(vm: AppViewModel, onBack: () -> Unit) {
    val items by vm.shopping.collectAsState()
    val accent = LocalAccent.current
    var text by remember { mutableStateOf("") }

    LifeScreen("Покупки", onBack = onBack) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LifeField(text, { text = it }, "Что купить?") }
            Box(Modifier.padding(start = 8.dp)) {
                PillButton("+", {
                    vm.addShopping(text.trim())
                    text = ""
                }, enabled = text.isNotBlank())
            }
        }
        if (items.isEmpty()) EmptyHint("Список пуст. Скажите помощнику: «добавь молоко в список покупок»")
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.toggleShopping(item) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (item.checked) accent else Color.Transparent)
                        .border(1.5.dp, if (item.checked) accent else GrayText, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(
                    item.title,
                    color = if (item.checked) GrayText else Snow,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                )
                IconButton(onClick = { vm.deleteShopping(item) }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
            }
        }
        if (items.any { it.checked }) {
            PillButton("Убрать купленное", { vm.clearChecked() }, Modifier.fillMaxWidth().padding(vertical = 16.dp), outlined = true)
        }
    }
}

// ---------------------------------------------------------------- Привычки

@Composable
fun HabitsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val habits by vm.habits.collectAsState()
    val logs by vm.habitLogs.collectAsState()
    val accent = LocalAccent.current
    var dialog by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val doneToday = habits.count { h -> logs.any { it.habitId == h.id && it.epochDay == today.toEpochDay() } }
    val fraction = if (habits.isEmpty()) 0f else doneToday.toFloat() / habits.size

    LifeScreen("Привычки", onBack = onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            ProgressRing(fraction, "${(fraction * 100).toInt()}%\n$doneToday из ${habits.size}", Modifier.size(150.dp), stroke = 10f)
        }

        // Неделя: сколько привычек выполнено в каждый день
        val monday = today.with(DayOfWeek.MONDAY)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 0..6) {
                val d = monday.plusDays(i.toLong())
                val count = logs.count { it.epochDay == d.toEpochDay() }
                val share = if (habits.isEmpty()) 0f else (count.toFloat() / habits.size).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(width = 16.dp, height = 60.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.size(width = 16.dp, height = 60.dp).clip(RoundedCornerShape(8.dp)).background(CardColor))
                        Box(
                            Modifier
                                .size(width = 16.dp, height = (60 * share).dp.coerceAtLeast(if (count > 0) 6.dp else 0.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent),
                        )
                    }
                    Text(
                        Fmt.weekdayShort(d.dayOfWeek),
                        color = if (d == today) Snow else GrayText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (habits.isEmpty()) EmptyHint("Добавьте первую привычку: например, «Пить воду»")
        habits.forEach { h ->
            val done = logs.any { it.habitId == h.id && it.epochDay == today.toEpochDay() }
            InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { vm.toggleHabitToday(h, done) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.title, color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (done) accent else Color.Transparent)
                            .border(1.5.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.deleteHabit(h) }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
                }
            }
        }
        PillButton("+ Новая привычка", { dialog = true }, Modifier.fillMaxWidth().padding(vertical = 16.dp), outlined = true)
    }

    if (dialog) {
        var title by remember { mutableStateOf("") }
        FormDialog("Новая привычка", { dialog = false }, {
            vm.addHabit(title.trim())
            dialog = false
        }, confirmEnabled = title.isNotBlank()) {
            LifeField(title, { title = it }, "Название")
        }
    }
}

// ---------------------------------------------------------------- Заметки

@Composable
fun NotesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val notes by vm.notes.collectAsState()
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val listener = remember { SpeechListener(context) }
    DisposableEffect(Unit) { onDispose { listener.cancel() } }
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val startRecording = rememberMicRequest {
        if (!listening) {
            listening = true
            partial = ""
            status = null
            scope.launch {
                when (val r = listener.listen { partial = it }) {
                    is SpeechResult.Heard -> {
                        vm.addNote(r.text.trim())
                        status = "Записано"
                    }
                    SpeechResult.Silence -> status = "Ничего не услышала"
                    is SpeechResult.Error -> status = "Не получилось записать"
                }
                listening = false
            }
        }
    }

    LifeScreen("Заметки", onBack = onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(if (listening) Color.Transparent else accent)
                    .border(3.dp, accent, CircleShape)
                    .clickable { if (listening) listener.cancel() else startRecording() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (listening) Icons.Rounded.Close else Icons.Rounded.Mic,
                    contentDescription = "Записать голосом",
                    tint = if (listening) accent else Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            when {
                listening && partial.isNotBlank() -> partial
                listening -> "Говорите…"
                status != null -> status!!
                else -> "Нажмите и продиктуйте заметку"
            },
            color = if (listening) Snow else GrayText,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        Text(
            "Чтобы записывать, не открывая приложение, добавьте плитку «Голосовая заметка» в шторку " +
                "или включите прослушивание «LifeOS» в настройках помощника.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LifeField(text, { text = it }, "Или напишите заметку") }
            Box(Modifier.padding(start = 8.dp)) {
                PillButton("+", {
                    vm.addNote(text.trim())
                    text = ""
                }, enabled = text.isNotBlank())
            }
        }

        if (notes.isEmpty()) EmptyHint("Заметок пока нет")
        notes.forEach { n ->
            InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(n.text, color = Snow, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (n.isVoice) LabelPill("голос", filled = false, modifier = Modifier.padding(end = 8.dp))
                            Text(Fmt.dateTime(n.createdAt), color = GrayText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    IconButton(onClick = { vm.deleteNote(n) }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
                }
            }
        }
    }
}
