package com.lifeos.app.ui.screens

import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.AssistantStatus
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.RobotSkin
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.data.VoiceType
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.rememberMicRequest
import com.lifeos.app.ui.rememberResumeTick
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.AccentChoices
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.CardBorder
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LifeStyle
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.onAccent
import androidx.compose.ui.graphics.vector.ImageVector

private const val STEPS = 6

/** Канонический порядок блоков главного экрана. */
private val HomeKeys = listOf("tasks", "habits", "finance", "notes", "goal", "morning")

private val BotLines = listOf(
    "Привет! Я твой помощник. Давай настроим меня и проверим",
    "Заметки — это быстро. Попробуй сказать",
    "Я напомню вовремя. Скажи, о чём",
    "Выбери, что видеть на главной",
    "Когда показывать утренний обзор?",
    "Почти готово! Выбери вид",
)

/** С какой стороны от робота стоит реплика на каждом шаге. */
private val BubbleLeft = listOf(true, false, true, false, true, true)

private val DayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Какие блоки главного экрана включены. [s] — строка из настроек через запятую. */
fun homeBlockSet(s: String): Set<String> =
    s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/** Включает или выключает блок и возвращает новую строку настроек в каноническом порядке. */
fun homeBlockToggle(s: String, key: String, on: Boolean): String {
    val set = homeBlockSet(s).toMutableSet()
    if (on) set += key else set -= key
    return HomeKeys.filter { it in set }.joinToString(",")
}

