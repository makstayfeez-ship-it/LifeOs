package com.lifeos.app.ui.components

import androidx.compose.foundation.layout.Column
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
 * Подсказка для Android 13+: приложение, установленное не из магазина, не может получить доступ к уведомлениям
 * или специальным возможностям, пока в «Сведениях о приложении» не нажато «Разрешить ограниченные настройки».
 */
@Composable
fun RestrictedSettingsHelp(title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(title, color = Snow, style = MaterialTheme.typography.titleSmall)
        Text(
            "1. Нажмите кнопку ниже: откроются сведения о LifeOS.\n" +
                "2. В правом верхнем углу нажмите ⋮ (три точки) и выберите «Разрешить ограниченные настройки». " +
                "Если пункта нет, сначала один раз попробуйте включить доступ, потом вернитесь сюда.\n" +
                "3. Вернитесь назад и включите переключатель LifeOS.",
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        PillButton(
            "Открыть сведения о приложении",
            { context.startActivity(Perms.appDetailsSettings(context)) },
            Modifier.fillMaxWidth(),
            outlined = true,
        )
    }
}
