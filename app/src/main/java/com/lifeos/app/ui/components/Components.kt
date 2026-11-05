package com.lifeos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.lifeBackground
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.Disabled
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow

/** Каркас экрана: заголовок со стрелкой «назад» и тело, которое по умолчанию прокручивается. */
@Composable
fun LifeScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().lifeBackground().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = Snow)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Snow,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 0.dp),
            )
            actions()
        }
        val body = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 16.dp)
        Column(body, content = content)
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    val bg = when {
        !enabled -> Disabled
        outlined -> Color.Transparent
        else -> accent
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (outlined && enabled) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (outlined && enabled) accent else Snow,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun LifeField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    password: Boolean = false,
    container: Color = CardColor,
) {
    val accent = LocalAccent.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        textStyle = TextStyle(color = Snow, fontSize = 17.sp),
        cursorBrush = SolidColor(accent),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = GrayText, fontSize = 17.sp)
                inner()
            }
        },
    )
}

@Composable
fun LifeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) accent else CardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Snow else GrayText, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Мелкая метка («label» из дизайн-системы): залитая или с обводкой. */
@Composable
fun LabelPill(text: String, filled: Boolean = true, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.background(accent) else Modifier.border(1.dp, accent, shape))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, color = if (filled) Snow else accent, style = MaterialTheme.typography.bodySmall)
    }
}

/** Карточка с оранжевой обводкой, как «title / date / time» в дизайн-системе. */
@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .border(1.5.dp, accent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** Тёмно-серая карточка без обводки. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(CardColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Snow,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        color = GrayText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, subtitle: String? = null) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Snow, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = GrayText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Snow,
                uncheckedTrackColor = CardColor,
                uncheckedThumbColor = GrayText,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Диалог-форма в стиле приложения. */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "Добавить",
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = { Text(title, color = Snow, style = MaterialTheme.typography.headlineMedium) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmText, color = if (confirmEnabled) accent else GrayText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = GrayText) } },
    )
}

/** Диалог с одним полем ввода. */
@Composable
fun TextInputDialog(title: String, placeholder: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    FormDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(text.trim()) },
        confirmEnabled = text.isNotBlank(),
    ) {
        LifeField(text, { text = it }, placeholder)
    }
}

@Composable
fun LifeBottomBar(current: String?, onNavigate: (String) -> Unit, onPlus: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        BarItem("home", "Главная", Icons.Rounded.Home, current, onNavigate)
        BarItem("apps", "Приложения", Icons.Rounded.Apps, current, onNavigate)
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(accent).clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Добавить", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        BarItem("reminders", "Уведомления", Icons.Rounded.Notifications, current, onNavigate)
        BarItem("profile", "Профиль", Icons.Rounded.Person, current, onNavigate)
    }
}

@Composable
private fun BarItem(route: String, label: String, icon: ImageVector, current: String?, onNavigate: (String) -> Unit) {
    val accent = LocalAccent.current
    val color = if (current == route) accent else GrayText
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { onNavigate(route) }.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
