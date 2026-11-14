package com.lifeos.app.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lifeos.app.data.RobotSkin
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import kotlin.math.roundToInt

/**
 * Карточка напоминания: появляется, когда вы говорите «напомни…». Не похожа на блокнот для заметок: тёмная карточка
 * с колокольчиком, где слова сразу превращаются в «когда» и «что», а внизу кнопки «Сохранить» и «Отмена».
 * Нужно разрешение «Поверх других приложений».
 */
class ReminderOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private var view: ComposeView? = null
    private var owner: RobotOverlay.OverlayOwner? = null

    // 0 — включаем микрофон, 1 — слушаем, 2 — итог на подтверждение, 3 — сохранено, 4 — не вышло
    private var mode by mutableStateOf(0)
    private var liveText by mutableStateOf("")
    private var question by mutableStateOf("")
    private var whenText by mutableStateOf("")
    private var titleText by mutableStateOf("")
    private var message by mutableStateOf("")
    private var skin by mutableStateOf<RobotSkin>(SkinCatalog.default)

    var onSave: () -> Unit = {}
    var onCancel: () -> Unit = {}

    fun show(newSkin: RobotSkin) {
        skin = newSkin
        mode = 0
        liveText = ""
        question = ""
        if (view != null) return
        val o = RobotOverlay.OverlayOwner().also { it.start() }
        owner = o
        val cv = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { Card() }
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

    fun preparing() {
        mode = 0
    }

    fun listening(text: String, ask: String) {
        mode = 1
        liveText = text
        question = ask
    }

    fun confirm(whenLabel: String, title: String) {
        whenText = whenLabel
        titleText = title
        mode = 2
    }

    fun done(whenLabel: String, title: String) {
        whenText = whenLabel
        titleText = title
        mode = 3
    }

    fun fail(text: String) {
        message = text
        mode = 4
    }

    fun hide() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        owner?.stop()
        owner = null
        onSave = {}
        onCancel = {}
    }

    @Composable
    private fun Card() {
        val ink = Color(0xFFF4F4F6)
        val dim = Color(0xFF9A9AA3)
        val accent = Color(skin.glow)
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, shape)
                .clip(shape)
                .background(Color(0xFF1B1B20))
                .border(1.5.dp, accent.copy(alpha = 0.8f), shape)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Notifications, null, tint = Color(0xFF0B0B0D), modifier = Modifier.size(24.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Напоминание", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (mode) {
                            0 -> "Включаю микрофон…"
                            1 -> "Говорите, я слушаю"
                            2 -> "Проверьте и сохраните"
                            3 -> "Готово, напомню"
                            else -> "Не получилось"
                        },
                        color = dim,
                        fontSize = 13.sp,
                    )
                }
                RobotView(skin, if (mode == 3) RobotMood.HAPPY else RobotMood.LISTENING, Modifier.size(64.dp))
            }

            Column(Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(top = 12.dp)) {
                when (mode) {
                    0, 1 -> {
                        Text(
                            question.ifBlank { "Что и когда напомнить? Например: «завтра в 15:00 сходить к стоматологу»" },
                            color = dim,
                            fontSize = 14.sp,
                        )
                        if (liveText.isNotBlank()) {
                            Text(liveText + if (mode == 1) " ▍" else "", color = ink, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    2, 3 -> {
                        Text(whenText, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(titleText, color = ink, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        if (mode == 2) {
                            Text("Сохранится само через пару секунд", color = dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                                Text("Придёт уведомлением и громким окном", color = dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    else -> Text(message, color = ink, fontSize = 16.sp)
                }
            }

            if (mode == 2) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .border(1.5.dp, accent, RoundedCornerShape(50))
                            .clickable { onCancel() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Отмена", color = ink, fontSize = 15.sp) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable { onSave() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Сохранить", color = Color(0xFF0B0B0D), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
