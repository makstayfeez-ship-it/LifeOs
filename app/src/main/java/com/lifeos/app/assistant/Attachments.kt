package com.lifeos.app.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * То, что пользователь прикрепил к вопросу: фото (уходят ИИ как картинки), страницы PDF (тоже как картинки)
 * или текст файла (txt, docx, csv и другие текстовые).
 */
class Attachment(
    val name: String,
    /** Картинки в JPEG, закодированные в base64. */
    val images: List<String> = emptyList(),
    /** Текст файла, если это документ. */
    val text: String = "",
    /** Небольшая картинка для показа в строке ввода. */
    val preview: Bitmap? = null,
    /** Пояснение для ИИ, например «PDF, показаны первые 4 страницы из 12». */
    val note: String = "",
)

class AttachmentException(message: String) : Exception(message)

object AttachmentLoader {
    private const val MAX_SIDE = 1280
    private const val MAX_TEXT = 60_000
    private const val MAX_PDF_PAGES = 4

    suspend fun load(context: Context, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mime = cr.getType(uri).orEmpty().lowercase()
        val name = displayName(context, uri)
        val lower = name.lowercase()
        when {
            mime.startsWith("image/") || lower.matches(Regex(".*\\.(jpe?g|png|webp|heic|heif|bmp|gif)$")) -> {
                val bmp = decodeImage(context, uri) ?: throw AttachmentException("Не получилось открыть фото «$name»")
                Attachment(name, images = listOf(toBase64(bmp)), preview = thumb(bmp))
            }
            mime == "application/pdf" || lower.endsWith(".pdf") -> loadPdf(context, uri, name)
            lower.endsWith(".docx") || mime.contains("wordprocessingml") -> {
                val text = readDocx(context, uri)
                if (text.isBlank()) throw AttachmentException("В файле «$name» нет текста")
                Attachment(name, text = text.take(MAX_TEXT), note = "документ Word")
            }
            else -> {
                val bytes = cr.openInputStream(uri)?.use { it.readNBytesCompat(MAX_TEXT * 3) }
                    ?: throw AttachmentException("Не получилось открыть «$name»")
                val sample = bytes.take(2000)
                if (sample.count { it.toInt() == 0 } > 5) throw AttachmentException("Такой файл («$name») я пока не умею читать. Подойдут фото, PDF, Word и текстовые файлы.")
                Attachment(name, text = String(bytes, Charsets.UTF_8).take(MAX_TEXT))
            }
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> if (n.isNotBlank()) return n }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "файл"
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val degrees = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        return fit(raw, degrees)
    }

    /** Приводит картинку к размеру, который принимает ИИ, и при необходимости поворачивает. */
    private fun fit(src: Bitmap, degrees: Float): Bitmap {
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(src.width, src.height))
        if (scale >= 1f && degrees == 0f) return src
        val m = Matrix().apply {
            postScale(scale, scale)
            if (degrees != 0f) postRotate(degrees)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun toBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun thumb(bmp: Bitmap): Bitmap {
        val s = 160f / max(bmp.width, bmp.height)
        return if (s >= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
    }

    private fun loadPdf(context: Context, uri: Uri, name: String): Attachment {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw AttachmentException("Не получилось открыть «$name»")
        try {
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: Exception) {
                throw AttachmentException("PDF «$name» не открывается (возможно, он защищён паролем)")
            }
            try {
                val total = renderer.pageCount
                if (total == 0) throw AttachmentException("В PDF «$name» нет страниц")
                val pages = minOf(total, MAX_PDF_PAGES)
                val images = ArrayList<String>()
                var preview: Bitmap? = null
                for (i in 0 until pages) {
                    val page = renderer.openPage(i)
                    try {
                        val scale = minOf(2f, MAX_SIDE.toFloat() / max(page.width, page.height))
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        images += toBase64(bmp)
                        if (preview == null) preview = thumb(bmp)
                    } finally {
                        page.close()
                    }
                }
                val note = if (total > pages) "PDF, показаны первые $pages страниц из $total" else "PDF, страниц: $total"
                return Attachment(name, images = images, preview = preview, note = note)
            } finally {
                renderer.close()
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    /** Достаёт текст из .docx: это zip, в котором word/document.xml. */
    private fun readDocx(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: return ""
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == "word/document.xml") {
                    val xml = String(zip.readNBytesCompat(MAX_TEXT * 8), Charsets.UTF_8)
                    return xml
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<w:tab/>"), "\t")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                        .lines().joinToString("\n") { it.trim() }
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                }
                e = zip.nextEntry
            }
        }
        return ""
    }
}
