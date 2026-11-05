package com.lifeos.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.Command
import com.lifeos.app.assistant.SpeechListener
import com.lifeos.app.assistant.SpeechResult
import com.lifeos.app.data.Rarity
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.data.VoiceType
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Perms
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.rememberMicRequest
import com.lifeos.app.ui.rememberResumeTick
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ChatMsg(val fromUser: Boolean, val text: String)

// ---------------------------------------------------------------- Чат с помощником

@Composable
fun AssistantChatScreen(vm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    val listener = remember { SpeechListener(context) }
    DisposableEffect(Unit) { onDispose { listener.cancel() } }
    val listState = rememberLazyListState()
    val messages = remember {
        mutableStateListOf(
            ChatMsg(false, "Привет! Спроси что-нибудь или скажи, например: «поставь будильник на 7:30», «запиши молоко в покупки»."),
        )
    }
    var input by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf(RobotMood.HAPPY) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    fun send(text: String, speak: Boolean) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val history = messages.map { (if (it.fromUser) "user" else "assistant") to it.text }
        messages += ChatMsg(true, t)
        busy = true
        mood = RobotMood.THINKING
        scope.launch {
            val (cmd, res) = vm.talk(t, history)
            if (cmd is Command.SetAlarm || cmd is Command.SetTimer) {
                mood = RobotMood.WORKING
                delay(1500)
            }
            messages += ChatMsg(false, res.reply)
            mood = RobotMood.TALKING
            if (speak) vm.speak(res.reply)
            delay(1800)
            mood = RobotMood.HAPPY
            busy = false
        }
    }

    val startVoice = rememberMicRequest {
        if (!listening && !busy) {
            listening = true
            mood = RobotMood.LISTENING
            scope.launch {
                when (val r = listener.listen { input = it }) {
                    is SpeechResult.Heard -> {
                        input = ""
                        listening = false
                        send(r.text, speak = true)
                    }
                    else -> {
                        input = ""
                        listening = false
                        mood = RobotMood.HAPPY
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.lastIndex) }

    LifeScreen(
        settings.assistantName,
        onBack = onBack,
        scroll = false,
        actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Настройки помощника", tint = Snow) }
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RobotView(SkinCatalog.byId(settings.skinId), mood, Modifier.size(120.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (m.fromUser) Modifier.background(accent)
                                else Modifier.background(CardColor),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(m.text, color = Snow, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { LifeField(input, { input = it }, if (listening) "Говорите…" else "Напишите сообщение") }
            IconButton(onClick = { if (listening) listener.cancel() else startVoice() }) {
                Icon(Icons.Rounded.Mic, "Голос", tint = if (listening) accent else GrayText)
            }
            IconButton(
                onClick = {
                    send(input, speak = false)
                    input = ""
                },
                enabled = input.isNotBlank() && !busy,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Отправить", tint = if (input.isNotBlank()) accent else GrayText)
            }
        }
    }
}

// ---------------------------------------------------------------- Настройки помощника

@Composable
fun AssistantSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val accent = LocalAccent.current
    val tick = rememberResumeTick()
    val overlayOk = remember(tick) { Perms.canOverlay(context) }
    val watcherOk = remember(tick) { Perms.watcherEnabled(context) }

    var name by remember(s.assistantName) { mutableStateOf(s.assistantName) }
    var key by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var pitch by remember(s.customPitch) { mutableStateOf(s.customPitch) }
    var rate by remember(s.customRate) { mutableStateOf(s.customRate) }

    val enableWake = rememberMicRequest {
        vm.update { it.copy(wakeEnabled = true) }
        AssistantControl.startListening(context)
    }
    val callNow = rememberMicRequest { AssistantControl.summon(context) }

    LifeScreen("Настройки помощника", onBack = onBack) {
        SectionTitle("Имя")
        Text(
            "Помощник откликается на «LifeOS», «OS» и на имя, которое вы ему дадите.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LifeField(name, { name = it }, "Имя помощника")
        PillButton(
            "Сохранить имя",
            { vm.update { it.copy(assistantName = name.trim().ifBlank { "LifeOS" }) } },
            Modifier.padding(top = 8.dp),
            enabled = name.trim().isNotBlank() && name.trim() != s.assistantName,
            outlined = true,
        )

        SectionTitle("Голос")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeChip("Женский", s.voiceType == VoiceType.FEMALE, { vm.update { it.copy(voiceType = VoiceType.FEMALE) } })
            LifeChip("Мужской", s.voiceType == VoiceType.MALE, { vm.update { it.copy(voiceType = VoiceType.MALE) } })
            LifeChip("Свой", s.voiceType == VoiceType.CUSTOM, { vm.update { it.copy(voiceType = VoiceType.CUSTOM) } })
        }
        if (s.voiceType == VoiceType.CUSTOM) {
            Text("Тон", color = GrayText, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = pitch, onValueChange = { pitch = it },
                onValueChangeFinished = { vm.update { it.copy(customPitch = pitch) } },
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            )
            Text("Скорость", color = GrayText)
            Slider(
                value = rate, onValueChange = { rate = it },
                onValueChangeFinished = { vm.update { it.copy(customRate = rate) } },
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            )
            val voices = remember(tick) { vm.c.tts.russianVoices() }
            if (voices.isNotEmpty()) {
                Text("Голос синтезатора", color = GrayText, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    voices.take(12).forEachIndexed { i, v ->
                        LifeChip("Голос ${i + 1}", s.customVoiceName == v, { vm.update { it.copy(customVoiceName = v) } })
                    }
                }
            }
        }
        PillButton(
            "Проверить голос",
            { vm.speak("Привет! Я ${s.assistantName}, твой помощник.") },
            Modifier.padding(top = 12.dp),
            outlined = true,
        )

        SectionTitle("Вызов")
        SwitchRow(
            "Слушать «LifeOS» в фоне",
            s.wakeEnabled,
            { on ->
                if (on) {
                    enableWake()
                } else {
                    vm.update { it.copy(wakeEnabled = false) }
                    AssistantControl.stop(context)
                }
            },
            subtitle = "Помощник отзывается на голос, даже когда экран выключен. В шторке появится уведомление.",
        )
        SwitchRow(
            "Показывать робота на экране",
            s.robotEnabled,
            { on -> vm.update { it.copy(robotEnabled = on) } },
            subtitle = "Без этого помощник только отвечает голосом.",
        )
        PillButton("Позвать сейчас", { callNow() }, Modifier.padding(top = 8.dp), outlined = true)

        SectionTitle("Разрешения для робота")
        PermissionRow(
            "Поверх других приложений",
            "Нужно, чтобы робот появлялся на экране и чтобы будильник ставился из фона.",
            overlayOk,
        ) { context.startActivity(Perms.overlaySettings(context)) }
        PermissionRow(
            "Робот видит экран",
            "Необязательно. Робот будет уходить с кнопок и полей ввода. Тексты не читаются.",
            watcherOk,
        ) { context.startActivity(Perms.accessibilitySettings()) }

        SectionTitle("Разговор с ИИ")
        Text(
            "Чтобы помощник отвечал на любые вопросы, вставьте ключ Anthropic API. Он хранится только на этом телефоне.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LifeField(key, { key = it }, "Ключ ИИ", password = true)
        PillButton(
            "Сохранить ключ",
            { vm.update { it.copy(apiKey = key.trim()) } },
            Modifier.padding(top = 8.dp),
            enabled = key.trim() != s.apiKey,
            outlined = true,
        )

        SectionTitle("Заметка с выключенным экраном")
        Text(
            "Потяните шторку, нажмите «изменить» и добавьте плитку «Голосовая заметка»: один тап, и телефон слушает " +
                "(работает и на экране блокировки). Либо включите прослушивание выше и скажите: «LifeOS, запиши …».",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun PermissionRow(title: String, hint: String, granted: Boolean, onOpen: () -> Unit) {
    InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, color = Snow, style = MaterialTheme.typography.titleMedium)
                Text(hint, color = GrayText, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) LabelPill("включено") else PillButton("Открыть", onOpen)
        }
    }
}

// ---------------------------------------------------------------- Магазин роботов

@Composable
fun RobotShopScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val accent = LocalAccent.current
    var message by remember { mutableStateOf<String?>(null) }

    LifeScreen("Магазин роботов", onBack = onBack) {
        if (vm.c.purchases.isDemo) {
            InfoCard(Modifier.fillMaxWidth()) {
                Text("Демо-режим", color = accent, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Оплата ещё не подключена: платные роботы выдаются бесплатно, чтобы проверить магазин.",
                    color = GrayText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        message?.let { Text(it, color = accent, modifier = Modifier.padding(top = 10.dp)) }

        SkinCatalog.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { skin ->
                    val owned = skin.id in s.ownedSkins
                    val selected = s.skinId == skin.id
                    val shape = RoundedCornerShape(22.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(CardColor)
                            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RobotView(skin, if (selected) RobotMood.HAPPY else RobotMood.THINKING, Modifier.size(110.dp))
                        Text(skin.name, color = Snow, style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LabelPill(skin.rarity.label, filled = skin.rarity != Rarity.FREE)
                        }
                        skin.edition?.let { Text(it, color = GrayText, fontSize = 11.sp) }
                        PillButton(
                            text = when {
                                selected -> "Выбран"
                                owned -> "Выбрать"
                                skin.priceRub == 0 -> "Взять"
                                else -> "${skin.priceRub} ₽"
                            },
                            onClick = { vm.buyOrSelect(skin) { message = it } },
                            enabled = !selected,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        EmptyHint("Новые и лимитированные роботы появятся здесь позже.")
    }
}
