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
import androidx.compose.material.icons.rounded.Palette
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
import com.lifeos.app.ui.theme.AccentChoices
import com.lifeos.app.ui.theme.Bg
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
            .background(Bg)
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

        ProfileRow("Настройки помощника", Icons.Rounded.AutoAwesome) { go("assistant_settings") }
        ProfileRow("Утренний обзор", Icons.Rounded.WbSunny) { go("morning_settings") }
        ProfileRow("Магазин роботов", Icons.Rounded.Storefront) { go("shop") }
        ProfileRow("Оформление", Icons.Rounded.Palette) { go("theme") }
        ProfileRow("Дополнительно", Icons.Rounded.Settings) { go("extras") }
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
    }
}

@Composable
fun ExtrasScreen(vm: AppViewModel, onBack: () -> Unit) {
    val user by vm.user.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val cloud = user?.cloud == true

    LifeScreen("Дополнительно", onBack = onBack) {
        SectionTitle("Облако")
        InfoCard(Modifier.fillMaxWidth()) {
            Text(
                if (cloud) "Ваши данные сохраняются в облаке автоматически через несколько секунд после изменения."
                else "Облако не подключено: данные хранятся только на этом телефоне. Чтобы они сохранялись в аккаунте, подключите Firebase (см. README).",
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

        SectionTitle("Аккаунт")
        PillButton("Выйти из аккаунта", { confirmExit = true }, Modifier.fillMaxWidth())

        SectionTitle("О приложении")
        Text("LifeOS 0.1.0", color = GrayText)
    }

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