/**
 * Знакомство при первом входе: шесть коротких шагов с настройкой и объяснением, как всё работает.
 * «Пропустить» и «Начать» отмечают знакомство пройденным и вызывают [onFinish].
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onFinish: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val accent = LocalAccent.current
    var step by remember { mutableIntStateOf(0) }

    fun finish() {
        vm.update { it.copy(onboardingDone = true) }
        onFinish()
    }

    fun next() {
        if (step >= STEPS - 1) finish() else step += 1
    }

    fun prev() {
        if (step > 0) step -= 1
    }

    BackHandler(enabled = step > 0) { prev() }

    Column(
        Modifier
            .fillMaxSize()
            .lifeBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Шаг ${step + 1} из $STEPS", color = GrayText, fontSize = 14.sp)
            Text(
                "Пропустить",
                color = GrayText,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { finish() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) accent else CardBorder),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { w -> dir * w / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -dir * w / 3 } + fadeOut())
            },
            label = "onboarding-step",
        ) { s ->
            StepPage(
                step = s,
                vm = vm,
                settings = settings,
                onNext = { next() },
                onPrev = { prev() },
            )
        }

        PillButton(
            if (step == STEPS - 1) "Начать" else "Дальше",
            { next() },
            Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(
            if (step == STEPS - 1) "Всё можно поменять в Профиле" else "Свайпни карточку, и я её подтолкну →",
            color = GrayText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        )
    }
}

// ---------------------------------------------------------------- страница шага

@Composable
private fun StepPage(step: Int, vm: AppViewModel, settings: AppSettings, onNext: () -> Unit, onPrev: () -> Unit) {
    val skin = SkinCatalog.byId(settings.skinId)
    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        BotRow(BotLines[step], BubbleLeft[step], skin, if (step == 0) RobotMood.LISTENING else RobotMood.HAPPY)

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            // Две карточки за основной: «стопка», как в дизайне
            val back = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(44.dp)
                    .clip(back)
                    .background(CardColor)
                    .border(1.dp, CardBorder, back),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    .height(44.dp)
                    .clip(back)
                    .background(CardColor)
                    .border(1.dp, CardBorder, back),
            )

            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 22.dp)
                    .clip(shape)
                    .background(CardColor)
                    .border(1.dp, CardBorder, shape)
                    .pointerInput(step) {
                        // Свайп влево: дальше, вправо: назад
                        val threshold = 80.dp.toPx()
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -threshold) onNext() else if (drag > threshold) onPrev()
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                drag += amount
                            },
                        )
                    }
                    .padding(18.dp),
            ) {
                CardHeader(step)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (step) {
                        0 -> StepAssistant(vm, settings)
                        1 -> StepNotes(vm, settings)
                        2 -> StepReminders(vm, settings)
                        3 -> StepHome(vm, settings)
                        4 -> StepMorning(vm, settings)
                        else -> StepLook(vm, settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun BotRow(text: String, bubbleLeft: Boolean, skin: RobotSkin, mood: RobotMood) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!bubbleLeft) RobotView(skin, mood, Modifier.width(84.dp))
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(CardColor)
                .border(1.dp, CardBorder, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, color = Snow, fontSize = 15.sp)
        }
        if (bubbleLeft) RobotView(skin, mood, Modifier.width(84.dp))
    }
}

private class StepInfo(val icon: ImageVector, val title: String, val subtitle: String)

private val StepInfos = listOf(
    StepInfo(Icons.Rounded.AutoAwesome, "ИИ-помощник", "Настрой и проверь, как он работает"),
    StepInfo(Icons.Rounded.StickyNote2, "Заметки", "Диктуй мысли, пока заняты руки"),
    StepInfo(Icons.Rounded.Notifications, "Напоминания", "Не забудешь ни про что"),
    StepInfo(Icons.Rounded.Widgets, "Главный экран", "Что показывать сразу"),
    StepInfo(Icons.Rounded.WbSunny, "Утренний обзор", "Что и когда показывать"),
    StepInfo(Icons.Rounded.Palette, "Оформление", "Цвет и вид приложения"),
)

@Composable
private fun CardHeader(step: Int) {
    val accent = LocalAccent.current
    val info = StepInfos[step]
    val box = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(box).background(Bg).border(1.dp, CardBorder, box),
            contentAlignment = Alignment.Center,
        ) {
            Icon(info.icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(info.title, color = Snow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(info.subtitle, color = GrayText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = GrayText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

// ---------------------------------------------------------------- шаг 1: помощник

@Composable
private fun StepAssistant(vm: AppViewModel, s: AppSettings) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    var micGranted by remember(tick) { mutableStateOf(AssistantControl.hasMic(context)) }
    val enableMic = rememberMicRequest {
        micGranted = true
        vm.update { it.copy(wakeEnabled = true) }
        AssistantControl.startListening(context)
    }
    val status by AssistantStatus.state.collectAsState()

    Label("ГОЛОС")
    val voiceSel = when (s.voiceType) {
        VoiceType.FEMALE -> 0
        VoiceType.MALE -> 1
        VoiceType.CUSTOM -> 2
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LifeChip("Женский", voiceSel == 0, { vm.update { it.copy(voiceType = VoiceType.FEMALE) } }, Modifier.weight(1f))
        LifeChip("Мужской", voiceSel == 1, { vm.update { it.copy(voiceType = VoiceType.MALE) } }, Modifier.weight(1f))
        LifeChip(
            "Робот",
            voiceSel == 2,
            { vm.update { it.copy(voiceType = VoiceType.CUSTOM, customPitch = 0.6f, customRate = 0.95f) } },
            Modifier.weight(1f),
        )
    }

    Label("СЛОВО ВЫЗОВА")
    val preset = when (s.assistantName) {
        "LifeOS" -> 0
        "OS" -> 1
        else -> 2
    }
    var customMode by remember { mutableStateOf(preset == 2) }
    var customName by remember { mutableStateOf(if (preset == 2) s.assistantName else "") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LifeChip("LifeOS", !customMode && preset == 0, {
            customMode = false
            vm.update { it.copy(assistantName = "LifeOS") }
        })
        LifeChip("OS", !customMode && preset == 1, {
            customMode = false
            vm.update { it.copy(assistantName = "OS") }
        })
        LifeChip("+ своё", customMode, { customMode = true })
    }
    if (customMode) {
        Box(Modifier.padding(top = 10.dp)) {
            LifeField(
                customName,
                { v ->
                    customName = v
                    if (v.isNotBlank()) vm.update { st -> st.copy(assistantName = v.trim()) }
                },
                "Как тебя звать?",
            )
        }
    }

    Label("ПРОВЕРКА")
    if (!micGranted || !status.running) {
        PillButton("Включить микрофон", { enableMic() }, Modifier.fillMaxWidth())
        return
    }

    // Сколько проверок пройдено: 0 — ждём вызов, 1 — вызов услышан, 2 — услышана команда, 3 — услышан вопрос
    var progress by remember { mutableIntStateOf(0) }
    var counted by remember { mutableStateOf(status.lastHeard) }
    LaunchedEffect(status.lastHeard, status.updatedAt) {
        val heard = status.lastHeard
        if (status.running && heard.isNotBlank() && heard != counted) {
            counted = heard
            val alarm = heard.contains("будильник", ignoreCase = true)
            progress = when (progress) {
                0, 1 -> if (alarm) 2 else 1
                else -> 3
            }
        }
    }
    val name = s.assistantName.ifBlank { "LifeOS" }
    CheckRow("Позови: «$name»", rowState(0, progress))
    CheckRow("Скажи: «поставь будильник на 7:30»", rowState(1, progress))
    CheckRow("Задай любой вопрос", rowState(2, progress))
}

/** 0 — ещё не очередь, 1 — сейчас ждём, 2 — готово. */
private fun rowState(index: Int, progress: Int): Int = when {
    progress > index -> 2
    progress == index -> 1
    else -> 0
}

