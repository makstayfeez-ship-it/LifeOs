package com.lifeos.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifeos.app.assistant.AiAgent
import com.lifeos.app.assistant.AiModels
import com.lifeos.app.assistant.AiProviders
import com.lifeos.app.assistant.CommandExecutor
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.Snow
import kotlinx.coroutines.launch

private val Good = Color(0xFF4CD964)
private val Bad = Color(0xFFFF6B6B)

/**
 * Подключение нейросети. Обычному пользователю тут делать нечего: если ключ вшит в сборку, ИИ уже работает.
 * Свой ключ (OpenAI, Claude, Gemini и другие) нужен владельцу, чтобы проверить ИИ сразу, не дожидаясь новой сборки.
 */
@Composable
fun AiSetupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val direct = AiAgent.direct(s)
    val relay = AiAgent.relayBase(s) != null

    var key by remember { mutableStateOf("") }
    var baseUrl by remember(s.aiBaseUrl) { mutableStateOf(s.aiBaseUrl) }
    var model by remember(s.aiModel) { mutableStateOf(s.aiModel) }
    var advanced by remember { mutableStateOf(s.aiBaseUrl.isNotBlank() || s.aiModel.isNotBlank()) }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun runTest(st: com.lifeos.app.data.AppSettings) {
        busy = true
        result = "Проверяю…"
        scope.launch {
            val app = context.applicationContext
            result = AiAgent(app, CommandExecutor(app)).test(st)
            busy = false
        }
    }

    LifeScreen("Нейросеть", onBack = onBack) {
        InfoCard(Modifier.fillMaxWidth()) {
            when {
                direct != null -> {
                    Text("✓ ИИ подключён: ${direct.provider.title}", color = Good, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (direct.builtIn) "Ключ вшит в приложение: пользователям ничего вводить не нужно."
                        else "Ваш ключ хранится только на этом телефоне.",
                        color = GrayText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                relay -> Text("✓ ИИ работает через сервер LifeOS", color = Good, style = MaterialTheme.typography.titleMedium)
                else -> {
                    Text("ИИ не подключён", color = Bad, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Вставьте ключ ниже, и помощник начнёт отвечать на любые вопросы.",
                        color = GrayText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        SectionTitle("Ключ нейросети")
        LifeField(key, { key = it.trim(); result = "" }, "Вставьте ключ (sk-…, AIza…)", password = true)
        val guess = AiProviders.detect(key)
        if (key.isNotBlank()) {
            Text(
                when {
                    baseUrl.isNotBlank() -> "Сервис: по вашему адресу"
                    guess != null -> "Это ключ: ${guess.title}"
                    else -> "Сервис по ключу не узнать. Откройте «Дополнительно» и впишите адрес."
                },
                color = if (guess != null || baseUrl.isNotBlank()) GrayText else Bad,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Вставить", {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty().trim()
                if (text.isNotBlank()) {
                    key = text
                    result = ""
                }
            }, outlined = true)
            PillButton(
                "Сохранить и проверить",
                {
                    AiModels.forget(context)
                    val st = s.copy(apiKey = key.trim(), aiBaseUrl = baseUrl.trim(), aiModel = model.trim())
                    vm.update { it.copy(apiKey = st.apiKey, aiBaseUrl = st.aiBaseUrl, aiModel = st.aiModel) }
                    key = ""
                    runTest(st)
                },
                enabled = key.isNotBlank() && !busy,
            )
        }

        if (direct != null || relay) {
            PillButton("Проверить связь", { runTest(s) }, Modifier.padding(top = 8.dp), enabled = !busy, outlined = true)
        }
        if (result.isNotBlank()) {
            SelectionContainer {
                Text(
                    result,
                    color = if (result.startsWith("✓")) Good else if (result.startsWith("✗")) Bad else Snow,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        SectionTitle("Где взять ключ")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProviders.all.forEach { p ->
                LifeChip(p.title, false, {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyPage)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }
        }

        Row(Modifier.padding(top = 18.dp)) {
            LifeChip(if (advanced) "Скрыть дополнительное" else "Дополнительно", advanced, { advanced = !advanced })
        }
        if (advanced) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeField(baseUrl, { baseUrl = it.trim() }, "Адрес сервиса, например https://…/v1")
                LifeField(model, { model = it.trim() }, "Модель (необязательно)")
                Text(
                    "Адрес нужен только для сервисов, совместимых с OpenAI. Модель обычно выбирается сама.",
                    color = GrayText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (s.apiKey.isNotBlank() || s.aiBaseUrl.isNotBlank() || s.aiModel.isNotBlank()) {
            PillButton(
                "Убрать мой ключ",
                {
                    AiModels.forget(context)
                    vm.update { it.copy(apiKey = "", aiBaseUrl = "", aiModel = "") }
                    result = ""
                },
                Modifier.padding(top = 16.dp),
                outlined = true,
            )
        }
    }
}
