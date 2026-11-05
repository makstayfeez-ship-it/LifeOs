package com.lifeos.app.ui.robot

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.lifeos.app.data.Accessory
import com.lifeos.app.data.RobotSkin
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.delay

enum class RobotMood { HAPPY, LISTENING, THINKING, TALKING, WORKING }

private const val DESIGN_W = 200f
private const val DESIGN_H = 214f

private val White = Color(0xFFEDEBFF)
private val ShoeDark = Color(0xFF14161A)
private val Screen = Color(0xFF020305)
private val Gold = Color(0xFFFFD84D)

/** Свечение: три прохода линии с убывающей прозрачностью. */
private inline fun glow(color: Color, width: Float, draw: (Color, Float) -> Unit) {
    draw(color.copy(alpha = 0.10f), width + 9f)
    draw(color.copy(alpha = 0.22f), width + 4.5f)
    draw(color, width)
}

/**
 * Робот LifeOS, нарисованный кодом: чёрная толстовка, светящееся лицо, наушники.
 * Внешний вид задаёт [skin], поведение — [mood], [walking] и [facingRight].
 * Сегодня это 2.5D-графика на Canvas; настоящие 3D-модели можно подключить позже,
 * не меняя места, где робот используется.
 */
@Composable
fun RobotView(
    skin: RobotSkin,
    mood: RobotMood,
    modifier: Modifier = Modifier,
    walking: Boolean = false,
    facingRight: Boolean = true,
) {
    val g = Color(skin.glow)
    val hoodie = Color(skin.hoodie)

    val infinite = rememberInfiniteTransition(label = "robot")
    val bob by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val step by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "step",
    )
    val talk by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse), label = "talk",
    )

    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 4200))
            blink = true
            delay(130)
            blink = false
        }
    }

    Canvas(modifier.aspectRatio(DESIGN_W / DESIGN_H)) {
        val s = size.width / DESIGN_W
        scale(s, pivot = Offset.Zero) {
            translate(0f, 14f) {
                val swing = if (walking) (step - 0.5f) * 2f else 0f
                val tilt = if (walking) (if (facingRight) 4f else -4f) else 0f
                rotate(tilt, pivot = Offset(100f, 170f)) {
                    drawRobot(skin, g, hoodie, mood, (bob - 0.5f) * 5f, swing, talk, blink)
                }
            }
        }
    }
}

