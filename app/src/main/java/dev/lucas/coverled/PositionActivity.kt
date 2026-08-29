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

/**
 * Lets the user drag the dot to where it should appear on the cover screen.
 * Shows a scaled outline of the cover (748×720 on the Flip5, taken live from CoverDisplays);
 * while dragging, a real preview dot is shown on the actual cover and follows the finger.
 */
class PositionActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var preview: CoverPreview
    private var previewShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        title = "Dot position"

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        root.addView(TextView(this).apply {
            text = "Drag the dot to where you want it on the cover screen. Close the phone (or peek at the cover) to see the live preview."
        })
        preview = CoverPreview(this)
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(16); bottomMargin = dp(16) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply { text = "Center"; setOnClickListener { preview.set(0.5f, 0.5f) } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply { text = "Top"; setOnClickListener { preview.set(0.5f, 0.15f) } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply { text = "Bottom"; setOnClickListener { preview.set(0.5f, 0.8f) } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        // Live preview on the real cover; the indicator re-reads dotX/dotY on every change.
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
        private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppColors.DEFAULT_COLOR }
        private val paintHint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = dp(12).toFloat(); textAlign = Paint.Align.CENTER }
        private val aspect: Float = CoverDisplays.cover(context)?.let { d ->
            val p = android.graphics.Point(); @Suppress("DEPRECATION") d.getRealSize(p); p.x.toFloat() / p.y
        } ?: (748f / 720f)
        private var x = settings.dotX
        private var y = settings.dotY

        fun set(nx: Float, ny: Float) { x = nx.coerceIn(0f, 1f); y = ny.coerceIn(0f, 1f); settings.dotX = x; settings.dotY = y; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            var fw = w.toFloat(); var fh = fw / aspect
            if (fh > h) { fh = h.toFloat(); fw = fh * aspect }
            frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
        }

        override fun onDraw(c: Canvas) {
            val r = dp(24).toFloat()
            c.drawRoundRect(frame, r, r, paintFill)
            c.drawRoundRect(frame, r, r, paintFrame)
            c.drawCircle(frame.left + x * frame.width(), frame.top + y * frame.height(), dp(9).toFloat(), paintDot)
            c.drawText("⚡ charging info appears here", frame.centerX(), frame.bottom - dp(14), paintHint)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    set((e.x - frame.left) / frame.width(), (e.y - frame.top) / frame.height())
                    return true
                }
                MotionEvent.ACTION_UP -> { performClick(); return true }
            }
            return super.onTouchEvent(e)
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
