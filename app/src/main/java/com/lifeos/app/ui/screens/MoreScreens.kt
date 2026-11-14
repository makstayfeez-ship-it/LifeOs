package com.lifeos.app.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.components.SwitchRow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import com.lifeos.app.widget.FinanceWidget
import com.lifeos.app.widget.QuickNoteWidget
import com.lifeos.app.widget.TodayWidget
import com.lifeos.app.widget.WidgetUpdater

// ---------------------------------------------------------------- Виджеты

@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun add(cls: Class<*>) {
        if (!WidgetUpdater.requestPin(context, cls)) {
            Toast.makeText(
                context,
                "Ваш рабочий стол не умеет добавлять виджеты отсюда. Удерживайте пустое место на рабочем столе → Виджеты → LifeOS.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    LifeScreen("Виджеты", onBack = onBack) {
        Text(
            "Виджеты живут на рабочем столе. Самый полезный из них — микрофон: одно нажатие, и вы диктуете заметку.",
            color = GrayText,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        WidgetCard("Голосовая заметка", "Кнопка-микрофон. Нажали, сказали, готово.") { add(QuickNoteWidget::class.java) }
        WidgetCard("Сегодня", "Дела и события на сегодня.") { add(TodayWidget::class.java) }
        WidgetCard("Финансы", "Расходы и доходы за месяц.") { add(FinanceWidget::class.java) }
    }
}

@Composable
private fun WidgetCard(title: String, hint: String, onAdd: () -> Unit) {
    InfoCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, color = Snow, style = MaterialTheme.typography.titleMedium)
                Text(hint, color = GrayText, style = MaterialTheme.typography.bodySmall)
            }
            PillButton("Добавить", onAdd)
        }
    }
}

// ---------------------------------------------------------------- Аккаунт, уведомления, конфиденциальность

