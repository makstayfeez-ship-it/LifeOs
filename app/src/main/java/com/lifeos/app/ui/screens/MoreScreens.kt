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
    }
}

@Composable
fun NotificationsSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    LifeScreen("Уведомления", onBack = onBack) {
        SectionTitle("Утренний обзор")
        SwitchRow(
            "Присылать утренний обзор",
            s.morningEnabled,
            { on -> vm.updateMorning { it.copy(morningEnabled = on) } },
            subtitle = "Время и содержимое настраиваются в разделе «Утренний обзор».",
        )
        SwitchRow(
            "Скрывать на экране блокировки",
            s.hideLockNotifs,
            { on -> vm.update { it.copy(hideLockNotifs = on) } },
            subtitle = "Вместо дел и сумм будет нейтральный текст.",
        )
        SectionTitle("Системные настройки")
        Text(
            "Звук, баннеры и каналы уведомлений Android настраиваются в системе.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
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
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onSecurity: () -> Unit) {
    LifeScreen("Конфиденциальность", onBack = onBack) {
        InfoCard(Modifier.fillMaxWidth()) {
            Text(
                "• Заметки, дела, финансы и привычки лежат в памяти телефона. В облако они попадают только если вы вошли в облачный аккаунт.\n" +
                    "• Записи звука остаются только на телефоне и в облако не отправляются.\n" +
                    "• Автоучёт денег читает только уведомления банков и не сохраняет их текст: в приложение попадают лишь сумма, магазин и направление.\n" +
                    "• Микрофон слушает «LifeOS» только когда включено прослушивание (в шторке виден значок). Распознавание речи делает система Android.\n" +
                    "• Робот «видит экран» только как положение кнопок и полей, текст не читается.\n" +
                    "• Если вставлен ключ ИИ, текст ваших вопросов уходит в Anthropic. Без ключа помощник работает только с командами.\n" +
                    "• Ключ ИИ и PIN-код не уходят в облако.",
                color = GrayText,
            )
        }
        PillButton("Блокировка и защита", onSecurity, Modifier.fillMaxWidth().padding(top = 12.dp), outlined = true)
    }
}
