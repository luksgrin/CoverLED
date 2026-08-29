package dev.lucas.coverled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Glue between notification state, fold state and the cover indicator (spec §6, §7.1).
 *
 * Behaves like a physical LED: it glows when the cover would otherwise be dark, and gets out of
 * the way as soon as the user wants the screen.
 *
 *   pending ∧ closed ∧ ¬snoozed          → show(colors)
 *   otherwise                            → hide
 *   user taps the LED                    → snooze (hide, reveal Samsung's cover UI)
 *   screen turns ON and we didn't cause it → snooze (user pressed the side key / lifted the phone)
 *   screen turns OFF                     → un-snooze, re-show after a grace period
 *   a *new* notification arrives         → un-snooze, show
 */
class IndicatorCoordinator(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val state = NotificationState.get(context)
    private val fold = FoldState(context)
    private val colors = AppColors(context)

    private var shouldShow = false          // pending ∧ closed
    private var snoozed = false
    private var lastLaunchAt = 0L
    private var lastSnapshot: Map<String, Int> = emptyMap()
    private var lastColors: IntArray? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    snoozed = false
                    if (shouldShow) {
                        Log.i(TAG, "screen off while pending; re-show in ${RESHOW_DELAY_MS}ms")
                        handler.removeCallbacks(reshow)
                        handler.postDelayed(reshow, RESHOW_DELAY_MS)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    val sinceLaunch = SystemClock.elapsedRealtime() - lastLaunchAt
                    if (shouldShow && !snoozed && sinceLaunch > OWN_WAKE_WINDOW_MS) {
                        Log.i(TAG, "screen on not caused by us (${sinceLaunch}ms after launch) -> snooze")
                        snooze()
                    }
                }
                CoverIndicatorActivity.ACTION_USER_DISMISS -> {
                    Log.i(TAG, "user tapped the LED -> snooze")
                    snooze()
                }
            }
        }
    }
    private val reshow = Runnable { if (shouldShow && !snoozed) launch() }

    fun start() {
        fold.start()
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(CoverIndicatorActivity.ACTION_USER_DISMISS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            combine(state.snapshot, fold.closed) { snap, closed -> snap to closed }
                .collect { (snap, closed) -> apply(snap, closed) }
        }
    }

    fun stop() {
        scope.cancel()
        handler.removeCallbacks(reshow)
        runCatching { context.unregisterReceiver(receiver) }
        fold.stop()
        IndicatorController.hide(context)
    }

    private fun snooze() {
        snoozed = true
        handler.removeCallbacks(reshow)
        IndicatorController.hide(context)
    }

    private fun apply(snap: Map<String, Int>, closed: Boolean) {
        val want = snap.isNotEmpty() && closed
        val somethingNew = snap.any { (pkg, n) -> n > (lastSnapshot[pkg] ?: 0) }
        lastSnapshot = snap
        if (somethingNew && snoozed) { Log.i(TAG, "new notification -> un-snooze"); snoozed = false }
        Log.i(TAG, "apply: pending=${snap.size} closed=$closed snoozed=$snoozed -> ${if (want && !snoozed) "SHOW" else "HIDE"}")
        shouldShow = want
        if (!want) {
            handler.removeCallbacks(reshow)
            lastColors = null
            IndicatorController.hide(context)
            return
        }
        lastColors = dotsFor(snap.keys)
        if (!snoozed) launch()
    }

    /**
     * Which dots to show: priority apps first, then by order of first appearance. If more than
     * MAX_DOTS apps are pending, the last dot is white and stands for "others".
     */
    private fun dotsFor(pkgs: Set<String>): IntArray {
        val seen = state.firstSeenOrder()
        val ordered = pkgs.sortedWith(
            compareByDescending<String> { colors.isPriority(it) }
                .thenBy { seen[it] ?: Long.MAX_VALUE }
                .thenBy { colors.priorityOf(it) }
        )
        val max = Settings.MAX_DOTS
        return if (ordered.size <= max) ordered.map { colors.colorFor(it) }.toIntArray()
        else (ordered.take(max - 1).map { colors.colorFor(it) } + AppColors.OTHERS_COLOR).toIntArray()
    }

    private fun launch() {
        val c = lastColors ?: return
        lastLaunchAt = SystemClock.elapsedRealtime()
        IndicatorController.show(context, c)
    }

    companion object {
        private const val TAG = "CoverLED"
        private const val RESHOW_DELAY_MS = 2_000L
        /** SCREEN_ON arriving within this window after our own launch is our turnScreenOn, not the user. */
        private const val OWN_WAKE_WINDOW_MS = 2_500L
    }
}
