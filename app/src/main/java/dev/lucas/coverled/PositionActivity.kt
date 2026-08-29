package dev.lucas.coverled

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

/**
 * Drag the dot and the charging line to where they should appear on the cover screen.
 * A scaled outline of the cover is shown (aspect from the real display); the darker band is the
 * camera cutout. While this screen is open a real preview is shown on the cover and follows the finger.
 */
class PositionActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var preview: CoverPreview
    private var previewShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(16)); applySystemInsetsPadding(top = true) }
        root.addView(com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = getString(R.string.cat_position)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = -dp(16); rightMargin = -dp(16) })
        root.addView(TextView(this).apply { text = getString(R.string.position_intro) })
        preview = CoverPreview(this)
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(16); bottomMargin = dp(16) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: Int, action: () -> Unit) = buttons.addView(
            Button(this).apply { text = getString(label); setOnClickListener { action() } },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        btn(R.string.position_center) { preview.setDot(0.5f, 0.5f) }
        btn(R.string.position_top) { preview.setDot(0.5f, 0.15f) }
        btn(R.string.position_bottom) { preview.setDot(0.5f, 0.8f) }
        btn(R.string.position_reset_battery) { preview.setBattery(0.5f, 0.9f) }
        root.addView(buttons)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        IndicatorController.show(this, intArrayOf(AppColors.DEFAULT_COLOR)); previewShown = true
    }

    override fun onStop() {
        super.onStop()
        if (previewShown) { IndicatorController.hide(this); previewShown = false }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class CoverPreview(context: Context) : View(context) {
        private val frame = RectF()
        private val paintFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = Color.GRAY }
        private val paintFill = Paint().apply { color = Color.BLACK }
        private val paintCutout = Paint().apply { color = Color.rgb(40, 40, 40) }
        private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppColors.DEFAULT_COLOR }
        private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(160, 160, 160); textSize = dp(11).toFloat(); textAlign = Paint.Align.CENTER }
        private val sample = "⚡ 79 % · 1 h 41 min"

        // real cover geometry: size and cutout rect (fractions)
        private var aspect = 748f / 720f
        private var cutout: RectF? = null
        init {
            CoverDisplays.cover(context)?.let { d ->
                val p = android.graphics.Point(); @Suppress("DEPRECATION") d.getRealSize(p); aspect = p.x.toFloat() / p.y
                d.cutout?.boundingRects?.firstOrNull()?.let { r ->
                    cutout = RectF(r.left / p.x.toFloat(), r.top / p.y.toFloat(), r.right / p.x.toFloat(), r.bottom / p.y.toFloat())
                }
            }
        }

        private var dx = settings.dotX; private var dy = settings.dotY
        private var bx = settings.batteryX; private var by = settings.batteryY
        private var dragging = 0   // 0 none, 1 dot, 2 battery

        fun setDot(x: Float, y: Float) { dx = x.coerceIn(0f, 1f); dy = y.coerceIn(0f, 1f); settings.dotX = dx; settings.dotY = dy; invalidate() }
        fun setBattery(x: Float, y: Float) { bx = x.coerceIn(0f, 1f); by = y.coerceIn(0f, 1f); settings.batteryX = bx; settings.batteryY = by; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            var fw = w.toFloat(); var fh = fw / aspect
            if (fh > h) { fh = h.toFloat(); fw = fh * aspect }
            frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
        }

        private fun px(fx: Float) = frame.left + fx * frame.width()
        private fun py(fy: Float) = frame.top + fy * frame.height()

        override fun onDraw(c: Canvas) {
            val r = dp(24).toFloat()
            c.drawRoundRect(frame, r, r, paintFill)
            cutout?.let { c.drawRect(px(it.left), py(it.top), px(it.right), py(it.bottom), paintCutout) }
            c.drawRoundRect(frame, r, r, paintFrame)
            c.drawCircle(px(dx), py(dy), dp(9).toFloat(), paintDot)
            c.drawText(sample, px(bx), py(by) + paintText.textSize / 3, paintText)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val toDot = hypot(e.x - px(dx), e.y - py(dy)); val toBat = hypot(e.x - px(bx), e.y - py(by))
                    dragging = if (toBat < toDot) 2 else 1
                    move(e); return true
                }
                MotionEvent.ACTION_MOVE -> { move(e); return true }
                MotionEvent.ACTION_UP -> { dragging = 0; performClick(); return true }
            }
            return super.onTouchEvent(e)
        }

        private fun move(e: MotionEvent) {
            val fx = (e.x - frame.left) / frame.width(); val fy = (e.y - frame.top) / frame.height()
            if (dragging == 2) setBattery(fx, fy) else setDot(fx, fy)
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
