package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.theme.AccentChoices
import com.lifeos.app.ui.theme.AccentNames
import com.lifeos.app.ui.theme.CardBorder
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.HeroCard
import com.lifeos.app.ui.theme.IsDarkStyle
import com.lifeos.app.ui.theme.LifePalette
import com.lifeos.app.ui.theme.LifeStyle
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.OnHero
import com.lifeos.app.ui.theme.OnHeroGray
import com.lifeos.app.ui.theme.Snow
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.onAccent
import com.lifeos.app.ui.theme.paletteOf

private class ProfileItem(val title: String, val icon: ImageVector, val route: String)

private val GroupMain = listOf(
    ProfileItem("Аккаунт", Icons.Rounded.Person, "account"),
    ProfileItem("Нижнее меню", Icons.Rounded.Apps, "navbar"),
    ProfileItem("Виджеты", Icons.Rounded.Widgets, "widgets"),
)
private val GroupAssistant = listOf(
    ProfileItem("Настройки помощника", Icons.Rounded.AutoAwesome, "assistant_settings"),
    ProfileItem("Магазин роботов", Icons.Rounded.Storefront, "shop"),
)
private val GroupSystem = listOf(
    ProfileItem("Оформление", Icons.Rounded.Palette, "theme"),
    ProfileItem("Безопасность", Icons.Rounded.Lock, "security"),
    ProfileItem("Как работает LifeOS", Icons.Rounded.Info, "welcome"),
)

@Composable
fun ProfileScreen(vm: AppViewModel, go: (String) -> Unit) {
    val user by vm.user.collectAsState()
    val settings by vm.settings.collectAsState()
    val name = user?.name.orEmpty()
    val email = user?.email.orEmpty()
    val cloud = user?.cloud == true
    val setAccent: (Int) -> Unit = { i -> vm.update { it.copy(accent = i) } }
    when (LifePalette.style) {
        LifeStyle.DARK -> ProfileDark(name, email, cloud, go)
        LifeStyle.WARM -> ProfileWarm(name, cloud, settings.accent, setAccent, go)
        LifeStyle.LIGHT -> ProfileLight(name, cloud, settings.accent, go)
    }
}

private fun letter(name: String): String = name.firstOrNull()?.uppercase() ?: "L"

@Composable
private fun ProfileColumn(horizontal: Alignment.Horizontal = Alignment.Start, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .lifeBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = horizontal,
        content = content,
    )
}

