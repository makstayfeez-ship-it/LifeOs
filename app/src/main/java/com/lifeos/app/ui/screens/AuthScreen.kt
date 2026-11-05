package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import com.lifeos.app.ui.components.LifeField

/** Регистрация и вход: карточка с тёмными полями и кнопкой «отправить», как в дизайн-системе. */
@Composable
fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val cloud = vm.c.auth.cloudAvailable
    val valid = email.contains("@") && password.length >= 6 && (!register || name.isNotBlank())

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RobotView(SkinCatalog.default, RobotMood.HAPPY, Modifier.size(150.dp))
        Text("LifeOS", style = MaterialTheme.typography.displaySmall, color = Snow)
        Text("Твоя жизнь. Одна система.", color = LocalAccent.current, modifier = Modifier.padding(bottom = 20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(CardColor)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (register) "Регистрация" else "Вход",
                style = MaterialTheme.typography.headlineMedium,
                color = Snow,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (register) LifeField(name, { name = it }, "Имя", container = Bg)
            LifeField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, container = Bg)
            LifeField(password, { password = it }, "Пароль (от 6 символов)", password = true, container = Bg)

            Text(
                if (cloud) "*Все данные сохраняются в вашем аккаунте и доступны на любом телефоне"
                else "*Облако не подключено: аккаунт хранится только на этом телефоне",
                color = GrayText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            message?.let {
                Text(it, color = LocalAccent.current, style = MaterialTheme.typography.bodyMedium)
            }

            PillButton(
                text = if (busy) "подождите…" else "отправить",
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    message = null
                    vm.authenticate(register, name, email, password) { err ->
                        busy = false
                        message = err
                    }
                },
            )
        }

        Text(
            if (register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",
            color = Snow,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable {
                    register = !register
                    message = null
                }
                .padding(8.dp),
        )
        if (!register && cloud) {
            Text(
                "Забыли пароль?",
                color = GrayText,
                modifier = Modifier
                    .clickable(enabled = email.contains("@")) { vm.resetPassword(email) { message = it } }
                    .padding(8.dp),
            )
        }
    }
}
