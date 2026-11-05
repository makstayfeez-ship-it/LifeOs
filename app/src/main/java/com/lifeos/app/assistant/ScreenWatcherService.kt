package com.lifeos.app.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow

/** Что сейчас на экране и чего нельзя загораживать роботу. Хранятся только прямоугольники, без текста. */
object ScreenState {
    val obstacles = MutableStateFlow<List<Rect>>(emptyList())

    @Volatile
    var connected = false
}

/**
 * Необязательная «зоркость» робота. Сервис специальных возможностей смотрит, где на экране
 * кнопки, поля ввода и другие элементы управления, и передаёт роботу только их границы.
 * Тексты, пароли и содержимое экрана не читаются и не сохраняются.
 */
class ScreenWatcherService : AccessibilityService() {
    private var lastScan = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenState.connected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 500) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        val screen = resources.displayMetrics
        val screenArea = screen.widthPixels.toLong() * screen.heightPixels
        val found = ArrayList<Rect>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES && found.size < MAX_RECTS) {
            val node = stack.removeLast()
            visited++
            if (node.isVisibleToUser && (node.isClickable || node.isEditable)) {
                val r = Rect()
                node.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height()
                // Пропускаем пустые и огромные «контейнеры на весь экран»
                if (area > 0 && area < screenArea * 4 / 10) found += r
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
        }
        ScreenState.obstacles.value = found
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ScreenState.connected = false
        ScreenState.obstacles.value = emptyList()
        return super.onUnbind(intent)
    }

    private companion object {
        const val MAX_NODES = 400
        const val MAX_RECTS = 80
    }
}
