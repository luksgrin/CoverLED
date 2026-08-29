package dev.lucas.coverled

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Draws N evenly spaced filled circles, centered, on a black background. */
class DotView(context: Context) : View(context) {

    var colors: IntArray = intArrayOf(Color.WHITE)
        set(value) { field = value; invalidate() }

    /** Dot diameter in dp. Small on purpose: this is an LED, not a widget. */
    var dotDp = 14f
    var gapDp = 12f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (colors.isEmpty()) return
        val density = resources.displayMetrics.density
        val d = dotDp * density
        val gap = gapDp * density
        val total = colors.size * d + (colors.size - 1) * gap
        val startX = (width - total) / 2f + d / 2f
        val cy = height / 2f
        val r = min(d / 2f, width / (colors.size * 2f + 2f))
        colors.forEachIndexed { i, c ->
            paint.color = c
            canvas.drawCircle(startX + i * (d + gap), cy, r, paint)
        }
    }
}
