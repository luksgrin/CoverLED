package dev.lucas.coverled

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
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
    private val handler = Handler(Looper.getMainLooper())
    private var txtLog: TextView? = null

    private val pickShape = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val err = ShapeLoader.import(this, uri)
        if (err == null) st.customShape = true
        refreshShape(err)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        st = Prefs(this)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        val container = findViewById<FrameLayout>(R.id.container)
        container.applySystemInsetsPadding()

        when (intent.getStringExtra(EXTRA_SECTION)) {
            SECTION_LAYOUT -> { toolbar.title = getString(R.string.cat_layout); layoutInflater.inflate(R.layout.section_layout, container); bindLayout() }
            SECTION_SHAPE -> { toolbar.title = getString(R.string.cat_shape); layoutInflater.inflate(R.layout.section_shape, container); bindShape() }
            SECTION_DEV -> { toolbar.title = getString(R.string.cat_dev); layoutInflater.inflate(R.layout.section_dev, container); bindDev() }
            else -> { toolbar.title = getString(R.string.cat_beat); layoutInflater.inflate(R.layout.section_beat, container); bindBeat() }
        }
    }

    // ---------------------------------------------------------------- beat & brightness
    private fun bindBeat() {
        findViewById<MaterialSwitch>(R.id.swBlink).apply {
            isChecked = st.blinkEnabled; setOnCheckedChangeListener { _, v -> st.blinkEnabled = v }
        }
        findViewById<MaterialSwitch>(R.id.swBattery).apply {
            isChecked = st.showBattery; setOnCheckedChangeListener { _, v -> st.showBattery = v }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.tgStyle).apply {
            check(when (st.beatStyle) { Prefs.STYLE_HARD -> R.id.rbHard; Prefs.STYLE_LUBDUB -> R.id.rbLubdub; else -> R.id.rbBreathe })
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) st.beatStyle = when (id) { R.id.rbHard -> Prefs.STYLE_HARD; R.id.rbLubdub -> Prefs.STYLE_LUBDUB; else -> Prefs.STYLE_BREATHE }
            }
        }
        slider(R.id.sbBlinkOn, R.id.lblBlinkOn, st.blinkOnMs.toFloat(), { "Beat length: ${it.toInt()} ms" }) { st.blinkOnMs = it.toInt() }
        slider(R.id.sbBlinkOff, R.id.lblBlinkOff, st.blinkOffMs.toFloat(), { "Dark gap: ${it.toInt()} ms" }) { st.blinkOffMs = it.toInt() }
        slider(R.id.sbBrightness, R.id.lblBrightness, (st.brightness * 100).toInt().toFloat(), { "Brightness: ${it.toInt()} %" }) { st.brightness = it / 100f }
    }

    // ---------------------------------------------------------------- layout & size
    private fun bindLayout() {
        val help = findViewById<TextView>(R.id.txtArrangementHelp)
        fun describe(a: String) = when (a) {
            Prefs.ARR_ROW -> "Dots side by side."
            Prefs.ARR_CYCLE -> "One dot; every beat it takes the next app's color."
            else -> "2 pair · 3 triangle · 4 square · 5 pentagon · 6 hexagon."
        }
        help.text = describe(st.arrangement)
        findViewById<MaterialButtonToggleGroup>(R.id.tgArrangement).apply {
            check(when (st.arrangement) { Prefs.ARR_ROW -> R.id.rbRow; Prefs.ARR_CYCLE -> R.id.rbCycle; else -> R.id.rbGeometric })
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                st.arrangement = when (id) { R.id.rbRow -> Prefs.ARR_ROW; R.id.rbCycle -> Prefs.ARR_CYCLE; else -> Prefs.ARR_GEOMETRIC }
                help.text = describe(st.arrangement)
            }
        }
        slider(R.id.sbSize, R.id.lblSize, st.dotSizeDp.toFloat(), { "Dot size: ${it.toInt()} dp" }) { st.dotSizeDp = it.toInt() }
    }

    // ---------------------------------------------------------------- shape
    private fun bindShape() {
        findViewById<Button>(R.id.btnShape).setOnClickListener { pickShape.launch(arrayOf("image/png")) }
        findViewById<Button>(R.id.btnShapeClear).setOnClickListener { ShapeLoader.clear(this); st.customShape = false; refreshShape(null) }
        refreshShape(null)
    }

    private fun refreshShape(error: String?) {
        val img = findViewById<ImageView>(R.id.imgShape) ?: return
        val txt = findViewById<TextView>(R.id.txtShape)
        val bmp = if (st.customShape) ShapeLoader.load(this) else null
        if (bmp != null) {
            img.setImageBitmap(bmp); img.setColorFilter(AppColors.DEFAULT_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY)
            txt.text = "Custom shape, tinted with each app's color"
        } else {
            img.setImageDrawable(getDrawable(R.drawable.ic_dot)); img.clearColorFilter()
            txt.text = "Circle"
        }
        if (error != null) txt.text = "Rejected: $error"
    }

    // ---------------------------------------------------------------- developer
    private fun bindDev() {
        txtLog = findViewById(R.id.txtLog)
        findViewById<TextView>(R.id.txtDisplays).text = CoverDisplays.describe(this)
        findViewById<Button>(R.id.btnTestNotif).setOnClickListener {
            log("Close the phone… test notification in 8 s"); handler.postDelayed({ TestNotification.post(this, true); log("posted") }, 8_000)
        }
        findViewById<Button>(R.id.btnCancelNotif).setOnClickListener { TestNotification.post(this, false); log("cancelled") }
        findViewById<Button>(R.id.btnShowNow).setOnClickListener { show(MainActivity.PALETTE.copyOf(1)) }
        findViewById<Button>(R.id.btnShowMulti).setOnClickListener {
            log("Close the phone… 3 dots in 8 s"); handler.postDelayed({ show(MainActivity.PALETTE.copyOf(3)) }, 8_000)
        }
        findViewById<Button>(R.id.btnHide).setOnClickListener { IndicatorController.hide(this); log("HIDE sent") }

        val txtFold = findViewById<TextView>(R.id.txtFold)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@SettingsActivity).windowLayoutInfo(this@SettingsActivity).collect { info ->
                    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    txtFold.text = if (fold == null) "FoldingFeature: none" else "FoldingFeature: ${fold.state} ${fold.orientation}"
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

    // ---------------------------------------------------------------- helpers
    private fun slider(sliderId: Int, labelId: Int, value: Float, label: (Float) -> String, onChange: (Float) -> Unit) {
        val lbl = findViewById<TextView>(labelId)
        findViewById<Slider>(sliderId).apply {
            this.value = value.coerceIn(valueFrom, valueTo)
            lbl.text = label(this.value)
            addOnChangeListener { _, v, fromUser -> lbl.text = label(v); if (fromUser) onChange(v) }
        }
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
