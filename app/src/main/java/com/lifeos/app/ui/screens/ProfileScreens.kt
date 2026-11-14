package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.unit.dp
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.theme.AccentChoices
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow

@Composable
fun ProfileScreen(vm: AppViewModel, go: (String) -> Unit) {
    val user by vm.user.collectAsState()
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .lifeBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 12.dp).size(88.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text(
                (user?.name?.firstOrNull() ?: 'L').uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
            )
        }
        Text(user?.name.orEmpty(), color = Snow, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
        Text(user?.email.orEmpty(), color = GrayText)
        LabelPill(
            if (user?.cloud == true) "данные в облаке" else "данные только на телефоне",
            filled = user?.cloud == true,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        ProfileRow("Аккаунт", Icons.Rounded.Person) { go("account") }
        ProfileRow("Нижнее меню", Icons.Rounded.Apps) { go("navbar") }
        ProfileRow("Виджеты", Icons.Rounded.Widgets) { go("widgets") }
        ProfileRow("Настройки помощника", Icons.Rounded.AutoAwesome) { go("assistant_settings") }
        ProfileRow("Магазин роботов", Icons.Rounded.Storefront) { go("shop") }
        ProfileRow("Оформление", Icons.Rounded.Palette) { go("theme") }
    }
}

@Composable
private fun ProfileRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    InfoCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(26.dp))
            Text(title, color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 14.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = GrayText)
        }
    }
}

@Composable
fun ThemeScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    LifeScreen("Оформление", onBack = onBack) {
        SectionTitle("Цвет приложения")
        Text(
            "Оранжевый — основной цвет дизайн-системы. Зелёный — цвет робота LifeOS.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentChoices.forEachIndexed { i, color ->
                val selected = s.accent == i
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (selected) Modifier.border(3.dp, Snow, CircleShape) else Modifier)
                        .clickable { vm.update { it.copy(accent = i) } },
                )
            }
        }

        SectionTitle("Фон и анимации")
        SwitchRow(
            "Мягкое свечение на экранах",
            s.glow,
            { on -> vm.update { it.copy(glow = on) } },
            subtitle = "Лёгкий отблеск акцентного цвета в углу. Выключите, если нужен чисто чёрный фон.",
        )
        SwitchRow(
            "Показывать робота на экране",
            s.robotEnabled,
            { on -> vm.update { it.copy(robotEnabled = on) } },
            subtitle = "Анимированный робот появляется, когда вы зовёте помощника. Если выключить, помощник только отвечает голосом.",
        )

        SectionTitle("Живые обои")
        Text(
            "Тёмный фон с плавно дрейфующим свечением в цвете вашего акцента, прямо на рабочем столе.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val context = androidx.compose.ui.platform.LocalContext.current
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
        androidx.compose.foundation.layout.Box(Modifier.size(24.dp))
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
