package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.data.Goal
import com.lifeos.app.data.TaskEntity
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.theme.CardBorder
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.Disabled
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.HeroCard
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.OnHero
import com.lifeos.app.ui.theme.OnHeroGray
import com.lifeos.app.ui.theme.Snow
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.onAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Главный экран в трёх стилях. Данные и действия одни и те же ([HomeState]), различается только внешний вид:
 * тёмный (основной), тёплый с оранжевой карточкой и светлый со списком дел.
 */
internal class HomeState(
    val name: String,
    val todays: List<TaskEntity>,
    val events: Int,
    val open: Int,
    val spent: Long,
    val habitsDone: Int,
    val habitsTotal: Int,
    val notesCount: Int,
    val goal: Goal?,
    val blocks: Set<String>,
    val notesButton: Boolean,
    val problems: Int,
    val go: (String) -> Unit,
    val toggleTask: (TaskEntity) -> Unit,
    val onVoice: () -> Unit,
    val onText: () -> Unit,
    val onRemind: () -> Unit,
    val onQuickNote: () -> Unit,
    val onPrompt: (String) -> Unit,
    val onAdd: () -> Unit,
) {
    /** Сегодняшние дела: невыполненные по времени, выполненные в конце. */
    val sorted: List<TaskEntity> =
        todays.sortedWith(compareBy<TaskEntity> { it.done }.thenBy { it.timeMinutes ?: Int.MAX_VALUE })

    val habitsValue: String get() = if (habitsTotal == 0) "—" else "$habitsDone из $habitsTotal"
}

private val HomeChips = listOf("Будильник на 7:30", "Запиши молоко", "Что у меня сегодня?")

internal fun greetingWord(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..11 -> "Доброе утро,"
        in 12..17 -> "Добрый день,"
        in 18..22 -> "Добрый вечер,"
        else -> "Доброй ночи,"
    }
}

private fun dateLine(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() }

private fun initial(name: String): String = name.firstOrNull()?.uppercase() ?: "L"

