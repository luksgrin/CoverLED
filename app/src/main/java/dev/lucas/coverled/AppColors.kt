package dev.lucas.coverled

import android.content.Context
import android.graphics.Color

/**
 * Package → indicator color mapping (spec §3.3). Persisted in SharedPreferences so it can be
 * edited later from the UI; seeded with sensible defaults.
 */
class AppColors(context: Context) {
    private val prefs = context.getSharedPreferences("app_colors", Context.MODE_PRIVATE)

    fun colorFor(pkg: String): Int =
        prefs.getInt(pkg, DEFAULTS[pkg] ?: DEFAULT_COLOR)

    fun set(pkg: String, color: Int) = prefs.edit().putInt(pkg, color).apply()

    /** Priority for the single-dot mode: lower = more important. Unknown apps go last. */
    fun priorityOf(pkg: String): Int = PRIORITY.indexOf(pkg).let { if (it < 0) PRIORITY.size else it }

    companion object {
        val DEFAULT_COLOR = Color.rgb(255, 152, 0)   // orange: "something else"

        val DEFAULTS: Map<String, Int> = mapOf(
            "com.whatsapp" to Color.rgb(33, 150, 243),                  // blue
            "com.google.android.gm" to Color.rgb(76, 175, 80),          // green
            "com.google.android.calendar" to Color.rgb(156, 39, 176),   // purple
            "com.samsung.android.calendar" to Color.rgb(156, 39, 176),
            "com.samsung.android.dialer" to Color.rgb(244, 67, 54),     // red: missed call
            "com.google.android.dialer" to Color.rgb(244, 67, 54),
            "com.samsung.android.messaging" to Color.rgb(0, 188, 212),  // cyan: SMS
            "com.google.android.apps.messaging" to Color.rgb(0, 188, 212),
            "com.Slack" to Color.rgb(233, 30, 99),                      // pink
            "org.telegram.messenger" to Color.rgb(3, 169, 244),
            "dev.lucas.coverled" to Color.WHITE,                        // our own test notification
        )
        private val PRIORITY = listOf(
            "com.samsung.android.dialer", "com.google.android.dialer",
            "com.whatsapp", "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "org.telegram.messenger", "com.Slack", "com.google.android.gm",
            "com.google.android.calendar", "com.samsung.android.calendar",
        )
    }
}
