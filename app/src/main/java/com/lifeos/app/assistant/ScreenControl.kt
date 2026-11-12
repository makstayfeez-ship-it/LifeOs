package com.lifeos.app.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Помощник смотрит на экран и управляет им: читает текст, нажимает на кнопки по названию, листает, печатает.
 * Работает только пока пользователь сам включил LifeOS в «Специальных возможностях» Android. Без этого Android не даёт
 * ни одному приложению видеть чужой экран, это защита от слежки, и обойти её нельзя.
 * Пароли не читаются. Текст экрана уходит в ИИ только тогда, когда вы просите помощника посмотреть на экран.
 */
object ScreenControl {
    const val NEED_ACCESS =
        "Чтобы я видел экран и нажимал на нём, включите LifeOS в специальных возможностях: Проверка помощника, строка «Управление экраном»."

    val enabled: Boolean get() = ScreenWatcherService.instance != null

    private fun service(): AccessibilityService? = ScreenWatcherService.instance

    fun readScreen(): String {
        val svc = service() ?: return NEED_ACCESS
        val root = svc.rootInActiveWindow ?: return "Сейчас не вижу экран"
        val lines = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        var chars = 0
        while (queue.isNotEmpty() && visited < 700 && chars < 4000) {
            val n = queue.removeFirst()
            visited++
            if (!n.isVisibleToUser) continue
            if (!n.isPassword) {
                val t = (n.text ?: n.contentDescription)?.toString()?.trim()
                if (!t.isNullOrEmpty() && lines.add(t.take(300))) chars += t.length
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        val app = root.packageName?.toString().orEmpty()
        return if (lines.isEmpty()) "На экране нет текста (приложение $app)"
        else "Приложение $app. На экране: " + lines.joinToString("; ")
    }

    fun click(text: String): String {
        val svc = service() ?: return NEED_ACCESS
        val root = svc.rootInActiveWindow ?: return "Сейчас не вижу экран"
        val node = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isVisibleToUser }
            ?: return "Не нашёл на экране «$text»"
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "Нажал «$text»"
        val r = Rect()
        node.getBoundsInScreen(r)
        return if (tap(svc, r.exactCenterX(), r.exactCenterY())) "Нажал «$text»" else "Не получилось нажать «$text»"
    }

    fun scroll(down: Boolean): String {
        val svc = service() ?: return NEED_ACCESS
        val root = svc.rootInActiveWindow ?: return "Сейчас не вижу экран"
        val node = largestScrollable(root)
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node != null && node.performAction(action)) return if (down) "Прокрутил вниз" else "Прокрутил вверх"
        // Запасной путь: смахнуть пальцем
        val m = svc.resources.displayMetrics
        val x = m.widthPixels / 2f
        val from = m.heightPixels * (if (down) 0.72f else 0.28f)
        val to = m.heightPixels * (if (down) 0.28f else 0.72f)
        val path = Path().apply { moveTo(x, from); lineTo(x, to) }
        return if (gesture(svc, path, 300)) (if (down) "Прокрутил вниз" else "Прокрутил вверх") else "Не получилось прокрутить"
    }

    fun type(text: String): String {
        val svc = service() ?: return NEED_ACCESS
        val root = svc.rootInActiveWindow ?: return "Сейчас не вижу экран"
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: firstEditable(root)
            ?: return "Не вижу поля ввода. Нажмите на поле и повторите"
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Напечатал" else "Не получилось напечатать"
    }

    private fun largestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 700) {
            val n = stack.removeLast()
            visited++
            if (n.isScrollable && n.isVisibleToUser) {
                val r = Rect()
                n.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height()
                if (area > bestArea) { best = n; bestArea = area }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return best
    }

    private fun firstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 700) {
            val n = stack.removeLast()
            visited++
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return null
    }

    private fun tap(svc: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(svc, path, 60)
    }

    private fun gesture(svc: AccessibilityService, path: Path, durationMs: Long): Boolean = runCatching {
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        svc.dispatchGesture(g, null, null)
    }.getOrDefault(false)
}
