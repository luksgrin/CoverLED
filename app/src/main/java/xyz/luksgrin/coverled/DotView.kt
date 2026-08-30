package xyz.luksgrin.coverled

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws up to [Settings.MAX_DOTS] indicators (circles, or a user PNG tinted per color) either in a
 * row or as the vertices of a regular polygon (2 = pair, 3 = triangle, 4 = square, 5 = pentagon,
 * 6 = hexagon), centered at ([posX], [posY]) on a black background.
 */
class DotView(context: Context) : View(context) {

    var colors: IntArray = intArrayOf(Color.WHITE)
        set(value) { field = value.copyOf(value.size.coerceAtMost(Settings.MAX_DOTS)); invalidate() }

    var dotDp = 14f
        set(v) { field = v; invalidate() }
    var gapDp = 12f
    var geometric = true
        set(v) { field = v; invalidate() }
    /** White/grayscale shape with alpha; tinted with each color. null = circle. */
    var shape: Bitmap? = null
        set(v) { field = v; invalidate() }

    var posX = 0.5f
        set(v) { field = v; invalidate() }
    var posY = 0.5f
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val n = colors.size
        if (n == 0) return
        val density = resources.displayMetrics.density
        val d = dotDp * density
        val gap = gapDp * density
        val pts = layout(n, d, gap)

        // bounding box of the group, to keep it fully on screen
        val minX = pts.minOf { it.first } - d / 2; val maxX = pts.maxOf { it.first } + d / 2
        val minY = pts.minOf { it.second } - d / 2; val maxY = pts.maxOf { it.second } + d / 2
        val cx = (posX * width).coerceIn(-minX + d / 4, width - maxX - d / 4)
        val cy = (posY * height).coerceIn(-minY + d / 4, height - maxY - d / 4)

        colors.forEachIndexed { i, c ->
            val x = cx + pts[i].first; val y = cy + pts[i].second
            val bmp = shape
            if (bmp != null) {
                paint.colorFilter = PorterDuffColorFilter(c, PorterDuff.Mode.MULTIPLY)
                dst.set(x - d / 2, y - d / 2, x + d / 2, y + d / 2)
                canvas.drawBitmap(bmp, null, dst, paint)
            } else {
                paint.colorFilter = null
                paint.color = c
                canvas.drawCircle(x, y, d / 2, paint)
            }
        }
    }

    /** Offsets from the group center for n items. */
    private fun layout(n: Int, d: Float, gap: Float): List<Pair<Float, Float>> {
        if (n == 1) return listOf(0f to 0f)
        if (!geometric || n == 2) {
            val total = n * d + (n - 1) * gap
            return List(n) { i -> (-total / 2 + d / 2 + i * (d + gap)) to 0f }
        }
        // regular polygon whose side ≈ d + gap
        val r = (d + gap) / (2 * sin(PI / n)).toFloat()
        val start = if (n % 2 == 1) -PI / 2 else -PI / 2 + PI / n   // odd: vertex up; even: flat top
        return List(n) { i ->
            val a = start + i * 2 * PI / n
            (r * cos(a)).toFloat() to (r * sin(a)).toFloat()
        }
    }
}
