package dev.lucas.coverled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The "LED". A black full-screen activity that shows on the cover display while the
 * device is locked. Draws the dots and, while charging, a small battery line so the user
 * keeps the information Samsung's charging AOD would otherwise have shown (can't overlay it).
 */
class CoverIndicatorActivity : AppCompatActivity() {

    companion object {
        const val TAG = "CoverLED"
        const val EXTRA_COLORS = "colors"
        const val ACTION_HIDE = "dev.lucas.coverled.HIDE"
        /** Sent when the user taps the LED: "let me see the screen". */
        const val ACTION_USER_DISMISS = "dev.lucas.coverled.USER_DISMISS"
    }

    private lateinit var dots: DotView
    private lateinit var battery: TextView

    private val hideReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "HIDE received, finishing")
            finishAndRemoveTask()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = renderBattery(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Indicator onCreate on display ${display?.displayId}")

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.05f }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // A tap = "I want the screen": hide and reveal Samsung's cover UI / notifications.
            setOnClickListener {
                Log.i(TAG, "tap -> user dismiss")
                sendBroadcast(Intent(ACTION_USER_DISMISS).setPackage(packageName))
                finishAndRemoveTask()
            }
        }
        dots = DotView(this)
        battery = TextView(this).apply {
            setTextColor(Color.rgb(160, 160, 160))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            visibility = TextView.GONE
        }
        root.addView(dots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(battery, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (28 * resources.displayMetrics.density).toInt()
        })
        setContentView(root)
        applyIntent(intent)

        ContextCompat.registerReceiver(this, hideReceiver, IntentFilter(ACTION_HIDE), ContextCompat.RECEIVER_NOT_EXPORTED)

        // Back gesture behaves like a tap: reveal the screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendBroadcast(Intent(ACTION_USER_DISMISS).setPackage(packageName))
                finishAndRemoveTask()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        // Sticky broadcast: registering returns the current battery status immediately.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { renderBattery(it) }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(batteryReceiver) }
        super.onStop()
        Log.i(TAG, "Indicator onStop")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        dots.colors = intent?.getIntArrayExtra(EXTRA_COLORS) ?: intArrayOf(Color.WHITE)
    }

    private fun renderBattery(i: Intent) {
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (!charging) { battery.visibility = TextView.GONE; return }
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0) level * 100 / scale else -1
        val bm = getSystemService(BatteryManager::class.java)
        val remainMs = bm?.computeChargeTimeRemaining() ?: -1L
        val remain = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.battery_full)
            remainMs > 0 -> {
                val m = remainMs / 60_000
                if (m >= 60) getString(R.string.battery_hm, m / 60, m % 60) else getString(R.string.battery_m, m)
            }
            else -> ""
        }
        battery.text = if (remain.isEmpty()) "⚡ $pct %" else "⚡ $pct %  ·  $remain"
        battery.visibility = TextView.VISIBLE
    }

    override fun onDestroy() {
        unregisterReceiver(hideReceiver)
        Log.i(TAG, "Indicator onDestroy")
        super.onDestroy()
    }
}
