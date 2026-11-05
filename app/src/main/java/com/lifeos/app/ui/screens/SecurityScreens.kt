package com.lifeos.app.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.security.Biometrics
import com.lifeos.app.security.PinLock
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import kotlinx.coroutines.delay

private val ErrorRed = Color(0xFFFF6B6B)

/** Экран блокировки: закрывает приложение, пока не введён PIN-код или не подтверждён отпечаток. */
@Composable
fun LockScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val accent = LocalAccent.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var fails by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val waitSeconds = ((blockedUntil - now) / 1000 + 1).coerceAtLeast(0)
    val blocked = blockedUntil > now

    // На время блокировки после неверных попыток идёт обратный отсчёт
    LaunchedEffect(blockedUntil) {
        while (SystemClock.elapsedRealtime() < blockedUntil) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    // Отпечаток предлагается сразу при открытии
    LaunchedEffect(s.lockEnabled, s.biometric) {
        if (s.lockEnabled && s.biometric) {
            delay(350)
            Biometrics.prompt(context) { vm.unlock() }
        }
    }
    // Назад на экране блокировки сворачивает приложение, а не открывает содержимое
    BackHandler { (context as? android.app.Activity)?.moveTaskToBack(true) }

    fun submit() {
        if (blocked || pin.length < PinLock.MIN_LENGTH) return
        if (PinLock.verify(pin, s.pinSalt, s.pinHash)) {
            fails = 0
            error = null
            vm.unlock()
        } else {
            fails++
            pin = ""
            if (fails % 5 == 0) {
                blockedUntil = SystemClock.elapsedRealtime() + 30_000L
                error = "Слишком много попыток"
            } else {
                error = "Неверный PIN-код"
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            // Закрывает всё, что под ним, и не пропускает нажатия
            .pointerInput(Unit) {},
    ) {
        if (!s.lockEnabled) return@Box // настройки ещё загружаются
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(40.dp))
            Text("LifeOS", color = Snow, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
            Text("Введите PIN-код", color = GrayText, modifier = Modifier.padding(top = 4.dp))

            Row(Modifier.padding(vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(PinLock.MIN_LENGTH, pin.length)) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) accent else CardColor),
                    )
                }
            }
            Text(
                if (blocked) "Подождите $waitSeconds с" else error.orEmpty(),
                color = ErrorRed,
                modifier = Modifier.padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "ok")
            keys.chunked(3).forEach { row ->
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> PadKey(onClick = { Biometrics.prompt(context) { vm.unlock() } }, visible = s.biometric) {
                                Icon(Icons.Rounded.Fingerprint, "Отпечаток", tint = accent, modifier = Modifier.size(28.dp))
                            }
                            "ok" -> PadKey(onClick = { submit() }, visible = true, filled = pin.length >= PinLock.MIN_LENGTH) {
                                Icon(Icons.Rounded.Check, "Войти", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            else -> PadKey(
                                onClick = { if (!blocked && pin.length < PinLock.MAX_LENGTH) { pin += key; error = null } },
                                visible = true,
                            ) { Text(key, color = Snow, fontSize = 26.sp) }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .clickable(enabled = pin.isNotEmpty()) { pin = pin.dropLast(1) }
                    .padding(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, "Стереть", tint = if (pin.isNotEmpty()) Snow else CardColor)
            }
        }
    }
}

@Composable
private fun PadKey(onClick: () -> Unit, visible: Boolean, filled: Boolean = false, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (!visible) Color.Transparent else if (filled) accent else CardColor)
            .then(if (visible) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { if (visible) content() }
}

// ---------------------------------------------------------------- Экран «Безопасность»

