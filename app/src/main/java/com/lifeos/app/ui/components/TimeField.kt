package com.lifeos.app.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.util.Calendar

/**
 * Поле времени без клавиатуры: по нажатию открывается круглый выбор часов и минут.
 * Так нельзя ошибиться с форматом (на цифровой клавиатуре нет двоеточия). [minutes] — минуты от полуночи или null.
 */
@Composable
fun TimeField(
    minutes: Int?,
    onChange: (Int?) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardColor)
            .clickable {
                val start = minutes ?: Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                TimePickerDialog(
                    context,
                    { _, h, m -> onChange(h * 60 + m) },
                    start / 60,
                    start % 60,
                    true,
                ).show()
            }
            .padding(start = 20.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = if (minutes != null) accent else GrayText, modifier = Modifier.size(22.dp))
        Text(
            if (minutes != null) Fmt.time(minutes) else placeholder,
            color = if (minutes != null) Snow else GrayText,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        )
        if (minutes != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Rounded.Close, "Убрать время", tint = GrayText) }
        }
    }
}