private fun DrawScope.drawRobot(
    skin: RobotSkin,
    g: Color,
    hoodie: Color,
    mood: RobotMood,
    by: Float,
    swing: Float,
    talk: Float,
    blink: Boolean,
) {
    val light = lerp(hoodie, Color.White, 0.14f)

    // Тень
    drawOval(g.copy(alpha = 0.10f), Offset(40f, 180f), Size(120f, 18f))
    drawOval(Color.Black.copy(alpha = 0.5f), Offset(54f, 184f), Size(92f, 11f))

    // Кроссовки: при ходьбе поднимаются по очереди
    for (i in 0..1) {
        val cx = if (i == 0) 78f else 122f
        val lift = max(0f, (if (i == 0) swing else -swing)) * 8f
        val top = 166f - lift
        drawRoundRect(ShoeDark, Offset(cx - 20f, top), Size(40f, 22f), CornerRadius(11f))
        drawRoundRect(White, Offset(cx - 21f, top + 15f), Size(42f, 8f), CornerRadius(4f))
        drawRoundRect(g.copy(alpha = 0.9f), Offset(cx - 13f, top + 5f), Size(26f, 3f), CornerRadius(1.5f))
    }

    // Руки (за телом)
    val armColor = lerp(hoodie, Color.White, 0.08f)
    val leftShoulder = Offset(66f, 114f + by)
    val rightShoulder = Offset(134f, 114f + by)
    val leftHand: Offset
    val rightHand: Offset
    when (mood) {
        RobotMood.WORKING -> {
            leftHand = Offset(86f, 142f + by)
            rightHand = Offset(114f, 142f + by)
        }
        RobotMood.LISTENING -> {
            leftHand = Offset(52f, 142f + by + swing * 5f)
            rightHand = Offset(150f, 84f + by)
        }
        RobotMood.TALKING -> {
            leftHand = Offset(52f, 142f + by + swing * 5f)
            rightHand = Offset(152f, 116f + by - talk * 10f)
        }
        else -> {
            leftHand = Offset(52f, 142f + by + swing * 5f)
            rightHand = Offset(148f, 142f + by - swing * 5f)
        }
    }
    drawLine(armColor, leftShoulder, leftHand, strokeWidth = 18f, cap = StrokeCap.Round)
    drawLine(armColor, rightShoulder, rightHand, strokeWidth = 18f, cap = StrokeCap.Round)

    // Тело (толстовка)
    val bodyTop = 98f + by
    drawRoundRect(
        Brush.verticalGradient(listOf(light, hoodie), startY = bodyTop, endY = bodyTop + 70f),
        Offset(62f, bodyTop), Size(76f, 70f), CornerRadius(26f),
    )
    drawRoundRect(g.copy(alpha = 0.35f), Offset(62f, bodyTop), Size(76f, 70f), CornerRadius(26f), style = Stroke(1.5f))
    glow(g, 2f) { c, w -> drawLine(c, Offset(100f, 112f + by), Offset(100f, 164f + by), strokeWidth = w, cap = StrokeCap.Round) }
    val logo = Path().apply {
        moveTo(108f, 118f + by); lineTo(113f, 118f + by); lineTo(113f, 130f + by)
        lineTo(124f, 130f + by); lineTo(124f, 136f + by); lineTo(108f, 136f + by); close()
    }
    drawPath(logo, g.copy(alpha = 0.25f), style = Stroke(5f, join = StrokeJoin.Round))
    drawPath(logo, g)

    // Часы в руках, когда робот ставит будильник
    if (mood == RobotMood.WORKING) {
        val wiggle = (talk - 0.5f) * 3f
        val c = Offset(100f + wiggle, 140f + by)
        drawCircle(Color(0xFF0B0F12), 16f, c)
        glow(g, 2.5f) { col, w -> drawCircle(col, 16f, c, style = Stroke(w)) }
        drawLine(g, c, Offset(c.x, c.y - 10f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(g, c, Offset(c.x + 8f, c.y + 2f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawCircle(g, 3f, Offset(c.x - 11f, c.y - 17f))
        drawCircle(g, 3f, Offset(c.x + 11f, c.y - 17f))
    }
    drawCircle(White, 9.5f, leftHand)
    drawCircle(White, 9.5f, rightHand)

    // Капюшон с «ушками»
    val headTop = 14f + by
    drawCircle(hoodie, 14f, Offset(55f, 26f + by))
    drawCircle(hoodie, 14f, Offset(145f, 26f + by))
    drawRoundRect(
        Brush.verticalGradient(listOf(light, hoodie), startY = headTop, endY = headTop + 100f),
        Offset(40f, headTop), Size(120f, 100f), CornerRadius(48f),
    )
    drawRoundRect(g.copy(alpha = 0.30f), Offset(40f, headTop), Size(120f, 100f), CornerRadius(48f), style = Stroke(2f))

    // Экран-лицо
    drawRoundRect(Screen, Offset(54f, 34f + by), Size(92f, 66f), CornerRadius(30f))
    drawRoundRect(g.copy(alpha = 0.25f), Offset(54f, 34f + by), Size(92f, 66f), CornerRadius(30f), style = Stroke(1.5f))
    drawOval(g.copy(alpha = 0.22f), Offset(62f, 74f + by), Size(14f, 8f))
    drawOval(g.copy(alpha = 0.22f), Offset(124f, 74f + by), Size(14f, 8f))

    drawEyes(g, mood, by, blink)
    drawMouth(g, mood, by, talk)

    // Наушники
    for (cx in listOf(30f, 152f)) {
        drawRoundRect(Color(0xFF0D0F12), Offset(cx, 48f + by), Size(18f, 42f), CornerRadius(9f))
        glow(g, 3f) { c, w -> drawRoundRect(c, Offset(cx + 1f, 49f + by), Size(16f, 40f), CornerRadius(8f), style = Stroke(w)) }
    }
    glow(g, 3.5f) { c, w ->
        drawArc(c, 192f, 156f, false, Offset(36f, 2f + by), Size(128f, 112f), style = Stroke(w, cap = StrokeCap.Round))
    }

    drawAccessory(skin.accessory, g, by)
}

private fun DrawScope.drawEyes(g: Color, mood: RobotMood, by: Float, blink: Boolean) {
    val y = 62f + by
    for (cx in listOf(80f, 120f)) {
        val left = cx < 100f
        when {
            blink -> glow(g, 5f) { c, w -> drawLine(c, Offset(cx - 9f, y + 2f), Offset(cx + 9f, y + 2f), strokeWidth = w, cap = StrokeCap.Round) }
            mood == RobotMood.LISTENING -> {
                drawCircle(g.copy(alpha = 0.18f), 12f, Offset(cx, y))
                drawCircle(g, 7f, Offset(cx, y))
            }
            mood == RobotMood.THINKING -> {
                drawCircle(g.copy(alpha = 0.18f), 9f, Offset(cx + 3f, y - 4f))
                drawCircle(g, 5f, Offset(cx + 3f, y - 4f))
            }
            mood == RobotMood.WORKING -> {
                val dir = if (left) 1f else -1f
                val p = Path().apply {
                    moveTo(cx - 8f * dir, y - 8f); lineTo(cx + 6f * dir, y); lineTo(cx - 8f * dir, y + 8f)
                }
                glow(g, 5f) { c, w -> drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
            else -> glow(g, 6f) { c, w ->
                drawArc(c, 200f, 140f, false, Offset(cx - 11f, y - 8f), Size(22f, 18f), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawMouth(g: Color, mood: RobotMood, by: Float, talk: Float) {
    val y = 80f + by
    when (mood) {
        RobotMood.TALKING -> drawOval(g, Offset(93f, y), Size(14f, 4f + talk * 9f))
        RobotMood.LISTENING -> drawCircle(g, 3.5f, Offset(100f, y + 4f))
        RobotMood.THINKING -> glow(g, 3f) { c, w -> drawLine(c, Offset(94f, y + 4f), Offset(106f, y + 4f), strokeWidth = w, cap = StrokeCap.Round) }
        else -> glow(g, 3.5f) { c, w ->
            drawArc(c, 20f, 140f, false, Offset(91f, y - 4f), Size(18f, 11f), style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawAccessory(accessory: Accessory, g: Color, by: Float) {
    when (accessory) {
        Accessory.NONE -> Unit
        Accessory.CROWN -> {
            val crown = Path().apply {
                moveTo(76f, 14f + by); lineTo(78f, -6f + by); lineTo(90f, 4f + by); lineTo(100f, -10f + by)
                lineTo(110f, 4f + by); lineTo(122f, -6f + by); lineTo(124f, 14f + by); close()
            }
            glow(Gold, 3f) { c, w -> drawPath(crown, c, style = Stroke(w, join = StrokeJoin.Round)) }
            for (p in listOf(Offset(78f, -6f + by), Offset(100f, -10f + by), Offset(122f, -6f + by))) {
                drawCircle(Color.White, 3.5f, p)
            }
        }
        Accessory.ANTENNA -> {
            glow(g, 3f) { c, w -> drawLine(c, Offset(100f, 14f + by), Offset(100f, -4f + by), strokeWidth = w, cap = StrokeCap.Round) }
            drawCircle(g.copy(alpha = 0.3f), 9f, Offset(100f, -6f + by))
            drawCircle(g, 5f, Offset(100f, -6f + by))
        }
        Accessory.CAP -> {
            drawRoundRect(Color(0xFF1B2A3A), Offset(50f, 4f + by), Size(100f, 22f), CornerRadius(11f))
            drawRoundRect(Color(0xFF1B2A3A), Offset(96f, 18f + by), Size(60f, 9f), CornerRadius(4.5f))
            glow(g, 2f) { c, w -> drawRoundRect(c, Offset(50f, 4f + by), Size(100f, 22f), CornerRadius(11f), style = Stroke(w)) }
        }
    }
}
