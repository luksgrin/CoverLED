package xyz.luksgrin.coverled

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
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

        val ui = OneUi(this)
        val (page, content) = ui.page(getString(R.string.cat_position), showBack = true) { finish() }
        content.addView(ui.note(getString(R.string.position_intro)))
        preview = CoverPreview(this)
        content.addView(ui.card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)))
            addView(ui.buttonBar(
                getString(R.string.position_center) to { preview.setDot(0.5f, 0.5f) },
                getString(R.string.position_top) to { preview.setDot(0.5f, 0.15f) },
                getString(R.string.position_bottom) to { preview.setDot(0.5f, 0.8f) },
            ))
            addView(ui.button(getString(R.string.position_reset_battery)) { preview.resetBattery() })
        }))
        OneUi.setContent(this, page)
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

        // real cover geometry: aspect and the cutout insets. The inset band is drawn as a hint only;
        // positions are fractions of the full panel and may go anywhere, exactly as in the indicator.
        private var aspect = 748f / 720f
        private var insetB = 0f                       // bottom cutout inset (fraction) → default charging-line spot
        private val cameras = ArrayList<RectF>()      // camera rectangles as fractions of the panel
        init {
            CoverDisplays.cover(context)?.let { d ->
                val p = android.graphics.Point(); @Suppress("DEPRECATION") d.getRealSize(p); aspect = p.x.toFloat() / p.y
                d.cutout?.let { c ->
                    insetB = c.safeInsetBottom / p.y.toFloat()
                    c.boundingRects.filter { !it.isEmpty }.forEach { r ->
                        cameras.add(RectF(r.left / p.x.toFloat(), r.top / p.y.toFloat(), r.right / p.x.toFloat(), r.bottom / p.y.toFloat()))
                    }
                }
            }
        }

        private var dx = settings.dotX; private var dy = settings.dotY
        private var bx = settings.batteryX
        private var by = settings.batteryY.let { if (it < 0f) Settings.defaultBatteryY(insetB) else it }
        private var dragging = 0   // 0 none, 1 dot, 2 battery

        fun setDot(x: Float, y: Float) { dx = x.coerceIn(0f, 1f); dy = y.coerceIn(0f, 1f); settings.dotX = dx; settings.dotY = dy; invalidate() }
        fun resetBattery() { settings.resetBattery(); bx = 0.5f; by = Settings.defaultBatteryY(insetB); invalidate() }
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
            // camera area(s): a hint only — things placed there may be hidden by the cameras
            c.save(); c.clipRect(frame)
            cameras.forEach { c.drawRoundRect(px(it.left), py(it.top), px(it.right), py(it.bottom), dp(10).toFloat(), dp(10).toFloat(), paintCutout) }
            c.restore()
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