@Composable
private fun Avatar(name: String, size: Dp, textSize: Int) {
    val accent = LocalAccent.current
    Box(Modifier.size(size).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
        Text(letter(name), color = onAccent(accent), fontSize = textSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** Выбор акцентного цвета: ряд кружков. Выбранный обведён. */
@Composable
internal fun AccentDots(selected: Int, onSelect: (Int) -> Unit, size: Dp = 40.dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentChoices.forEachIndexed { i, color ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
                    .then(if (selected == i) Modifier.border(3.dp, Snow, CircleShape) else Modifier)
                    .clickable { onSelect(i) },
            )
        }
    }
}

// ------------------------------------------------------------------ 1. Тёмный

@Composable
private fun ProfileDark(name: String, email: String, cloud: Boolean, go: (String) -> Unit) {
    val accent = LocalAccent.current
    ProfileColumn(Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 12.dp)) { Avatar(name, 88.dp, 40) }
        Text(name, color = Snow, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
        if (email.isNotBlank()) Text(email, color = GrayText, style = MaterialTheme.typography.bodyMedium)
        LabelPill(
            if (cloud) "Данные в облаке" else "Данные только на телефоне",
            filled = false,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
        )
        listOf(GroupMain, GroupAssistant, GroupSystem).forEach { group ->
            val shape = RoundedCornerShape(22.dp)
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(shape).background(CardColor).border(1.dp, CardBorder, shape)) {
                group.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(CardBorder))
                    Row(
                        Modifier.fillMaxWidth().clickable { go(item.route) }.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(item.icon, null, tint = accent, modifier = Modifier.size(22.dp))
                        Text(item.title, color = Snow, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                        if (item.route == "theme") {
                            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccentChoices.forEach { c -> Box(Modifier.size(10.dp).clip(CircleShape).background(c)) }
                            }
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GrayText)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 2. Тёплый

@Composable
private fun ProfileWarm(name: String, cloud: Boolean, accentIndex: Int, setAccent: (Int) -> Unit, go: (String) -> Unit) {
    val accent = LocalAccent.current
    ProfileColumn {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(HeroCard).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, 62.dp, 28)
            Column(Modifier.padding(start = 14.dp)) {
                Text(name, color = OnHero, style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (cloud) "Данные в облаке" else "Данные только на телефоне",
                    color = OnHeroGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        (GroupMain + GroupAssistant + GroupSystem.filter { it.route == "security" }).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    val shape = RoundedCornerShape(24.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(108.dp)
                            .clip(shape)
                            .background(CardColor)
                            .border(1.dp, CardBorder, shape)
                            .clickable { go(item.route) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(accent), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = onAccent(accent), modifier = Modifier.size(19.dp))
                        }
                        Text(item.title, color = Snow, style = MaterialTheme.typography.titleMedium, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2)
                    }
                }
                repeat(2 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        val colorShape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().clip(colorShape).background(CardColor).border(1.dp, CardBorder, colorShape).padding(16.dp)) {
            Text("Цвет приложения", color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            AccentDots(accentIndex, setAccent, size = 36.dp)
        }
        Spacer(Modifier.height(10.dp))
        WarmLink("Оформление и стиль", Icons.Rounded.Palette, accent) { go("theme") }
        Spacer(Modifier.height(10.dp))
        WarmLink("Как работает LifeOS", Icons.Rounded.Info, accent) { go("welcome") }
    }
}

@Composable
private fun WarmLink(title: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(CardColor).border(1.dp, CardBorder, shape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = onAccent(accent), modifier = Modifier.size(19.dp))
        }
        Text(title, color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GrayText)
    }
}

// ------------------------------------------------------------------ 3. Светлый

@Composable
private fun ProfileLight(name: String, cloud: Boolean, accentIndex: Int, go: (String) -> Unit) {
    ProfileColumn(Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 12.dp)) { Avatar(name, 88.dp, 40) }
        Text(name, color = Snow, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
        Text(
            if (cloud) "Данные в облаке" else "Данные хранятся только на телефоне",
            color = GrayText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp, bottom = 18.dp),
        )
        listOf(GroupMain, GroupAssistant, GroupSystem).forEach { group ->
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(CardColor)) {
                group.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(CardBorder))
                    val tint = pastelFor(item.title)
                    Row(
                        Modifier.fillMaxWidth().clickable { go(item.route) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                        Text(item.title, color = Snow, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                        if (item.route == "theme") {
                            Text(AccentNames.getOrElse(accentIndex) { "" }, color = GrayText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GrayText)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Оформление

/** Маленькая «картинка» стиля для выбора: цвета и расположение, как на настоящем экране. */
@Composable
private fun StylePreview(style: LifeStyle, selected: Boolean, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    val p = paletteOf(style)
    val a = AccentChoices[style.defaultAccent]
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(shape)
                .background(p.bg)
                .border(if (selected) 3.dp else 1.dp, if (selected) accent else CardBorder, shape)
                .padding(8.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Mini(p.text.copy(alpha = 0.85f), 6.dp, Modifier.fillMaxWidth(0.45f), 3.dp)
                when (style) {
                    LifeStyle.DARK -> {
                        Mini(p.card, 16.dp, Modifier.fillMaxWidth(), 8.dp, p.border)
                        Mini(p.card, 42.dp, Modifier.fillMaxWidth(), 8.dp, p.border)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Mini(p.card, 26.dp, Modifier.weight(1f), 7.dp, p.border)
                            Mini(p.card, 26.dp, Modifier.weight(1f), 7.dp, p.border)
                        }
                    }
                    LifeStyle.WARM -> {
                        Mini(a, 44.dp, Modifier.fillMaxWidth(), 12.dp)
                        Mini(p.hero, 18.dp, Modifier.fillMaxWidth(), 9.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Mini(p.card, 28.dp, Modifier.weight(1f), 8.dp, p.border)
                            Mini(p.card, 28.dp, Modifier.weight(1f), 8.dp, p.border)
                        }
                    }
                    LifeStyle.LIGHT -> {
                        Mini(p.card, 16.dp, Modifier.fillMaxWidth(), 8.dp)
                        Mini(p.card, 44.dp, Modifier.fillMaxWidth(), 8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(Color(0xFF2F7BFF), Color(0xFF1FA55B), Color(0xFFE08A00), Color(0xFF8B4FE0)).forEach {
                                Mini(it.copy(alpha = 0.22f), 16.dp, Modifier.weight(1f), 5.dp)
                            }
                        }
                    }
                }
            }
            // нижнее меню своей формы
            when (style) {
                LifeStyle.DARK -> Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(12.dp).clip(CircleShape).background(a)) }
                LifeStyle.WARM -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp).background(p.card), contentAlignment = Alignment.CenterStart) {
                    Mini(a, 10.dp, Modifier.fillMaxWidth(0.25f), 5.dp)
                }
                LifeStyle.LIGHT -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp).background(p.card), contentAlignment = Alignment.CenterStart) {
                    Mini(a.copy(alpha = 0.9f), 4.dp, Modifier.fillMaxWidth(0.2f), 2.dp)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(16.dp).padding(end = 2.dp))
            Text(style.title, color = if (selected) Snow else GrayText, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun Mini(color: Color, height: Dp, modifier: Modifier, radius: Dp, border: Color? = null) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.height(height).clip(shape).background(color).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier))
}

