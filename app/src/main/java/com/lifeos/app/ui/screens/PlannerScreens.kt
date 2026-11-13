package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import com.lifeos.app.audio.VoiceRecorder
import com.lifeos.app.audio.VoicePlayer
import com.lifeos.app.assistant.AssistantControl
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
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
import com.lifeos.app.ui.components.TimeField
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
        var time by remember { mutableStateOf<Int?>(null) }
        var isEvent by remember { mutableStateOf(false) }
        FormDialog(
            title = "Новое на ${Fmt.dayMonth(selected)}",
            onDismiss = { dialog = false },
            onConfirm = {
                vm.addTask(title.trim(), selected.toEpochDay(), time, isEvent)
                dialog = false
            },
            confirmEnabled = title.isNotBlank(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeChip("Задача", !isEvent, { isEvent = false })
                LifeChip("Событие", isEvent, { isEvent = true })
            }
            LifeField(title, { title = it }, "Название")
            TimeField(time, { time = it }, "Время: нажмите, чтобы выбрать (необязательно)")
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

    // Запись звука (аудиофайл) и воспроизведение
    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer(context) }
    var audioOn by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    val wave = remember { mutableStateListOf<Float>() }
    var playingId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            player.stop()
        }
    }
    LaunchedEffect(audioOn) {
        if (audioOn) {
            seconds = 0
            wave.clear()
            var ticks = 0
            while (audioOn) {
                delay(80)
                wave.add(recorder.level())
                if (wave.size > 40) wave.removeAt(0)
                if (++ticks % 12 == 0) seconds++
            }
        }
    }
    fun finishAudio() {
        audioOn = false
        val name = recorder.stop()
        if (name != null && seconds >= 1) {
            vm.addVoiceNote("Запись звука · " + String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), name)
            status = "Запись сохранена"
        } else {
            VoiceRecorder.delete(context, name)
            status = "Запись слишком короткая"
        }
        // Прослушивание «LifeOS» возвращается после записи
        if (vm.settings.value.wakeEnabled) AssistantControl.startListening(context)
    }
    val startAudio = rememberMicRequest {
        if (!audioOn && !listening) {
            // Микрофон один на всех: на время записи фоновое прослушивание отключается
            if (vm.settings.value.wakeEnabled) AssistantControl.stop(context)
            if (recorder.start()) {
                audioOn = true
                status = null
            } else {
                status = "Не удалось начать запись"
                if (vm.settings.value.wakeEnabled) AssistantControl.startListening(context)
            }
        }
    }

    val startRecording = rememberMicRequest {
        if (!listening) {
            listening = true
            partial = ""
            status = null
            scope.launch {
                when (val r = listener.listen(accurate = true) { partial = it }) {
                    is SpeechResult.Heard -> {
                        vm.addNote(r.text.trim())
                        status = "Записано"
                    }
                    SpeechResult.Silence -> status = "Ничего не услышано"
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

        InfoCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            if (audioOn) {
                Text(
                    String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                    color = Snow,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Canvas(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 6.dp)) {
                    val n = 40
                    val step = size.width / n
                    wave.forEachIndexed { i, v ->
                        val h = (size.height * (0.1f + 0.9f * v)).coerceAtMost(size.height)
                        val x = step * (n - wave.size + i) + step / 2
                        drawLine(accent, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = step * 0.6f, cap = StrokeCap.Round)
                    }
                }
                PillButton("Остановить и сохранить", { finishAudio() }, Modifier.fillMaxWidth())
            } else {
                Text("Запись звука", color = Snow, style = MaterialTheme.typography.titleMedium)
                Text("Сохранит аудио, которое потом можно прослушать. Для текста используйте микрофон выше.", color = GrayText, style = MaterialTheme.typography.bodySmall)
                PillButton("Записать звук", { startAudio() }, Modifier.fillMaxWidth().padding(top = 8.dp), outlined = true, enabled = !listening)
            }
        }

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
                    if (n.audioPath != null) {
                        val playing = playingId == n.id
                        IconButton(onClick = {
                            if (playing) {
                                player.stop()
                                playingId = null
                            } else if (player.play(n.audioPath) { playingId = null }) {
                                playingId = n.id
                            } else {
                                status = "Файла записи нет на этом телефоне"
                            }
                        }) {
                            Icon(
                                if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                if (playing) "Остановить" else "Воспроизвести",
                                tint = accent,
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (playingId == n.id) {
                            player.stop()
                            playingId = null
                        }
                        vm.deleteNote(n)
                    }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
                }
            }
        }
    }
}
