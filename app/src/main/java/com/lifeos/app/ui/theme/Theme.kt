package com.lifeos.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Три стиля приложения. Основной — тёмный с зелёным акцентом, два других можно включить в любой момент
 * в «Профиль → Оформление». Стиль задаёт цвета фона и карточек и форму главных экранов.
 */
enum class LifeStyle(val title: String, val defaultAccent: Int) {
    DARK("Тёмный", 1),
    WARM("Тёплый", 0),
    LIGHT("Светлый", 2),
    ;

    companion object {
        fun of(index: Int): LifeStyle = entries.getOrElse(index) { DARK }
    }
}

/** Набор цветов одного стиля. */
class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val gray: Color,
    val disabled: Color,
    /** Тёмная «главная» карточка (в тёплом стиле: «Спросить LifeOS», шапка профиля). */
    val hero: Color,
    val onHero: Color,
    val onHeroGray: Color,
)

private val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0A0B0E),
    card = Color(0xFF15171C),
    border = Color(0xFF262A31),
    text = Color(0xFFF4F5F7),
    gray = Color(0xFF8D929C),
    disabled = Color(0xFF3E424A),
    hero = Color(0xFF15171C),
    onHero = Color(0xFFF4F5F7),
    onHeroGray = Color(0xFF8D929C),
)

private val WarmPalette = Palette(
    dark = false,
    bg = Color(0xFFF3EFEA),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE6E0D8),
    text = Color(0xFF1A1613),
    gray = Color(0xFF70685F),
    disabled = Color(0xFFD3CCC3),
    hero = Color(0xFF1C1816),
    onHero = Color(0xFFF7F3EE),
    onHeroGray = Color(0xFFA9A199),
)

private val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF1F3F6),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE3E7EC),
    text = Color(0xFF12161C),
    gray = Color(0xFF69727F),
    disabled = Color(0xFFCBD1D8),
    hero = Color(0xFFFFFFFF),
    onHero = Color(0xFF12161C),
    onHeroGray = Color(0xFF69727F),
)

/**
 * Текущий стиль на всё приложение (и на окна поверх экрана, у которых нет своей темы).
 * Это состояние Compose: когда стиль меняется, экраны перерисовываются сами.
 */
object LifePalette {
    var style by mutableStateOf(LifeStyle.DARK)

    val current: Palette
        get() = when (style) {
            LifeStyle.DARK -> DarkPalette
            LifeStyle.WARM -> WarmPalette
            LifeStyle.LIGHT -> LightPalette
        }

    private const val PREFS = "life_ui"
    private const val KEY = "style"

    /** Стиль запоминается отдельно, чтобы первый кадр сразу был в нужных цветах, а не мигал тёмным. */
    fun restore(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
        style = LifeStyle.of(saved)
    }

    fun set(context: Context, index: Int) {
        val s = LifeStyle.of(index)
        if (s == style) return
        style = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, index).apply()
    }
}

/** Палитра конкретного стиля (для маленьких превью на экране «Оформление»). */
fun paletteOf(style: LifeStyle): Palette = when (style) {
    LifeStyle.DARK -> DarkPalette
    LifeStyle.WARM -> WarmPalette
    LifeStyle.LIGHT -> LightPalette
}

// Цвета стиля. Читаются как обычные константы, но меняются вместе со стилем.
val Bg: Color get() = LifePalette.current.bg
val CardColor: Color get() = LifePalette.current.card
val CardBorder: Color get() = LifePalette.current.border
val GrayText: Color get() = LifePalette.current.gray
val Disabled: Color get() = LifePalette.current.disabled
val Snow: Color get() = LifePalette.current.text
val HeroCard: Color get() = LifePalette.current.hero
val OnHero: Color get() = LifePalette.current.onHero
val OnHeroGray: Color get() = LifePalette.current.onHeroGray
val IsDarkStyle: Boolean get() = LifePalette.current.dark

/** Цвет текста и значков на акцентной заливке: тёмный на светлых акцентах (зелёный, оранжевый), белый на тёмных (синий). */
fun onAccent(accent: Color): Color = if (accent.luminance() > 0.3f) Color(0xFF0B0F0C) else Color.White

/** Акценты, которые пользователь выбирает на экране «Оформление». */
val AccentChoices: List<Color> = listOf(
    0xFFFF7A12, // оранжевый
    0xFF2BF16A, // зелёный (цвет робота LifeOS)
    0xFF2F7BFF, // синий
    0xFFA95CFF, // фиолетовый
    0xFFFF4FA3, // розовый
    0xFFFF4545, // красный
).map { Color(it) }

val AccentNames: List<String> = listOf("Оранжевый", "Зелёный", "Синий", "Фиолетовый", "Розовый", "Красный")

val LocalAccent = compositionLocalOf { AccentChoices[1] }

/** Включено ли мягкое свечение акцентного цвета в углу экрана (настройка «Оформление»). */
val LocalGlow = compositionLocalOf { true }

/** Фон экрана: цвет стиля и, в тёмном стиле, если включено, мягкое свечение акцента сверху справа. */
fun Modifier.lifeBackground(): Modifier = composed {
    val glow = LocalGlow.current
    val accent = LocalAccent.current
    background(Bg).then(
        if (glow && IsDarkStyle) {
            Modifier.drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.16f), Color.Transparent),
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

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun LifeTheme(accentIndex: Int, glow: Boolean = true, content: @Composable () -> Unit) {
    val accent = AccentChoices.getOrElse(accentIndex) { AccentChoices[1] }
    val p = LifePalette.current
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            background = p.bg,
            onBackground = p.text,
            surface = p.card,
            onSurface = p.text,
            surfaceVariant = p.card,
            onSurfaceVariant = p.gray,
            outline = p.border,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent(accent),
            background = p.bg,
            onBackground = p.text,
            surface = p.card,
            onSurface = p.text,
            surfaceVariant = p.card,
            onSurfaceVariant = p.gray,
            outline = p.border,
        )
    }
    // Значки в строке состояния и панели навигации: светлые на тёмном стиле, тёмные на светлом
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.activity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !p.dark
            controller.isAppearanceLightNavigationBars = !p.dark
        }
    }
    CompositionLocalProvider(LocalAccent provides accent, LocalGlow provides glow) {
        MaterialTheme(colorScheme = scheme, typography = LifeTypography, content = content)
    }
}
