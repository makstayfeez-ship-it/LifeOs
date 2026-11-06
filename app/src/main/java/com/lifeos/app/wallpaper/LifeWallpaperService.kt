package com.lifeos.app.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.lifeos.app.container
import com.lifeos.app.ui.theme.AccentChoices
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Живые обои: тёмный фон LifeOS с плавно дрейфующими светящимися шарами в цвете выбранного акцента. */
class LifeWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LifeEngine()

    private inner class LifeEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val scope = CoroutineScope(Dispatchers.Main)
        private var job: Job? = null
        private var accent = AccentChoices[0].let { Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
        private var visible = false
        private var t = 0f
        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            job = scope.launch {
                applicationContext.container.settings.flow.map { it.accent }.collect { i ->
                    val c = AccentChoices.getOrElse(i) { AccentChoices[0] }
                    accent = Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            if (visible) handler.post(frame)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            job?.cancel()
            scope.cancel()
            super.onDestroy()
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                canvas.drawColor(Color.parseColor("#0B0B0D"))
                t += 0.012f
                val orbs = listOf(
                    Triple(0.25f, 0.20f, 0.55f),
                    Triple(0.80f, 0.45f, 0.45f),
                    Triple(0.35f, 0.80f, 0.60f),
                )
                orbs.forEachIndexed { i, (bx, by, size) ->
                    val x = w * (bx + 0.08f * sin(t * (0.7f + i * 0.3f) + i))
                    val y = h * (by + 0.06f * cos(t * (0.5f + i * 0.2f) + i * 2))
                    val r = w * size
                    paint.shader = RadialGradient(
                        x, y, r,
                        intArrayOf(Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                    canvas.drawCircle(x, y, r, paint)
                }
                paint.shader = null
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
