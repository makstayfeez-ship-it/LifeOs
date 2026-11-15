package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.theme.CardBorder
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.HeroCard
import com.lifeos.app.ui.theme.LifePalette
import com.lifeos.app.ui.theme.LifeStyle
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.OnHero
import com.lifeos.app.ui.theme.OnHeroGray
import com.lifeos.app.ui.theme.Snow
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.onAccent

/** Раздел на экране «Приложения». */
internal class AppItem(val label: String, val icon: ImageVector, val route: String)

private class AppSection(val title: String, val items: List<AppItem>)

private val AppSections = listOf(
    AppSection(
        "Планирование",
        listOf(
            AppItem("Календарь", Icons.Rounded.CalendarMonth, "calendar"),
            AppItem("Задачи", Icons.Rounded.CheckCircle, "calendar"),
            AppItem("Привычки", Icons.Rounded.Loop, "habits"),
        ),
    ),
    AppSection(
        "Деньги и быт",
        listOf(
            AppItem("Финансы", Icons.Rounded.AccountBalanceWallet, "finance"),
            AppItem("Покупки", Icons.Rounded.ShoppingCart, "shopping"),
            AppItem("Заметки", Icons.Rounded.StickyNote2, "notes"),
        ),
    ),
    AppSection(
        "Помощник",
        listOf(
            AppItem("ИИ-помощник", Icons.Rounded.AutoAwesome, "assistant"),
            AppItem("Утренний обзор", Icons.Rounded.WbSunny, "morning"),
            AppItem("Цели на год", Icons.Rounded.Flag, "goals"),
            AppItem("Магазин роботов", Icons.Rounded.Storefront, "shop"),
        ),
    ),
    AppSection(
        "Система",
        listOf(
            AppItem("Оформление", Icons.Rounded.Palette, "theme"),
            AppItem("Виджеты", Icons.Rounded.Widgets, "widgets"),
            AppItem("Безопасность", Icons.Rounded.Lock, "security"),
            AppItem("Уведомления", Icons.Rounded.Notifications, "reminders"),
            AppItem("Профиль", Icons.Rounded.Person, "profile"),
            AppItem("Аккаунт", Icons.Rounded.Settings, "account"),
        ),
    ),
)

private val AllApps: List<AppItem> = AppSections.flatMap { it.items }

private fun findApps(query: String): List<AppItem> {
    val q = query.trim().lowercase()
    return AllApps.filter { it.label.lowercase().contains(q) }
}

private val PastelBlue = Color(0xFF2F7BFF)
private val PastelGreen = Color(0xFF1FA55B)
private val PastelAmber = Color(0xFFE08A00)
private val PastelPurple = Color(0xFF8B4FE0)
private val PastelRed = Color(0xFFD8453B)
private val PastelTeal = Color(0xFF17A2B8)

/** Цвет значка раздела в светлом стиле (у каждого раздела свой). */
internal fun pastelFor(label: String): Color = when (label) {
    "Календарь", "Заметки", "Уведомления", "Профиль" -> PastelBlue
    "Задачи", "Утренний обзор" -> PastelGreen
    "Финансы", "ИИ-помощник", "Магазин роботов" -> PastelAmber
    "Покупки", "Оформление" -> PastelPurple
    "Привычки", "Виджеты" -> PastelRed
    "Цели на год", "Безопасность", "Аккаунт" -> PastelTeal
    else -> PastelBlue
}

@Composable
fun AppsScreen(go: (String) -> Unit) {
    when (LifePalette.style) {
        LifeStyle.DARK -> AppsDark(go)
        LifeStyle.WARM -> AppsWarm(go)
        LifeStyle.LIGHT -> AppsLight(go)
    }
}

/** Строка поиска по разделам. */
@Composable
private fun SearchBox(
    query: String,
    onChange: (String) -> Unit,
    placeholder: String,
    container: Color,
    bordered: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (bordered) Modifier.border(1.dp, CardBorder, shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = GrayText, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Snow, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text(placeholder, color = GrayText, fontSize = 16.sp)
                    inner()
                }
            },
        )
    }
}

// ------------------------------------------------------------------ 1. Тёмный

@Composable
private fun AppsDark(go: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    LifeScreen("Приложения") {
        SearchBox(query, { query = it }, "Найти раздел", CardColor, bordered = true)
        if (query.isBlank()) {
            AppSections.forEach { sec ->
                Text(
                    sec.title.uppercase(),
                    color = GrayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 2.dp),
                )
                DarkGrid(sec.items, go)
            }
        } else {
            val found = findApps(query)
            Spacer(Modifier.height(14.dp))
            if (found.isEmpty()) EmptyHint("Такого раздела нет") else DarkGrid(found, go)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DarkGrid(items: List<AppItem>, go: (String) -> Unit) {
    val accent = LocalAccent.current
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { item ->
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .height(88.dp)
                        .clip(shape)
                        .background(CardColor)
                        .border(1.dp, CardBorder, shape)
                        .clickable { go(item.route) }
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = accent, modifier = Modifier.size(26.dp))
                    Text(
                        item.label,
                        color = Snow,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

// ------------------------------------------------------------------ 2. Тёплый

@Composable
private fun AppsWarm(go: (String) -> Unit) {
    val accent = LocalAccent.current
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().lifeBackground().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Все разделы", color = GrayText, style = MaterialTheme.typography.bodyMedium)
                Text("Приложения", color = Snow, style = MaterialTheme.typography.headlineLarge)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HeroCard)
                    .clickable {
                        searching = !searching
                        if (!searching) query = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Поиск", tint = OnHero, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (searching) {
                SearchBox(query, { query = it }, "Найти раздел", CardColor, bordered = true, modifier = Modifier.padding(bottom = 12.dp))
            }
            val searchingNow = searching && query.isNotBlank()
            if (!searchingNow) {
                // Помощник — главная карточка экрана
                val heroShape = RoundedCornerShape(26.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(heroShape)
                        .background(HeroCard)
                        .clickable { go("assistant") }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = onAccent(accent), modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("ИИ-помощник", color = OnHero, style = MaterialTheme.typography.titleMedium)
                        Text("Спроси или скажи голосом", color = OnHeroGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            val list = if (searchingNow) findApps(query) else AllApps.filter { it.route != "assistant" }
            if (list.isEmpty()) EmptyHint("Такого раздела нет")
            list.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item -> WarmCell(item, go, Modifier.weight(1f)) }
                    repeat(2 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WarmCell(item: AppItem, go: (String) -> Unit, modifier: Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(CardColor)
            .border(1.dp, CardBorder, shape)
            .clickable { go(item.route) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = onAccent(accent), modifier = Modifier.size(19.dp))
        }
        Text(
            item.label,
            color = Snow,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
    }
}

// ------------------------------------------------------------------ 3. Светлый

@Composable
private fun AppsLight(go: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().lifeBackground().statusBarsPadding()) {
        Text(
            "Приложения",
            color = Snow,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SearchBox(query, { query = it }, "Поиск", CardBorder, bordered = false)
            Spacer(Modifier.height(14.dp))
            val list = if (query.isBlank()) AllApps else findApps(query)
            if (list.isEmpty()) {
                EmptyHint("Такого раздела нет")
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardColor).padding(horizontal = 8.dp, vertical = 14.dp)) {
                    list.chunked(4).forEachIndexed { i, row ->
                        Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { item ->
                                PastelTile(item.label, item.icon, pastelFor(item.label), { go(item.route) }, Modifier.weight(1f))
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
