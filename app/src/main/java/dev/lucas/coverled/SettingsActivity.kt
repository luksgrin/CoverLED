package dev.lucas.coverled

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.lucas.coverled.Settings as Prefs

/** One settings category per screen; which one is chosen by [EXTRA_SECTION]. */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_BEAT = "beat"
        const val SECTION_LAYOUT = "layout"
        const val SECTION_SHAPE = "shape"
        const val SECTION_DEV = "dev"
        fun intent(from: android.content.Context, section: String) =
            Intent(from, SettingsActivity::class.java).putExtra(EXTRA_SECTION, section)
    }

    private lateinit var st: Prefs
    private lateinit var ui: OneUi
    private val handler = Handler(Looper.getMainLooper())
    private var txtLog: TextView? = null
    private var shapeImage: ImageView? = null
    private var shapeText: TextView? = null

    private val pickShape = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val err = ShapeLoader.import(this, uri)
        if (err == null) st.customShape = true
        refreshShape(err)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        st = Prefs(this); ui = OneUi(this)
        val section = intent.getStringExtra(EXTRA_SECTION) ?: SECTION_BEAT
        val title = when (section) {
            SECTION_LAYOUT -> R.string.cat_layout; SECTION_SHAPE -> R.string.cat_shape; SECTION_DEV -> R.string.cat_dev; else -> R.string.cat_beat
        }
        val (page, content) = ui.page(getString(title), showBack = true) { finish() }
        when (section) {
            SECTION_LAYOUT -> buildLayout(content)
            SECTION_SHAPE -> buildShape(content)
            SECTION_DEV -> buildDev(content)
            else -> buildBeat(content)
        }
        OneUi.setContent(this, page)
    }

    // ---------------------------------------------------------------- beat & brightness
    private fun buildBeat(c: LinearLayout) {
        c.addView(ui.card(ui.switchRow(getString(R.string.blink_switch), null, st.blinkEnabled) { st.blinkEnabled = it }))

        c.addView(ui.header(getString(R.string.beat_style)))
        c.addView(ui.card(*ui.radioRows(listOf(
            Triple(Prefs.STYLE_HARD, getString(R.string.style_blink), getString(R.string.style_blink_help)),
            Triple(Prefs.STYLE_BREATHE, getString(R.string.style_breathe), getString(R.string.style_breathe_help)),
            Triple(Prefs.STYLE_LUBDUB, getString(R.string.style_lubdub), getString(R.string.style_lubdub_help)),
        ), st.beatStyle) { st.beatStyle = it }.toTypedArray()))

        c.addView(ui.header(getString(R.string.timing)))
        c.addView(ui.card(
            ui.sliderRow(getString(R.string.beat_length).substringBefore(':'), 200f, 3000f, 100f, st.blinkOnMs.toFloat(), { "${it.toInt()} ms" }) { st.blinkOnMs = it.toInt() },
            ui.sliderRow(getString(R.string.dark_gap).substringBefore(':'), 500f, 15000f, 500f, st.blinkOffMs.toFloat(), { "${it.toInt()} ms" }) { st.blinkOffMs = it.toInt() },
        ))

        c.addView(ui.header(getString(R.string.display)))
        c.addView(ui.card(
            ui.sliderRow(getString(R.string.brightness).substringBefore(':'), 1f, 100f, 1f, (st.brightness * 100).toInt().toFloat(), { "${it.toInt()} %" }) { st.brightness = it / 100f },
            ui.switchRow(getString(R.string.battery_switch), null, st.showBattery) { st.showBattery = it },
        ))
        c.addView(ui.note(getString(R.string.brightness_help)))
    }

    // ---------------------------------------------------------------- layout & size
    private fun buildLayout(c: LinearLayout) {
        c.addView(ui.header(getString(R.string.layout_title)))
        c.addView(ui.card(*ui.radioRows(listOf(
            Triple(Prefs.ARR_ROW, getString(R.string.arr_row), getString(R.string.arr_row_help)),
            Triple(Prefs.ARR_GEOMETRIC, getString(R.string.arr_shape), getString(R.string.arr_shape_help)),
            Triple(Prefs.ARR_CYCLE, getString(R.string.arr_cycle), getString(R.string.arr_cycle_help)),
        ), st.arrangement) { st.arrangement = it }.toTypedArray()))
        c.addView(ui.note(getString(R.string.layout_help)))

        c.addView(ui.header(getString(R.string.size)))
        c.addView(ui.card(
            ui.sliderRow(getString(R.string.dot_size).substringBefore(':'), 8f, 64f, 1f, st.dotSizeDp.toFloat(), { "${it.toInt()} dp" }) { st.dotSizeDp = it.toInt() },
        ))
    }

    // ---------------------------------------------------------------- shape
    private fun buildShape(c: LinearLayout) {
        val img = ImageView(this).apply {
            setBackgroundColor(0xFF000000.toInt()); setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20))
            clipToOutline = true
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = ui.dp(22f); setColor(0xFF000000.toInt()) }
        }
        val txt = TextView(this).apply { textSize = 15f; gravity = Gravity.CENTER; setPadding(0, ui.dp(12), 0, 0); setTextColor(getColor(R.color.ou_text_secondary)) }
        shapeImage = img; shapeText = txt
        c.addView(ui.header(getString(R.string.current_shape)))
        c.addView(ui.card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
            addView(img, LinearLayout.LayoutParams(ui.dp(112), ui.dp(112)))
            addView(txt)
            addView(ui.button(getString(R.string.shape_load), true) { pickShape.launch(arrayOf("image/png")) })
            addView(ui.button(getString(R.string.shape_circle)) { ShapeLoader.clear(this@SettingsActivity); st.customShape = false; refreshShape(null) })
        }))
        c.addView(ui.header(getString(R.string.shape_rules_title)))
        c.addView(ui.card(ui.note(getString(R.string.shape_rules)).apply { setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16)); setLineSpacing(ui.dp(4f), 1f) }))
        refreshShape(null)
    }

    private fun refreshShape(error: String?) {
        val img = shapeImage ?: return; val txt = shapeText ?: return
        val bmp = if (st.customShape) ShapeLoader.load(this) else null
        if (bmp != null) {
            img.setImageBitmap(bmp); img.setColorFilter(AppColors.DEFAULT_COLOR, PorterDuff.Mode.MULTIPLY)
            txt.text = getString(R.string.shape_custom)
        } else {
            img.setImageDrawable(getDrawable(R.drawable.ic_dot)); img.clearColorFilter()
            txt.text = getString(R.string.shape_is_circle)
        }
        if (error != null) txt.text = getString(R.string.shape_rejected, error)
    }

    // ---------------------------------------------------------------- developer
    private fun buildDev(c: LinearLayout) {
        val log = TextView(this).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 11f; setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12)); setTextColor(getColor(R.color.ou_text)) }
        txtLog = log
        c.addView(ui.header(getString(R.string.dev_test_title)))
        c.addView(ui.card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(16))
            addView(ui.button(getString(R.string.dev_post_notif), true) {
                log("Close the phone… test notification in 8 s"); handler.postDelayed({ TestNotification.post(this@SettingsActivity, true); log("posted") }, 8_000)
            })
            addView(ui.button(getString(R.string.dev_cancel_notif)) { TestNotification.post(this@SettingsActivity, false); log("cancelled") })
        }))
        c.addView(ui.header(getString(R.string.dev_manual)))
        c.addView(ui.card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(16))
            addView(ui.buttonBar(
                getString(R.string.dev_one_dot) to { show(MainActivity.PALETTE.copyOf(1)) },
                getString(R.string.dev_three_dots) to { log("Close the phone… 3 dots in 8 s"); handler.postDelayed({ show(MainActivity.PALETTE.copyOf(3)) }, 8_000) },
                getString(R.string.dev_hide) to { IndicatorController.hide(this@SettingsActivity); log("HIDE sent") },
            ))
        }))
        val fold = TextView(this).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 11f; setTextColor(getColor(R.color.ou_text)) }
        c.addView(ui.header(getString(R.string.dev_device)))
        c.addView(ui.card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12))
            addView(TextView(this@SettingsActivity).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 11f; text = CoverDisplays.describe(this@SettingsActivity); setTextColor(getColor(R.color.ou_text)) })
            addView(fold)
        }))
        c.addView(ui.header(getString(R.string.dev_log)))
        c.addView(ui.card(log))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@SettingsActivity).windowLayoutInfo(this@SettingsActivity).collect { info ->
                    val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    fold.text = if (f == null) "FoldingFeature: none" else "FoldingFeature: ${f.state} ${f.orientation}"
                }
            }
        }
    }

    private fun show(colors: IntArray) {
        IndicatorController.show(this, colors)
            .onSuccess { log("Launched on display $it") }
            .onFailure { log("FAILED: ${it.message}") }
    }

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        txtLog?.text = "$ts $msg\n${txtLog?.text}"
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
