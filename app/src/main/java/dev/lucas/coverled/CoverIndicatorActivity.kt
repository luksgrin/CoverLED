package dev.lucas.coverled

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
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
    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private var chargingIntent: Intent? = null

    // Duty cycle: one "beat" (blinkOnMs) then dark for blinkOffMs. Battery text is not blinked.
    // Beat = hard on/off, or a fade in + fade out (heartbeat) when fadeEnabled.
    private var beat: Animator? = null
    private val blink = object : Runnable {
        override fun run() {
            val on = settings.blinkOnMs.toLong()
            beat?.cancel()
            when (settings.beatStyle) {
                Settings.STYLE_LUBDUB -> {
                    // lub (strong) – short pause – dub (softer), all within the beat length
                    val lubUp = alpha(0f, 1f, on * 15 / 100, DecelerateInterpolator())
                    val lubDown = alpha(1f, 0.15f, on * 20 / 100, AccelerateInterpolator())
                    val pause = alpha(0.15f, 0.15f, on * 10 / 100, null)
                    val dubUp = alpha(0.15f, 0.8f, on * 15 / 100, DecelerateInterpolator())
                    val dubDown = alpha(0.8f, 0f, on * 40 / 100, AccelerateInterpolator())
                    beat = AnimatorSet().apply { playSequentially(lubUp, lubDown, pause, dubUp, dubDown); start() }
                }
                Settings.STYLE_BREATHE -> {
                    val up = alpha(0f, 1f, on * 45 / 100, DecelerateInterpolator())
                    val down = alpha(1f, 0f, on * 55 / 100, AccelerateInterpolator())
                    beat = AnimatorSet().apply { playSequentially(up, down); start() }
                }
                else -> {
                    dots.alpha = 1f
                    handler.postDelayed({ dots.alpha = 0f }, on)
                }
            }
            handler.postDelayed(this, on + settings.blinkOffMs)
        }
    }

    private fun alpha(from: Float, to: Float, ms: Long, interp: android.view.animation.Interpolator?) =
        ObjectAnimator.ofFloat(dots, View.ALPHA, from, to).apply { duration = ms; interpolator = interp ?: LinearInterpolator() }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }

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
        settings = Settings(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
        settings.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        applySettings()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(batteryReceiver) }
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacksAndMessages(null)
        beat?.cancel()
        super.onStop()
        Log.i(TAG, "Indicator onStop")
    }

    private fun applySettings() {
        window.attributes = window.attributes.apply { screenBrightness = settings.brightness }
        dots.posX = settings.dotX
        dots.posY = settings.dotY
        dots.dotDp = settings.dotSizeDp.toFloat()
        dots.geometric = settings.arrangement == Settings.ARR_GEOMETRIC
        dots.shape = if (settings.customShape) ShapeLoader.load(this) else null
        handler.removeCallbacksAndMessages(null)
        beat?.cancel()
        dots.visibility = View.VISIBLE
        if (settings.blinkEnabled) {
            dots.alpha = 0f
            handler.post(blink)
        } else {
            dots.alpha = 1f
        }
        chargingIntent?.let { renderBattery(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        dots.colors = intent?.getIntArrayExtra(EXTRA_COLORS) ?: intArrayOf(Color.WHITE)
    }

    private fun renderBattery(i: Intent) {
        chargingIntent = i
        if (!settings.showBattery) { battery.visibility = TextView.GONE; return }
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
