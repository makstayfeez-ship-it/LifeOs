package com.lifeos.app.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lifeos.app.data.RobotSkin
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.Snow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Робот поверх всех окон. Появляется по вызову, сам ходит по экрану и уходит в сторону,
 * если встал на кнопку или поле ввода (границы приходят из [ScreenWatcherService]).
 * Требует разрешения «Поверх других приложений».
 */
class RobotOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null
    private var wanderJob: Job? = null
    private var moveJob: Job? = null

    /** Нажатие на робота — отпустить его. */
    var onTap: () -> Unit = {}

    private var skin by mutableStateOf<RobotSkin>(SkinCatalog.default)
    private var currentMood by mutableStateOf(RobotMood.HAPPY)
    private var bubble by mutableStateOf<String?>(null)
    private var walking by mutableStateOf(false)
    private var facingRight by mutableStateOf(true)
    private var visible by mutableStateOf(false)

    private fun dp(v: Int) = (v * density).roundToInt()

    private val winW get() = dp(WINDOW_DP)
    private val robotW get() = dp(ROBOT_W_DP)
    private val robotH get() = dp(ROBOT_H_DP)
    private val bubbleH get() = dp(BUBBLE_H_DP)

    private val screenW get() = context.resources.displayMetrics.widthPixels
    private val screenH get() = context.resources.displayMetrics.heightPixels

    val isShown: Boolean get() = view != null

    fun show(newSkin: RobotSkin) {
        skin = newSkin
        if (view != null) {
            visible = true
            return
        }
        val o = OverlayOwner().also { it.start() }
        owner = o
        val cv = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { OverlayContent() }
        }
        val lp = WindowManager.LayoutParams(
            winW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - winW - dp(8)
            y = screenH - robotH - dp(150)
        }
        params = lp
        view = cv
        runCatching { wm.addView(cv, lp) }.onFailure {
            view = null
            params = null
            o.stop()
            owner = null
            return
        }
        visible = true
        startWander()
    }

    fun setMood(m: RobotMood) {
        currentMood = m
    }

    /** Показывает реплику над роботом. null — убрать. */
    fun say(text: String?) {
        val p = params ?: return
        val v = view ?: return
        val had = bubble != null
        bubble = text
        val has = text != null
        if (has && !had) {
            p.y = max(0, p.y - bubbleH)
            runCatching { wm.updateViewLayout(v, p) }
        } else if (!has && had) {
            p.y = min(screenH - robotH, p.y + bubbleH)
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    /** Робот машет рукой и исчезает. */
    fun hide() {
        if (view == null) return
        say(null)
        visible = false
        scope.launch {
            delay(400)
            removeNow()
        }
    }

    fun release() {
        removeNow()
        scope.cancel()
    }

    private fun removeNow() {
        wanderJob?.cancel()
        moveJob?.cancel()
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        params = null
        owner?.stop()
        owner = null
        bubble = null
        walking = false
        visible = false
    }

    // ---------- движение ----------

    private fun robotRect(x: Int = params?.x ?: 0, y: Int = params?.y ?: 0, withBubble: Boolean = bubble != null): Rect {
        val left = x + (winW - robotW) / 2
        val top = y + if (withBubble) bubbleH else 0
        return Rect(left, top, left + robotW, top + robotH)
    }

    private fun overlapArea(a: Rect, b: Rect): Long {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        return if (w > 0 && h > 0) w.toLong() * h else 0L
    }

    private fun overlapScore(rect: Rect): Long =
        ScreenState.obstacles.value.sumOf { overlapArea(it, rect) }

    private fun startWander() {
        wanderJob?.cancel()
        wanderJob = scope.launch {
            var idleMs = 0L
            var limit = Random.nextLong(5_000, 10_000)
            while (isActive) {
                delay(700)
                idleMs += 700
                if (walking) continue
                val blocked = overlapScore(robotRect()) > 0
                if (blocked || idleMs >= limit) {
                    idleMs = 0
                    limit = Random.nextLong(5_000, 10_000)
                    val target = pickSpot()
                    moveTo(target.first, target.second)
                }
            }
        }
    }

    /** Выбирает место с наименьшим перекрытием элементов экрана, не рядом с текущим. */
    private fun pickSpot(): Pair<Int, Int> {
        val p = params ?: return 0 to 0
        val hasBubble = bubble != null
        val margin = dp(8)
        val xs = listOf(margin, (screenW - winW) / 2, screenW - winW - margin)
        val top = dp(60)
        val bottom = screenH - robotH - dp(120)
        val ys = listOf(top, top + (bottom - top) / 3, top + (bottom - top) * 2 / 3, bottom)

        val scored = mutableListOf<Triple<Int, Int, Long>>()
        for (x in xs) for (y in ys) {
            if (hypot((x - p.x).toFloat(), (y - p.y).toFloat()) < dp(80)) continue
            scored += Triple(x, y, overlapScore(robotRect(x, y, hasBubble)))
        }
        if (scored.isEmpty()) return p.x to p.y
        val best = scored.minOf { it.third }
        val pool = scored.filter { it.third == best }
        val pick = pool[Random.nextInt(pool.size)]
        return pick.first to pick.second
    }

    private fun moveTo(tx: Int, ty: Int) {
        moveJob?.cancel()
        moveJob = scope.launch {
            val p = params ?: return@launch
            val v = view ?: return@launch
            facingRight = tx >= p.x
            walking = true
            val speed = 240f * density // пикселей в секунду
            var last = SystemClock.uptimeMillis()
            try {
                while (isActive) {
                    delay(16)
                    val now = SystemClock.uptimeMillis()
                    val dt = (now - last) / 1000f
                    last = now
                    val dx = (tx - p.x).toFloat()
                    val dy = (ty - p.y).toFloat()
                    val dist = hypot(dx, dy)
                    val step = max(1f, speed * dt)
                    if (dist <= step) {
                        p.x = tx
                        p.y = ty
                        runCatching { wm.updateViewLayout(v, p) }
                        break
                    }
                    p.x += (dx / dist * step).roundToInt()
                    p.y += (dy / dist * step).roundToInt()
                    runCatching { wm.updateViewLayout(v, p) }
                }
            } finally {
                walking = false
            }
        }
    }

    // ---------- вид ----------

    @androidx.compose.runtime.Composable
    private fun OverlayContent() {
        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "robot-in",
        )
        Column(
            Modifier
                .width(WINDOW_DP.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0.5f, 1f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val text = bubble
            if (text != null) {
                Box(Modifier.fillMaxWidth().height(BUBBLE_H_DP.dp), contentAlignment = Alignment.BottomCenter) {
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clip(shape)
                            .background(CardColor.copy(alpha = 0.96f))
                            .border(1.dp, Color(skin.glow).copy(alpha = 0.8f), shape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text, color = Snow, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            RobotView(
                skin = skin,
                mood = currentMood,
                walking = walking,
                facingRight = facingRight,
                modifier = Modifier
                    .width(ROBOT_W_DP.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            )
        }
    }

    /** Окну вне Activity нужен свой «жизненный цикл», иначе Compose не запустится. */
    internal class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun start() {
            controller.performAttach()
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    private companion object {
        const val WINDOW_DP = 200
        const val ROBOT_W_DP = 120
        const val ROBOT_H_DP = 129
        const val BUBBLE_H_DP = 76
    }
}
