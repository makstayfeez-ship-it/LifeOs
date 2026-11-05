package com.lifeos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Палитра из дизайн-системы: чёрный фон, тёмно-серые карточки, оранжевый акцент.
val Bg = Color(0xFF0B0B0D)
val CardColor = Color(0xFF26262B)
val GrayText = Color(0xFF858585)
val Disabled = Color(0xFF858585)
val Snow = Color(0xFFF5F5F5)

/** Акценты, которые пользователь выбирает на экране «Оформление». Первый — оранжевый по умолчанию. */
val AccentChoices: List<Color> = listOf(
    0xFFFF7A12, // оранжевый (дизайн-система)
    0xFF2BF16A, // зелёный (цвет робота LifeOS)
    0xFF3D8BFF,
    0xFFA95CFF,
    0xFFFF4FA3,
    0xFFFF4545,
).map { Color(it) }

val LocalAccent = compositionLocalOf { AccentChoices[0] }

/** Включено ли мягкое свечение акцентного цвета в углу экрана (настройка «Оформление»). */
val LocalGlow = compositionLocalOf { true }

/** Фон экрана: тёмный цвет дизайн-системы и, если включено, мягкое свечение акцента сверху справа. */
fun Modifier.lifeBackground(): Modifier = composed {
    val glow = LocalGlow.current
    val accent = LocalAccent.current
    background(Bg).then(
        if (glow) {
            Modifier.drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
            }
        } else Modifier,
    )
}

// Типографика: Large Title, Title 1, Title 2, Headline, Body, Subhead, Caption.
// Шрифт SF Pro нельзя ставить на Android, поэтому берётся системный.
val LifeTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun LifeTheme(accentIndex: Int, glow: Boolean = true, content: @Composable () -> Unit) {
    val accent = AccentChoices.getOrElse(accentIndex) { AccentChoices[0] }
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        background = Bg,
        onBackground = Snow,
        surface = CardColor,
        onSurface = Snow,
        surfaceVariant = CardColor,
        onSurfaceVariant = GrayText,
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalGlow provides glow) {
        MaterialTheme(colorScheme = scheme, typography = LifeTypography, content = content)
    }
}
