package dev.lucas.coverled

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
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

/** Main-screen debug console for the Phase 2 spike. */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTOSHOW = "autoshow"
        /** adb: --ei testnotif 1 posts a test notification, 0 cancels it. */
        const val EXTRA_TESTNOTIF = "testnotif"
        /** adb: --ez clearall true dismisses all pending notifications (debug). */
        const val EXTRA_CLEARALL = "clearall"
        private const val CHANNEL = "test"
        private const val TEST_ID = 4242
        val PALETTE = intArrayOf(
            Color.rgb(33, 150, 243), Color.rgb(76, 175, 80), Color.rgb(156, 39, 176), Color.rgb(244, 67, 54)
        )
    }

    private lateinit var txtLog: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("CoverLED", "MainActivity onCreate display=${display?.displayId} extras=${intent?.extras?.keySet()?.joinToString()} autoshow=${intent?.getIntExtra(EXTRA_AUTOSHOW, -1)}")
        setContentView(R.layout.activity_main)

        txtLog = findViewById(R.id.txtLog)
        val txtDisplays = findViewById<TextView>(R.id.txtDisplays)
        val txtFold = findViewById<TextView>(R.id.txtFold)

        txtDisplays.text = CoverDisplays.describe(this)

        findViewById<Button>(R.id.btnShowNow).setOnClickListener {
            show(intArrayOf(Color.rgb(33, 150, 243)))
        }
        findViewById<Button>(R.id.btnShowDelayed).setOnClickListener {
            log("Close the phone now… launching in 8 s")
            handler.postDelayed({ show(intArrayOf(Color.rgb(33, 150, 243))) }, 8_000)
        }
        findViewById<Button>(R.id.btnShowMulti).setOnClickListener {
            log("Close the phone now… launching 3 dots in 8 s")
            handler.postDelayed({
                show(intArrayOf(Color.rgb(33, 150, 243), Color.rgb(76, 175, 80), Color.rgb(156, 39, 176)))
            }, 8_000)
        }
        findViewById<Button>(R.id.btnHide).setOnClickListener {
            IndicatorController.hide(this); log("HIDE sent")
        }

        // Debug hook so the spike can be driven over adb while the phone is closed:
        //   adb shell am start -n dev.lucas.coverled/.MainActivity --ei autoshow 3
        // Shows N dots immediately; 0 hides.
        if (intent.hasExtra(EXTRA_AUTOSHOW)) handleAutoshow(intent.getIntExtra(EXTRA_AUTOSHOW, 1))
        if (intent.hasExtra(EXTRA_TESTNOTIF)) testNotification(intent.getIntExtra(EXTRA_TESTNOTIF, 1) > 0)
        if (intent.getBooleanExtra(EXTRA_CLEARALL, false)) LedNotificationListener.debugClearAll()

        bindSettings()
        findViewById<Button>(R.id.btnColors).setOnClickListener { startActivity(Intent(this, ColorsActivity::class.java)) }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnTestNotif).setOnClickListener {
            log("Close the phone… test notification in 8 s")
            handler.postDelayed({ testNotification(true) }, 8_000)
        }
        findViewById<Button>(R.id.btnCancelNotif).setOnClickListener { testNotification(false) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationState.get(this@MainActivity).snapshot.collect { snap ->
                    findViewById<TextView>(R.id.txtState).text =
                        if (snap.isEmpty()) "pending: (none)"
                        else "pending:\n" + snap.entries.joinToString("\n") { "  ${it.key} ×${it.value}" }
                }
            }
        }

        // Fold-state readout (spec §5.3). Runs while this activity is started.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                        txtFold.text = when {
                            fold == null -> "FoldingFeature: none (closed, or single-screen layout)"
                            else -> "FoldingFeature: state=${fold.state} orientation=${fold.orientation}"
                        }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_AUTOSHOW)) handleAutoshow(intent.getIntExtra(EXTRA_AUTOSHOW, 1))
        if (intent.hasExtra(EXTRA_TESTNOTIF)) testNotification(intent.getIntExtra(EXTRA_TESTNOTIF, 1) > 0)
        if (intent.getBooleanExtra(EXTRA_CLEARALL, false)) LedNotificationListener.debugClearAll()
    }

    override fun onResume() {
        super.onResume()
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        findViewById<TextView>(R.id.txtAccess).text =
            (if (enabled) "notification access: GRANTED" else "notification access: NOT granted") +
            (if (Settings.canDrawOverlays(this)) "\ndisplay over other apps: GRANTED" else "\ndisplay over other apps: NOT granted (LED will be blocked)")
    }

    private fun testNotification(post: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (!post) { nm.cancel(TEST_ID); log("test notification cancelled"); return }
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.test_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            log("POST_NOTIFICATIONS not granted — enable notifications for CoverLED in Settings")
        }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dot)
            .setContentTitle("CoverLED test")
            .setContentText("If the LED works, a white dot is on the cover now.")
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(TEST_ID, n); log("test notification posted") }
            .onFailure { log("post failed: ${it.message}") }
    }

    private fun bindSettings() {
        val st = Settings(this)
        val lblOn = findViewById<TextView>(R.id.lblBlinkOn)
        val lblOff = findViewById<TextView>(R.id.lblBlinkOff)
        val lblBr = findViewById<TextView>(R.id.lblBrightness)

        findViewById<Switch>(R.id.swBlink).apply {
            isChecked = st.blinkEnabled
            setOnCheckedChangeListener { _, v -> st.blinkEnabled = v }
        }
        findViewById<Switch>(R.id.swFade).apply {
            isChecked = st.fadeEnabled
            setOnCheckedChangeListener { _, v -> st.fadeEnabled = v }
        }
        findViewById<Switch>(R.id.swBattery).apply {
            isChecked = st.showBattery
            setOnCheckedChangeListener { _, v -> st.showBattery = v }
        }
        // on: 200..3000 ms in 100 ms steps (0..28); off: 500..15000 ms in 500 ms steps (0..29)
        findViewById<SeekBar>(R.id.sbBlinkOn).apply {
            progress = (st.blinkOnMs - 200) / 100
            lblOn.text = "Beat length: ${st.blinkOnMs} ms"
            setOnSeekBarChangeListener(onChange { p -> st.blinkOnMs = 200 + p * 100; lblOn.text = "Beat length: ${st.blinkOnMs} ms" })
        }
        findViewById<SeekBar>(R.id.sbBlinkOff).apply {
            progress = (st.blinkOffMs - 500) / 500
            lblOff.text = "Dark gap: ${st.blinkOffMs} ms"
            setOnSeekBarChangeListener(onChange { p -> st.blinkOffMs = 500 + p * 500; lblOff.text = "Dark gap: ${st.blinkOffMs} ms" })
        }
        findViewById<SeekBar>(R.id.sbBrightness).apply {
            progress = (st.brightness * 100).toInt() - 1
            lblBr.text = "Brightness: ${(st.brightness * 100).toInt()} %"
            setOnSeekBarChangeListener(onChange { p -> st.brightness = (p + 1) / 100f; lblBr.text = "Brightness: ${p + 1} %" })
        }
    }

    private fun onChange(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) f(p) }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun handleAutoshow(n: Int) {
        if (n <= 0) { IndicatorController.hide(this); log("autoshow: HIDE"); return }
        show(PALETTE.copyOf(n.coerceAtMost(PALETTE.size)))
    }

    private fun show(colors: IntArray) {
        IndicatorController.show(this, colors)
            .onSuccess { log("Launched on display $it") }
            .onFailure { log("FAILED: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        txtLog.text = "$ts $msg\n${txtLog.text}"
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