/** Каркас главной: фон стиля, прокрутка и (по желанию) то, что лежит поверх, например кнопка «+». */
@Composable
private fun HomeColumn(overlay: @Composable BoxScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().lifeBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
        overlay()
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (bordered) Modifier.border(1.dp, CardBorder, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Кнопка быстрой голосовой заметки в шапке (если включена в настройках). */
@Composable
private fun NoteButton(st: HomeState, bg: Color, tint: Color, bordered: Boolean) {
    if (!st.notesButton) return
    RoundButton(
        Icons.Rounded.StickyNote2, "Быстрая голосовая заметка", bg, tint, st.onQuickNote,
        Modifier.padding(end = 8.dp), bordered,
    )
}

@Composable
private fun ProblemsCard(st: HomeState) {
    if (st.problems <= 0) return
    OutlineCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), onClick = { st.go("assistant_check") }) {
        Text("Помощник пока не готов", color = Snow, style = MaterialTheme.typography.titleMedium)
        Text(
            "Не хватает: ${st.problems}. Нажмите, чтобы проверить",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ------------------------------------------------------------------ общие маленькие части

/** Плитка с числом: «Расходы сегодня», «Привычки». [boxed]: значок в цветном квадрате (тёплый стиль). */
@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier,
    boxed: Boolean,
) {
    val accent = LocalAccent.current
    InfoCard(modifier, onClick = onClick) {
        if (boxed) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = onAccent(accent), modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Text(label, color = GrayText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 14.dp))
        Text(
            value,
            color = Snow,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class Kind { DARK, WARM, LIGHT }

/** Строка-ссылка («Утренний обзор», «Цель на год», «Заметки»): форма зависит от стиля. */
@Composable
private fun LinkRow(
    kind: Kind,
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(if (kind == Kind.LIGHT) 18.dp else 24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardColor)
            .then(if (kind == Kind.LIGHT) Modifier else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (kind) {
            Kind.DARK -> Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            Kind.WARM -> Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = onAccent(accent), modifier = Modifier.size(20.dp))
            }
            Kind.LIGHT -> Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Snow, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GrayText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GrayText)
    }
}

/** Цель, заметки и утренний обзор: одинаковый набор блоков для всех стилей. Блоки включаются в настройках главной. */
@Composable
private fun ExtraBlocks(st: HomeState, kind: Kind) {
    if ("notes" in st.blocks && kind != Kind.LIGHT) {
        LinkRow(
            kind, Icons.Rounded.StickyNote2, pastelFor("Заметки"), "Заметки",
            if (st.notesCount == 0) "Пока пусто" else Fmt.plural(st.notesCount, "заметка", "заметки", "заметок"),
            { st.go("notes") }, Modifier.padding(top = 12.dp),
        )
    }
    if ("goal" in st.blocks) {
        val g = st.goal
        val ring: (@Composable () -> Unit)? = if (g != null && g.hasTarget) {
            { ProgressRing(g.fraction, "${g.percent}%", Modifier.size(48.dp), stroke = 5f) }
        } else {
            null
        }
        LinkRow(
            kind, Icons.Rounded.Flag, pastelFor("Цели на год"), "Цель на год",
            g?.let { it.emoji + " " + it.title } ?: "Поставьте цель на год",
            { st.go("goals") }, Modifier.padding(top = 12.dp),
            trailing = ring,
        )
    }
    if ("morning" in st.blocks) {
        LinkRow(
            kind, Icons.Rounded.WbSunny, pastelFor("Утренний обзор"), "Утренний обзор",
            "Погода, дела и расходы", { st.go("morning") }, Modifier.padding(top = 12.dp),
        )
    }
}

/** Плитка раздела с цветным квадратом: значок и подпись под ним (светлый стиль). */
@Composable
internal fun PastelTile(label: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            color = Snow,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ------------------------------------------------------------------ 1. Тёмный (основной)

@Composable
internal fun HomeDark(st: HomeState) {
    val accent = LocalAccent.current
    val today = LocalDate.now()
    HomeColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greetingWord(), color = GrayText, style = MaterialTheme.typography.bodyMedium)
                Text(st.name, color = Snow, style = MaterialTheme.typography.headlineLarge)
            }
            NoteButton(st, CardColor, accent, bordered = true)
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(CardColor).border(1.dp, CardBorder, CircleShape).clickable { st.go("profile") },
                contentAlignment = Alignment.Center,
            ) {
                Text(initial(st.name), color = accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        ProblemsCard(st)

        // Строка «Спросить LifeOS»: нажатие открывает строку для текста, кнопка справа зовёт голосом
        val askShape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(askShape)
                .background(CardColor)
                .border(1.dp, CardBorder, askShape)
                .clickable(onClick = st.onText)
                .padding(start = 18.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(20.dp))
            Text("Спросить LifeOS…", color = GrayText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
            AssistantCallButton(accent, st.onVoice, st.onText, st.onRemind, size = 46.dp)
        }
        Row(
            Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeChips.forEach { chip -> LifeChip(chip, false, { st.onPrompt(chip) }) }
        }

        if ("tasks" in st.blocks) {
            InfoCard(Modifier.fillMaxWidth().padding(top = 14.dp), onClick = { st.go("calendar") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Сегодня · ${Fmt.dayMonth(today)}", color = GrayText, modifier = Modifier.weight(1f))
                    Text("Все", color = accent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    Fmt.plural(st.todays.size, "дело", "дела", "дел"),
                    color = Snow,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                if (st.sorted.isEmpty()) {
                    Text("На сегодня пусто. Добавьте дело кнопкой «+»", color = GrayText, style = MaterialTheme.typography.bodyMedium)
                }
                st.sorted.take(4).forEachIndexed { i, t -> TaskLine(t, highlight = i == 0 && !t.done) }
                if (st.sorted.size > 4) {
                    Text("и ещё ${st.sorted.size - 4}", color = GrayText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        StatRow(st, Kind.DARK)
        ExtraBlocks(st, Kind.DARK)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TaskLine(t: TaskEntity, highlight: Boolean) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            t.timeMinutes?.let { Fmt.time(it) } ?: "",
            color = GrayText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(54.dp),
        )
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (highlight) accent else Disabled))
        Text(
            t.title,
            color = if (t.done) GrayText else Snow,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (t.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

/** Две плитки: расходы за сегодня и привычки (по включённым блокам). */
@Composable
private fun StatRow(st: HomeState, kind: Kind) {
    val finance = "finance" in st.blocks
    val habits = "habits" in st.blocks
    if (!finance && !habits) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (finance) {
            StatTile(
                Icons.Rounded.AccountBalanceWallet, "Расходы сегодня", Fmt.money(st.spent),
                { st.go("finance") }, Modifier.weight(1f), boxed = kind == Kind.WARM,
            )
        }
        if (habits) {
            StatTile(
                Icons.Rounded.Loop, "Привычки", st.habitsValue,
                { st.go("habits") }, Modifier.weight(1f), boxed = kind == Kind.WARM,
            )
        }
    }
}

// ------------------------------------------------------------------ 2. Тёплый

@Composable
internal fun HomeWarm(st: HomeState) {
    val accent = LocalAccent.current
    HomeColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateLine(LocalDate.now()), color = GrayText, style = MaterialTheme.typography.bodyMedium)
                Text("Привет, ${st.name}", color = Snow, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NoteButton(st, CardColor, accent, bordered = true)
            RoundButton(Icons.Rounded.Add, "Быстро добавить", HeroCard, OnHero, st.onAdd)
        }
        Spacer(Modifier.height(16.dp))
        ProblemsCard(st)

        if ("tasks" in st.blocks) {
            val on = onAccent(accent)
            val next = st.sorted.firstOrNull { !it.done }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(accent)
                    .clickable { st.go("calendar") }
                    .padding(20.dp),
            ) {
                Text("Сегодня", color = on, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    Fmt.plural(st.todays.size, "дело", "дела", "дел"),
                    color = on,
                    fontSize = 52.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "${Fmt.plural(st.events, "событие", "события", "событий")} · ${Fmt.plural(st.open, "задача", "задачи", "задач")}",
                    color = on.copy(alpha = 0.85f),
                )
                if (next != null) {
                    Box(
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            (next.timeMinutes?.let { Fmt.time(it) + " · " } ?: "") + next.title,
                            color = Color(0xFF1A1613),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Тёмная карточка «Спросить LifeOS»
        val askShape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(askShape)
                .background(HeroCard)
                .clickable(onClick = st.onText)
                .padding(start = 18.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Спросить LifeOS", color = OnHero, style = MaterialTheme.typography.titleMedium)
                Text("Скажи: «запиши молоко»", color = OnHeroGray, style = MaterialTheme.typography.bodySmall)
            }
            AssistantCallButton(accent, st.onVoice, st.onText, st.onRemind, size = 48.dp)
        }

        StatRow(st, Kind.WARM)
        ExtraBlocks(st, Kind.WARM)
        Spacer(Modifier.height(12.dp))
    }
}

// ------------------------------------------------------------------ 3. Светлый

@Composable
internal fun HomeLight(st: HomeState) {
    val accent = LocalAccent.current
    HomeColumn(
        overlay = {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .shadow(8.dp, CircleShape)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(onClick = st.onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Быстро добавить", tint = onAccent(accent), modifier = Modifier.size(28.dp))
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateLine(LocalDate.now()), color = GrayText, style = MaterialTheme.typography.bodyMedium)
                Text("Главная", color = Snow, style = MaterialTheme.typography.headlineLarge)
            }
            NoteButton(st, CardColor, accent, bordered = false)
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(accent).clickable { st.go("profile") },
                contentAlignment = Alignment.Center,
            ) {
                Text(initial(st.name), color = onAccent(accent), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        ProblemsCard(st)

        val askShape = RoundedCornerShape(22.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(askShape)
                .background(CardColor)
                .clickable(onClick = st.onText)
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(20.dp))
            Text("Спросите LifeOS", color = GrayText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
            AssistantCallButton(
                accent, st.onVoice, st.onText, st.onRemind,
                size = 40.dp, bg = accent.copy(alpha = 0.14f), tint = accent,
            )
        }

        if ("tasks" in st.blocks) {
            Text("Сегодня", color = GrayText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardColor)) {
                if (st.sorted.isEmpty()) {
                    Text("На сегодня дел нет", color = GrayText, modifier = Modifier.padding(16.dp))
                }
                st.sorted.take(5).forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(CardBorder))
                    Row(
                        Modifier.fillMaxWidth().clickable { st.go("calendar") }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (t.done) accent else Color.Transparent)
                                .border(2.dp, if (t.done) accent else GrayText, CircleShape)
                                .clickable { st.toggleTask(t) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t.done) Icon(Icons.Rounded.Check, null, tint = onAccent(accent), modifier = Modifier.size(16.dp))
                        }
                        Text(
                            t.title,
                            color = if (t.done) GrayText else Snow,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        )
                        t.timeMinutes?.let { Text(Fmt.time(it), color = GrayText, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (st.sorted.size > 5) {
                    Text(
                        "и ещё ${st.sorted.size - 5}",
                        color = GrayText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { st.go("calendar") }.padding(start = 56.dp, bottom = 12.dp, top = 2.dp),
                    )
                }
            }
        }

        // Быстрые переходы: календарь всегда, остальное по включённым блокам
        val tiles = buildList {
            add(Triple("Календарь", Icons.Rounded.CalendarMonth, "calendar"))
            if ("habits" in st.blocks) add(Triple("Привычки", Icons.Rounded.Loop, "habits"))
            if ("finance" in st.blocks) add(Triple("Финансы", Icons.Rounded.AccountBalanceWallet, "finance"))
            if ("notes" in st.blocks) add(Triple("Заметки", Icons.Rounded.StickyNote2, "notes"))
        }
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardColor)
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tiles.forEach { (label, icon, route) ->
                PastelTile(label, icon, pastelFor(label), { st.go(route) }, Modifier.weight(1f))
            }
        }

        ExtraBlocks(st, Kind.LIGHT)
        // Место под круглую кнопку «+»
        Spacer(Modifier.height(84.dp))
    }
}
