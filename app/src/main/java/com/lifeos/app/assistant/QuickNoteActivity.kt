package com.lifeos.app.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.NoteEntity
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LifeTheme
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import kotlinx.coroutines.delay

/**
 * Быстрая голосовая заметка без входа в приложение: открывается из плитки в шторке
 * (в том числе поверх экрана блокировки), слушает одну фразу, сохраняет и закрывается.
 */
class QuickNoteActivity : ComponentActivity() {
    private lateinit var listener: SpeechListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listener = SpeechListener(this)
        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            LifeTheme(settings.accent) {
                var status by remember { mutableStateOf("Говорите заметку…") }
                var partial by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    if (!AssistantControl.hasMic(this@QuickNoteActivity)) {
                        status = "Нужен доступ к микрофону: откройте LifeOS и разрешите его"
                        delay(2500)
                        finish()
                        return@LaunchedEffect
                    }
                    when (val r = listener.listen { partial = it }) {
                        is SpeechResult.Heard -> {
                            container.dao.insertNote(NoteEntity(text = r.text.trim(), isVoice = true))
                            partial = r.text.trim()
                            status = "Записано"
                        }
                        SpeechResult.Silence -> status = "Ничего не услышала"
                        is SpeechResult.Error -> status = "Не получилось записать"
                    }
                    delay(1400)
                    finish()
                }

                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    InfoCard(Modifier.fillMaxWidth()) {
                        Text(status, color = LocalAccent.current, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        if (partial.isNotBlank()) {
                            Text(partial, color = Snow, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("LifeOS", color = GrayText, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        listener.cancel()
        super.onDestroy()
    }
}
