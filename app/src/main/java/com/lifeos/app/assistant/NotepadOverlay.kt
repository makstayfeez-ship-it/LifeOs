package com.lifeos.app.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lifeos.app.data.RobotSkin
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import kotlin.math.roundToInt

/**
 * Блокнот с помощником: появляется, когда вы говорите «заметка». Слова вписываются в листок по мере речи,
 * а «остановить» (или кнопка «Сохранить») закрывает запись, и заметка сохраняется. Нужно разрешение «Поверх других приложений».
 */
class NotepadOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private var view: ComposeView? = null
    private var owner: RobotOverlay.OverlayOwner? = null

    private var text by mutableStateOf("")

    /** 0 — слушаем, 1 — сохранено, 2 — ничего не записано. */
    private var state by mutableStateOf(0)
    private var skin by mutableStateOf<RobotSkin>(SkinCatalog.default)

    /** Нажатие на «Сохранить». */
    var onStop: () -> Unit = {}

    fun show(newSkin: RobotSkin) {
        skin = newSkin
        text = ""
        state = 0
        if (view != null) return
        val o = RobotOverlay.OverlayOwner().also { it.start() }
        owner = o
        val cv = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { Paper() }
        }
        val width = (context.resources.displayMetrics.widthPixels * 0.92f).roundToInt()
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (96 * density).roundToInt()
        }
        view = cv
        runCatching { wm.addView(cv, lp) }.onFailure {
            view = null
            o.stop()
            owner = null
        }
    }

    fun update(newText: String) {
        text = newText
    }

    /** Показывает итог: «Заметка сохранена» или «Ничего не записано». Потом нужно вызвать [hide]. */
    fun finish(saved: Boolean) {
        state = if (saved) 1 else 2
    }

    fun hide() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        owner?.stop()
        owner = null
        onStop = {}
    }

    @androidx.compose.runtime.Composable
    private fun Paper() {
        val paper = Color(0xFFFFF6DC)
        val ink = Color(0xFF3B2F1E)
        val line = Color(0x2236607A)
        val accent = Color(skin.glow)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(paper),
        ) {
            // Спираль блокнота
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(8) { Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF8A7A62))) }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Заметка", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (state) {
                            1 -> "Сохранено в заметки"
                            2 -> "Ничего не записано"
                            else -> "Говорю — я записываю"
                        },
                        color = ink.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
                RobotView(skin, if (state == 1) RobotMood.HAPPY else RobotMood.LISTENING, Modifier.size(84.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .padding(horizontal = 18.dp)
                    .drawBehind {
                        // Линейки тетрадного листа
                        var y = 28.dp.toPx()
                        while (y < size.height) {
                            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.5f)
                            y += 28.dp.toPx()
                        }
                    },
            ) {
                val shown = text.takeLast(500)
                if (shown.isBlank()) {
                    Text("Начните говорить…", color = ink.copy(alpha = 0.35f), fontSize = 18.sp, lineHeight = 28.sp)
                } else {
                    Text(shown + if (state == 0) " ▍" else "", color = ink, fontSize = 18.sp, lineHeight = 28.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (state == 0) "Скажите «остановить», чтобы сохранить" else "",
                    color = ink.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (state == 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable { onStop() }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) { Text("Сохранить", color = Color(0xFF0B0B0D), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
