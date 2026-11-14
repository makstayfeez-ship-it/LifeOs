package com.lifeos.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifeos.app.ui.Perms
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.Snow

/**
 * Короткая подсказка для Android 13+: приложение, установленное не из магазина, не может получить доступ к уведомлениям
 * или специальным возможностям, пока в «Сведениях о приложении» не нажато «Разрешить ограниченные настройки».
 * Обойти эту защиту из самого приложения нельзя, поэтому показываем её только тогда, когда включить не вышло.
 */
@Composable
fun RestrictedSettingsHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InfoCard(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Android пишет «ограниченный доступ»?", color = Snow, style = MaterialTheme.typography.titleSmall)
        Text(
            "Сведения о приложении → ⋮ вверху справа → «Разрешить ограниченные настройки». Потом включите ещё раз.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        PillButton("Сведения о приложении", { context.startActivity(Perms.appDetailsSettings(context)) }, Modifier.fillMaxWidth(), outlined = true)
    }
}