@Composable
fun SecurityScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val bioAvailable = remember { Biometrics.available(context) }
    // 0 — ничего, 1 — задать PIN, 2 — спросить PIN, чтобы выключить, 3 — спросить PIN перед сменой, 4 — новый PIN
    var dialog by remember { mutableIntStateOf(0) }

    LifeScreen("Безопасность", onBack = onBack) {
        SectionTitle("Блокировка приложения")
        SwitchRow(
            "PIN-код при входе",
            s.lockEnabled,
            { on -> dialog = if (on) 1 else 2 },
            subtitle = "LifeOS закрывается PIN-кодом, когда вы возвращаетесь в него после перерыва больше 10 секунд.",
        )
        if (s.lockEnabled) {
            SwitchRow(
                "Отпечаток пальца или лицо",
                s.biometric && bioAvailable,
                { on -> if (bioAvailable) vm.update { it.copy(biometric = on) } },
                subtitle = if (bioAvailable) "Вход без ввода PIN-кода. PIN остаётся запасным способом."
                else "На телефоне не настроен отпечаток или лицо. Добавьте их в настройках Android.",
            )
            PillButton("Сменить PIN-код", { dialog = 3 }, Modifier.padding(top = 8.dp), outlined = true)
        }

        SectionTitle("Защита данных")
        SwitchRow(
            "Запретить скриншоты",
            s.secureScreen,
            { on -> vm.update { it.copy(secureScreen = on) } },
            subtitle = "Содержимое LifeOS не попадёт на скриншот и скроется в списке последних приложений.",
        )
        SwitchRow(
            "Скрывать уведомления на экране блокировки",
            s.hideLockNotifs,
            { on -> vm.update { it.copy(hideLockNotifs = on) } },
            subtitle = "Утренний обзор и виджеты не покажут дела и суммы, пока телефон заблокирован.",
        )

        SectionTitle("Как всё защищено")
        InfoCard(Modifier.fillMaxWidth()) {
            Text(
                "• PIN-код хранится только как хэш PBKDF2 с солью, сам PIN нигде не записывается.\n" +
                    "• Файлы в «Документах» шифруются AES-256, ключ лежит в защищённом хранилище Android и не покидает телефон.\n" +
                    "• Ключ ИИ хранится только на телефоне и не уходит в облако.\n" +
                    "• Если PIN забыт, выйдите из аккаунта и войдите снова: блокировка сбросится.",
                color = GrayText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(Modifier.size(24.dp))
    }

    when (dialog) {
        1 -> PinDialog("Придумайте PIN-код", askRepeat = true, onDismiss = { dialog = 0 }) { pin ->
            saveNewPin(vm, pin)
            dialog = 0
            null
        }
        2 -> PinDialog("Введите PIN-код, чтобы выключить", askRepeat = false, onDismiss = { dialog = 0 }) { pin ->
            if (PinLock.verify(pin, s.pinSalt, s.pinHash)) {
                vm.update { it.copy(lockEnabled = false, biometric = false, pinSalt = "", pinHash = "") }
                dialog = 0
                null
            } else "Неверный PIN-код"
        }
        3 -> PinDialog("Введите текущий PIN-код", askRepeat = false, onDismiss = { dialog = 0 }) { pin ->
            if (PinLock.verify(pin, s.pinSalt, s.pinHash)) {
                dialog = 4
                null
            } else "Неверный PIN-код"
        }
        4 -> PinDialog("Новый PIN-код", askRepeat = true, onDismiss = { dialog = 0 }) { pin ->
            saveNewPin(vm, pin)
            dialog = 0
            null
        }
    }
}

private fun saveNewPin(vm: AppViewModel, pin: String) {
    val salt = PinLock.newSalt()
    val hash = PinLock.hash(pin, salt)
    vm.update { it.copy(lockEnabled = true, pinSalt = salt, pinHash = hash) }
}

/** Запрос PIN-кода. [onSubmit] возвращает текст ошибки или null, если всё принято. */
@Composable
private fun PinDialog(title: String, askRepeat: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> String?) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    FormDialog(
        title = title,
        confirmText = "Готово",
        onDismiss = onDismiss,
        onConfirm = {
            err = when {
                a.length < PinLock.MIN_LENGTH -> "Минимум ${PinLock.MIN_LENGTH} цифры"
                askRepeat && a != b -> "PIN-коды не совпадают"
                else -> onSubmit(a)
            }
        },
    ) {
        LifeField(a, { a = it.filter(Char::isDigit).take(PinLock.MAX_LENGTH) }, "PIN-код", keyboardType = KeyboardType.NumberPassword, password = true)
        if (askRepeat) {
            LifeField(b, { b = it.filter(Char::isDigit).take(PinLock.MAX_LENGTH) }, "Повторите PIN-код", keyboardType = KeyboardType.NumberPassword, password = true)
        }
        err?.let { Text(it, color = ErrorRed) }
    }
}