@Composable
fun ThemeScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val accent = LocalAccent.current
    LifeScreen("Оформление", onBack = onBack) {
        SectionTitle("Стиль")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LifeStyle.entries.forEach { st ->
                StylePreview(
                    st,
                    selected = LifePalette.style == st,
                    accent = accent,
                    onClick = {
                        // сразу показываем новый стиль, а потом запоминаем его в настройках
                        LifePalette.set(context, st.ordinal)
                        vm.update { it.copy(designStyle = st.ordinal, accent = st.defaultAccent) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTitle("Цвет")
        AccentDots(s.accent, { i -> vm.update { it.copy(accent = i) } })

        SectionTitle("Ещё")
        if (IsDarkStyle) {
            SwitchRow("Мягкое свечение", s.glow, { on -> vm.update { it.copy(glow = on) } })
        }
        SwitchRow("Робот на экране", s.robotEnabled, { on -> vm.update { it.copy(robotEnabled = on) } })

        SectionTitle("Живые обои")
        PillButton(
            "Установить живые обои",
            {
                val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(
                        android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        android.content.ComponentName(context, com.lifeos.app.wallpaper.LifeWallpaperService::class.java),
                    )
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        runCatching {
                            context.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                        }
                    }
            },
            Modifier.fillMaxWidth(),
            outlined = true,
        )
        Box(Modifier.size(24.dp))
    }
}

/** Облако: сохранить данные сейчас или загрузить из аккаунта. Показывается в разделе «Аккаунт». */
@Composable
fun CloudSection(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val cloud = user?.cloud == true

    SectionTitle("Облако")
    InfoCard(Modifier.fillMaxWidth()) {
        Text(
            if (cloud) "Данные сохраняются в облаке сами."
            else "Облако не подключено: данные только на этом телефоне.",
            color = GrayText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (cloud) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Сохранить сейчас", { vm.backupNow { message = it } }, Modifier.weight(1f), outlined = true)
            PillButton("Загрузить", { vm.restoreNow { message = it } }, Modifier.weight(1f), outlined = true)
        }
    }
    message?.let { Text(it, color = LocalAccent.current, modifier = Modifier.padding(top = 10.dp)) }
    val last = vm.settings.collectAsState().value.lastBackup
    Text(
        if (last > 0) "Последнее сохранение: " + com.lifeos.app.ui.Fmt.dateTime(last) else "Сохранений в облаке ещё не было",
        color = GrayText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Выход из аккаунта и сведения о приложении. */
@Composable
fun ExitSection(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }
    val cloud = user?.cloud == true

    SectionTitle("Выход")
    PillButton("Выйти из аккаунта", { confirmExit = true }, Modifier.fillMaxWidth())

    SectionTitle("О приложении")
    Text("LifeOS 0.3", color = Snow)

    if (confirmExit) {
        FormDialog(
            title = "Выйти из аккаунта?",
            confirmText = "Выйти",
            onDismiss = { confirmExit = false },
            onConfirm = {
                confirmExit = false
                vm.signOut()
            },
        ) {
            Text(
                if (cloud) "Данные сохранятся в облаке и вернутся при следующем входе. С телефона они будут удалены."
                else "Аккаунт локальный: данные останутся на телефоне, а войти снова можно по email и паролю.",
                color = GrayText,
            )
        }
    }
}