@Composable
private fun CheckRow(text: String, state: Int) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(Bg)
            .border(if (state == 1) 1.5.dp else 1.dp, if (state == 1) accent else CardBorder, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            2 -> Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = onAccent(accent), modifier = Modifier.size(16.dp))
            }
            1 -> Box(Modifier.size(28.dp).clip(CircleShape).border(1.5.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, null, tint = accent, modifier = Modifier.size(16.dp))
            }
            else -> Box(Modifier.size(28.dp).clip(CircleShape).border(1.5.dp, CardBorder, CircleShape))
        }
        Text(text, color = Snow, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        if (state == 2) Text("Слышу", color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (state == 1) Text("Жду", color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- шаги 2 и 3: примеры

@Composable
private fun SayRow(text: String) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(Bg)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Mic, null, tint = accent, modifier = Modifier.size(18.dp))
        Text(text, color = Snow, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun ResultCard(title: String, subtitle: String, icon: ImageVector?) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Bg)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = accent, modifier = Modifier.padding(end = 12.dp).size(24.dp))
        }
        Column {
            Text(title, color = Snow, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = GrayText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StepNotes(vm: AppViewModel, s: AppSettings) {
    Label("СКАЖИ ТАК")
    SayRow("«LifeOS, запиши заметку: купить молоко»")
    SayRow("«Добавь в заметки идею для проекта»")
    Label("ПОЛУЧИТСЯ ТАК")
    ResultCard("Купить молоко", "Только что · голосом", null)
    Box(Modifier.padding(top = 8.dp)) {
        SwitchRow("Кнопка блокнота на главной", s.notesButton, { on -> vm.update { it.copy(notesButton = on) } })
    }
}

@Composable
private fun StepReminders(vm: AppViewModel, s: AppSettings) {
    Label("СКАЖИ ТАК")
    SayRow("«Напомни через час позвонить маме»")
    SayRow("«Напомни завтра в 9 сдать отчёт»")
    Label("ПОЛУЧИТСЯ ТАК")
    ResultCard("Позвонить маме", "Через 1 час", Icons.Rounded.Notifications)
    Box(Modifier.padding(top = 8.dp)) {
        SwitchRow("Звук и вибрация", s.loudReminders, { on -> vm.update { it.copy(loudReminders = on) } })
    }
}

// ---------------------------------------------------------------- шаг 4: главный экран

@Composable
private fun StepHome(vm: AppViewModel, s: AppSettings) {
    val enabled = homeBlockSet(s.homeBlocks)
    Label("БЛОКИ НА ГЛАВНОЙ")
    val blocks = listOf(
        "tasks" to "Дела на сегодня",
        "habits" to "Привычки",
        "finance" to "Финансы",
        "notes" to "Заметки",
        "goal" to "Цель на год",
        "morning" to "Утренний обзор",
    )
    blocks.forEach { (key, title) ->
        SwitchRow(title, key in enabled, { on -> vm.update { st -> st.copy(homeBlocks = homeBlockToggle(st.homeBlocks, key, on)) } })
    }
}

// ---------------------------------------------------------------- шаг 5: утренний обзор

@Composable
private fun StepMorning(vm: AppViewModel, s: AppSettings) {
    val context = LocalContext.current
    val accent = LocalAccent.current

    fun morning(block: (AppSettings) -> AppSettings) {
        vm.updateMorning { st -> block(st).copy(morningEnabled = true) }
    }

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            String.format(java.util.Locale.ROOT, "%02d:%02d", s.morningHour, s.morningMinute),
            color = Snow,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    TimePickerDialog(
                        context,
                        { _, h, m -> morning { st -> st.copy(morningHour = h, morningMinute = m) } },
                        s.morningHour,
                        s.morningMinute,
                        true,
                    ).show()
                },
        )
        Text("показывать утром", color = GrayText, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
    }

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        DayNames.forEachIndexed { i, name ->
            val on = s.morningDays and (1 shl i) != 0
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else Bg)
                    .border(1.dp, if (on) accent else CardBorder, CircleShape)
                    .clickable {
                        val mask = s.morningDays xor (1 shl i)
                        // Хотя бы один день должен остаться
                        if (mask and 127 != 0) morning { st -> st.copy(morningDays = mask) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(name, color = if (on) onAccent(accent) else GrayText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    Label("В ОБЗОРЕ")
    SwitchRow("Погода", s.briefWeather, { on -> morning { st -> st.copy(briefWeather = on) } })
    SwitchRow("Дела", s.briefTasks, { on -> morning { st -> st.copy(briefTasks = on) } })
    SwitchRow("Расходы", s.briefSpending, { on -> morning { st -> st.copy(briefSpending = on) } })
    SwitchRow("Озвучить голосом", s.speakBrief, { on -> morning { st -> st.copy(speakBrief = on) } })
}

// ---------------------------------------------------------------- шаг 6: оформление

@Composable
private fun StepLook(vm: AppViewModel, s: AppSettings) {
    val context = LocalContext.current

    Label("ЦВЕТ")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AccentChoices.forEachIndexed { i, color ->
            val selected = s.accent == i
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(if (selected) Modifier.border(3.dp, Snow, CircleShape) else Modifier)
                    .clickable { vm.update { it.copy(accent = i) } },
            )
        }
    }

    Label("СТИЛЬ")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LifeStyle.entries.forEachIndexed { i, style ->
            LifeChip(
                style.title,
                s.designStyle == i,
                { vm.update { it.copy(designStyle = i, accent = LifeStyle.of(i).defaultAccent) } },
                Modifier.weight(1f),
            )
        }
    }

    Box(Modifier.padding(top = 8.dp)) {
        SwitchRow("Мягкое свечение", s.glow, { on -> vm.update { it.copy(glow = on) } })
    }
    SwitchRow("Показывать робота", s.robotEnabled, { on -> vm.update { it.copy(robotEnabled = on) } })

    PillButton(
        "Установить живые обои",
        {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, com.lifeos.app.wallpaper.LifeWallpaperService::class.java),
            )
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
        },
        Modifier.fillMaxWidth().padding(top = 10.dp),
        outlined = true,
    )
}
