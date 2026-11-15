package com.lifeos.app.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Крестик в углу окна заметки и напоминания: если позвали случайно, одно нажатие всё останавливает (голос не нужен). */
@Composable
internal fun CardClose(tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, "Закрыть", tint = tint, modifier = Modifier.size(28.dp))
    }
}
