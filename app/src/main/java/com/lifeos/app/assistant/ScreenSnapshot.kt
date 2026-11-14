package com.lifeos.app.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.graphics.Bitmap
import android.text.InputType
import android.util.Base64
import android.view.View
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Снимок экрана на тот момент, когда LifeOS позвали как системного помощника (кнопка питания, жест от угла экрана).
 * Android сам отдаёт помощнику по умолчанию текст и картинку экрана под ним, и для этого не нужны «Специальные возможности»
 * и «ограниченные настройки». Управлять экраном (нажимать) так нельзя, только смотреть.
 * Снимок живёт в памяти пару минут и никуда не уходит, пока вы сами не спросите про экран.
 */
object ScreenSnapshot {
    private const val MAX_AGE_MS = 3 * 60_000L
    private const val MAX_TEXT = 5000
    private const val MAX_NODES = 900
    private const val MAX_SIDE = 1280

    @Volatile private var text: String = ""
    @Volatile private var app: String = ""
    @Volatile private var link: String = ""
    @Volatile private var shot: Bitmap? = null
    @Volatile private var at: Long = 0L

    /** Новый вызов помощника: старый снимок уже неактуален. */
    fun clear() {
        text = ""
        app = ""
        link = ""
        shot = null
        at = 0L
    }

    fun fresh(): Boolean =
        at != 0L && System.currentTimeMillis() - at <= MAX_AGE_MS && (text.isNotBlank() || shot != null)

    fun storeStructure(structure: AssistStructure?, content: AssistContent?) {
        if (structure == null) return
        val lines = LinkedHashSet<String>()
        val queue = ArrayDeque<AssistStructure.ViewNode>()
        for (i in 0 until structure.windowNodeCount) queue.addLast(structure.getWindowNodeAt(i).rootViewNode)
        var visited = 0
        var chars = 0
        while (queue.isNotEmpty() && visited < MAX_NODES && chars < MAX_TEXT) {
            val n = queue.removeFirst()
            visited++
            if (n.visibility != View.VISIBLE) continue
            if (!isPassword(n.inputType)) {
                val t = (n.text ?: n.contentDescription ?: n.hint)?.toString()?.trim()
                if (!t.isNullOrEmpty() && lines.add(t.take(300))) chars += t.length
            }
            for (i in 0 until n.childCount) queue.addLast(n.getChildAt(i))
        }
        text = lines.joinToString("; ")
        app = structure.activityComponent?.packageName.orEmpty()
        link = content?.webUri?.toString().orEmpty()
        at = System.currentTimeMillis()
    }

    fun storeScreenshot(bmp: Bitmap?) {
        if (bmp == null) return
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(bmp.width, bmp.height))
        shot = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bmp
        }
        at = System.currentTimeMillis()
    }

    /** Текст экрана для ИИ или для голосового ответа; null, если снимка нет. */
    fun describe(): String? {
        if (!fresh()) return null
        val t = text
        if (t.isBlank()) return if (shot != null) "Текст на экране прочитать не получилось, но есть картинка экрана." else null
        val where = buildString {
            if (app.isNotBlank()) append("Приложение ").append(app).append(". ")
            if (link.isNotBlank()) append("Страница ").append(link).append(". ")
        }
        return where + "На экране (в момент, когда вы позвали помощника): " + t
    }

    /** Снимок как вложение для тихого помощника: картинка экрана и его текст. */
    fun toAttachment(): Attachment? {
        if (!fresh()) return null
        val bmp = shot
        val images = if (bmp != null) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            listOf(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
        } else {
            emptyList()
        }
        val preview = bmp?.let {
            val s = 160f / max(it.width, it.height)
            if (s >= 1f) it else Bitmap.createScaledBitmap(it, (it.width * s).toInt().coerceAtLeast(1), (it.height * s).toInt().coerceAtLeast(1), true)
        }
        return Attachment(
            name = "Экран",
            images = images,
            text = if (images.isEmpty()) text else "",
            preview = preview,
            note = "снимок экрана телефона на момент вызова помощника" + if (app.isNotBlank()) " (приложение $app)" else "",
        )
    }

    private fun isPassword(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val v = inputType and InputType.TYPE_MASK_VARIATION
        return (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
}