@Composable
fun AccountScreen(vm: AppViewModel, onBack: () -> Unit) {
    val user by vm.user.collectAsState()
    var name by remember(user?.name) { mutableStateOf(user?.name.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    LifeScreen("Аккаунт", onBack = onBack) {
        SectionTitle("Имя")
        LifeField(name, { name = it }, "Как к вам обращаться")
        PillButton(
            "Сохранить имя",
            { vm.updateName(name) { message = it } },
            Modifier.padding(top = 8.dp),
            enabled = name.isNotBlank() && name.trim() != user?.name,
            outlined = true,
        )
        message?.let { Text(it, color = LocalAccent.current, modifier = Modifier.padding(top = 8.dp)) }

        SectionTitle("Почта")
        Text(user?.email.orEmpty(), color = Snow)
        LabelPill(
            if (user?.cloud == true) "аккаунт в облаке" else "локальный аккаунт на этом телефоне",
            filled = user?.cloud == true,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (user?.cloud == true) {
            SectionTitle("Пароль")
            PillButton(
                "Отправить письмо для смены пароля",
                { user?.email?.let { e -> vm.resetPassword(e) { message = it } } },
                Modifier.fillMaxWidth(),
                outlined = true,
            )
        }

        // Всё, что раньше было отдельными пунктами профиля, теперь здесь
        CloudSection(vm)
        SectionTitle("Безопасность")
        SecuritySection(vm)
        NotificationsSection(vm)
        PrivacySection()
        ExitSection(vm)
        androidx.compose.foundation.layout.Box(Modifier.padding(bottom = 24.dp))
    }
}

/** Уведомления: утренний обзор, скрытие на экране блокировки, системные настройки. */
@Composable
fun NotificationsSection(vm: AppViewModel) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    SectionTitle("Уведомления")
    SwitchRow(
        "Присылать утренний обзор",
        s.morningEnabled,
        { on -> vm.updateMorning { it.copy(morningEnabled = on) } },
        subtitle = "Время и содержимое настраиваются на экране «Обзор» (шестерёнка в углу).",
    )
    SwitchRow(
        "Скрывать на экране блокировки",
        s.hideLockNotifs,
        { on -> vm.update { it.copy(hideLockNotifs = on) } },
        subtitle = "Вместо дел и сумм будет нейтральный текст.",
    )
    SwitchRow(
        "Громкое напоминание на весь экран",
        s.loudReminders,
        { on -> vm.update { it.copy(loudReminders = on) } },
        subtitle = "Кроме обычного уведомления, в назначенную минуту на весь экран появится окно со звонком, чтобы точно не пропустить.",
    )
    Text(
        "Звук, баннеры и каналы уведомлений Android настраиваются в системе.",
        color = GrayText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
    PillButton(
        "Открыть настройки уведомлений",
        {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        Modifier.fillMaxWidth(),
        outlined = true,
    )
}

/** Что и где хранится. */
@Composable
fun PrivacySection() {
    SectionTitle("Конфиденциальность")
    InfoCard(Modifier.fillMaxWidth()) {
        Text(
            "• Заметки, дела, финансы и привычки лежат в памяти телефона. В облако они попадают только если вы вошли в облачный аккаунт.\n" +
                "• Записи звука остаются только на телефоне и в облако не отправляются.\n" +
                "• Автоучёт денег читает только уведомления банков и не сохраняет их текст: в приложение попадают лишь сумма, магазин и направление.\n" +
                "• Микрофон слушает «LifeOS» только когда включено прослушивание (в шторке виден значок). Распознавание речи идёт на телефоне.\n" +
                "• Робот «видит экран» только как положение кнопок и полей, текст не читается.\n" +
                "• Когда вы пишете или говорите помощнику вопрос (в том числе с фото и файлами), текст и вложения уходят на сервер ИИ, чтобы он ответил. Без ИИ помощник работает только с командами.\n" +
                "• Ключ ИИ и PIN-код не уходят в облако.",
            color = GrayText,
        )
    }
}

// ---------------------------------------------------------------- Нижнее меню

@Composable
fun NavbarSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val items = com.lifeos.app.ui.NavCatalog.parse(s.navItems)
    val available = com.lifeos.app.ui.NavCatalog.all.filter { it.route !in items }
    val accent = LocalAccent.current
    val max = com.lifeos.app.ui.NavCatalog.MAX_ITEMS
    fun save(list: List<String>) = vm.update { it.copy(navItems = com.lifeos.app.ui.NavCatalog.encode(list)) }

    LifeScreen("Нижнее меню", onBack = onBack) {
        Text(
            "Выберите, что показывать внизу экрана. Например, добавьте «Заметки», чтобы они были под рукой. " +
                "«Главная» и «Приложения» остаются всегда. Максимум $max пунктов, кнопка «+» посередине.",
            color = GrayText,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SectionTitle("Сейчас в меню")
        items.forEachIndexed { i, route ->
            val d = com.lifeos.app.ui.NavCatalog.def(route) ?: return@forEachIndexed
            val locked = route in com.lifeos.app.ui.NavCatalog.fixed
            InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(d.icon, null, tint = accent, modifier = Modifier.padding(end = 12.dp))
                    Text(d.label, color = Snow, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (locked) {
                        Text("всегда", color = GrayText, style = MaterialTheme.typography.bodySmall)
                    } else {
                        androidx.compose.material3.IconButton(
                            onClick = { if (i > 2) save(items.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) },
                            enabled = i > 2,
                        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp, "Выше", tint = GrayText) }
                        androidx.compose.material3.IconButton(
                            onClick = { if (i < items.lastIndex) save(items.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) }) },
                            enabled = i < items.lastIndex,
                        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown, "Ниже", tint = GrayText) }
                        androidx.compose.material3.IconButton(onClick = { save(items - route) }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Убрать", tint = GrayText)
                        }
                    }
                }
            }
        }
        SectionTitle("Можно добавить")
        if (items.size >= max) Text("В меню уже $max пунктов. Уберите один, чтобы добавить другой.", color = GrayText)
        available.forEach { d ->
            InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(d.icon, null, tint = GrayText, modifier = Modifier.padding(end = 12.dp))
                    Text(d.label, color = Snow, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    PillButton("Добавить", { save(items + d.route) }, enabled = items.size < max, outlined = true)
                }
            }
        }
        PillButton("Вернуть как было", { save(listOf("home", "apps", "reminders", "profile")) }, Modifier.fillMaxWidth().padding(vertical = 12.dp), outlined = true)
    }
}
