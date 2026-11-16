package com.lifeos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent

private const val CELL_DP = 50
private const val PAD_DP = 8
private const val MODE_CLOSED = 0
private const val MODE_HOLD = 1
private const val MODE_TAP = 2

/** Где окно со стикерами оказалось относительно кнопки: по этому смещению считаем, над каким стикером палец. */
private class PopupOrigin {
    var dx = 0f
    var dy = 0f
}

private class AbovePositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val origin: PopupOrigin,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = maxOf(marginPx, windowSize.width - popupContentSize.width - marginPx)
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(marginPx, maxX)
        var y = anchorBounds.top - popupContentSize.height - gapPx
        if (y < marginPx) y = anchorBounds.bottom + gapPx
        origin.dx = (x - anchorBounds.left).toFloat()
        origin.dy = (y - anchorBounds.top).toFloat()
        return IntOffset(x, y)
    }
}

/**
 * Выбор стикера «зажал и провёл»: кнопка показывает выбранный стикер.
 * Зажмите её, не отпуская палец проведите к нужному стикеру (он подсветится) и отпустите: выбор сохранится.
 * Обычное короткое нажатие тоже открывает окно, где стикер выбирается касанием.
 */
@Composable
fun HoldEmojiPicker(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Зажмите, проведите пальцем к нужному стикеру и отпустите",
) {
    val accent = LocalAccent.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val cols = minOf(6, options.size).coerceAtLeast(1)
    val rows = (options.size + cols - 1) / cols
    val cellPx = with(density) { CELL_DP.dp.toPx() }
    val padPx = with(density) { PAD_DP.dp.toPx() }

    var mode by remember { mutableIntStateOf(MODE_CLOSED) }
    var highlighted by remember { mutableIntStateOf(-1) }
    val origin = remember { PopupOrigin() }
    val provider = remember(density) {
        AbovePositionProvider(
            gapPx = with(density) { 10.dp.roundToPx() },
            marginPx = with(density) { 8.dp.roundToPx() },
            origin = origin,
        )
    }
    val onSelectNow by rememberUpdatedState(onSelect)

    fun hit(p: Offset): Int {
        val x = p.x - origin.dx - padPx
        val y = p.y - origin.dy - padPx
        if (x < 0f || y < 0f) return -1
        val c = (x / cellPx).toInt()
        val r = (y / cellPx).toInt()
        if (c >= cols || r >= rows) return -1
        val i = r * cols + c
        return if (i < options.size) i else -1
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .size(64.dp)
                .clip(shape)
                .background(CardColor)
                .border(1.5.dp, accent, shape)
                .pointerInput(options, cellPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var tapped = false
                        // Ждём, пока палец не пролежит достаточно долго, не сдвинувшись
                        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var waiting = true
                            while (waiting) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    waiting = false
                                } else if (change.changedToUpIgnoreConsumed()) {
                                    tapped = true
                                    waiting = false
                                } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    waiting = false
                                }
                            }
                            false
                        } == null

                        if (timedOut) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            highlighted = hit(down.position)
                            mode = MODE_HOLD
                            var tracking = true
                            while (tracking) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    highlighted = -1
                                    mode = MODE_CLOSED
                                    tracking = false
                                } else {
                                    val index = hit(change.position)
                                    if (change.changedToUpIgnoreConsumed()) {
                                        if (index >= 0) onSelectNow(options[index])
                                        highlighted = -1
                                        mode = MODE_CLOSED
                                        tracking = false
                                    } else if (index != highlighted) {
                                        highlighted = index
                                        if (index >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                }
                            }
                        } else if (tapped) {
                            highlighted = -1
                            mode = MODE_TAP
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(value, fontSize = 32.sp)

            if (mode != MODE_CLOSED) {
                val tapMode = mode == MODE_TAP
                Popup(
                    popupPositionProvider = provider,
                    onDismissRequest = if (tapMode) ({ mode = MODE_CLOSED }) else null,
                    properties = PopupProperties(focusable = tapMode),
                ) {
                    val panel = RoundedCornerShape(24.dp)
                    Column(
                        Modifier
                            .clip(panel)
                            .background(CardColor)
                            .border(1.dp, accent.copy(alpha = 0.7f), panel)
                            .padding(PAD_DP.dp),
                    ) {
                        for (r in 0 until rows) {
                            Row {
                                for (c in 0 until cols) {
                                    val i = r * cols + c
                                    if (i >= options.size) {
                                        Spacer(Modifier.size(CELL_DP.dp))
                                        continue
                                    }
                                    val on = i == highlighted
                                    Box(
                                        Modifier
                                            .size(CELL_DP.dp)
                                            .then(
                                                if (tapMode) {
                                                    Modifier.clickable {
                                                        onSelect(options[i])
                                                        mode = MODE_CLOSED
                                                    }
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(if (on) 48.dp else 42.dp)
                                                .clip(CircleShape)
                                                .background(if (on) accent else Color.Transparent),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(options[i], fontSize = if (on) 30.sp else 24.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            hint,
            color = GrayText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
