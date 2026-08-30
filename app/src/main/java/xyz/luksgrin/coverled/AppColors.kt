package xyz.luksgrin.coverled

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/**
 * Package → indicator color (spec §3.3), plus the ignore list.
 *
 * Resolution order, like the old Galaxy LED which honored the color the app declared:
 *   1. user override            (prefs "user")
 *   2. app-declared light color (NotificationChannel.lightColor / Notification.ledARGB) → cached in "auto"
 *   3. app accent color         (Notification.color)                                    → cached in "auto"
 *   4. dominant color of the launcher icon (Palette)                                    → cached in "auto"
 *   5. DEFAULT_COLOR
 */
class AppColors(private val context: Context) {
    private val user = context.getSharedPreferences("colors_user", Context.MODE_PRIVATE)
    private val auto = context.getSharedPreferences("colors_auto", Context.MODE_PRIVATE)
    private val meta = context.getSharedPreferences("colors_meta", Context.MODE_PRIVATE)

    // ---- colors -------------------------------------------------------------------------

    fun colorFor(pkg: String): Int = when {
        user.contains(pkg) -> user.getInt(pkg, DEFAULT_COLOR)
        auto.contains(pkg) -> auto.getInt(pkg, DEFAULT_COLOR)
        else -> iconColor(pkg)?.also { auto.edit().putInt(pkg, it).apply() } ?: DEFAULT_COLOR
    }

    fun userColor(pkg: String): Int? = if (user.contains(pkg)) user.getInt(pkg, 0) else null
    fun setUserColor(pkg: String, color: Int?) =
        user.edit().apply { if (color == null) remove(pkg) else putInt(pkg, color) }.apply()

    /** Called by the listener with what the notification itself declares (0 = nothing). */
    fun learnFromNotification(pkg: String, lightColor: Int, accentColor: Int) {
        if (auto.contains(pkg)) return
        val c = firstUsable(lightColor, accentColor) ?: return
        auto.edit().putInt(pkg, c).apply()
        Log.i(TAG, "learned color for $pkg from notification: #${Integer.toHexString(c)}")
    }

    fun source(pkg: String): String = when {
        user.contains(pkg) -> "custom"
        auto.contains(pkg) -> "auto"
        else -> "default"
    }

    fun resetAuto(pkg: String) = auto.edit().remove(pkg).apply()

    private fun iconColor(pkg: String): Int? = runCatching {
        val icon = context.packageManager.getApplicationIcon(pkg)
        val bmp: Bitmap = icon.toBitmap(96, 96)
        val p = Palette.from(bmp).clearFilters().generate()
        val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch
        sw?.rgb?.let { boost(it) }
    }.onFailure { Log.w(TAG, "icon color for $pkg failed: ${it.message}") }.getOrNull()

    /** Make sure the dot reads well on black: bump saturation/value of dull picks. */
    private fun boost(rgb: Int): Int {
        val hsv = FloatArray(3); Color.colorToHSV(rgb, hsv)
        if (hsv[1] < 0.35f) hsv[1] = 0.35f
        if (hsv[2] < 0.75f) hsv[2] = 0.85f
        return Color.HSVToColor(hsv)
    }

    private fun firstUsable(vararg colors: Int): Int? =
        colors.firstOrNull { it != 0 && Color.alpha(it) > 0 && !isNearlyGray(it) }?.let { boost(it or 0xFF000000.toInt()) }

    private fun isNearlyGray(c: Int): Boolean {
        val hsv = FloatArray(3); Color.colorToHSV(c, hsv); return hsv[1] < 0.15f
    }

    // ---- ignore list & seen apps ------------------------------------------------------------

    fun isIgnored(pkg: String) = meta.getStringSet(KEY_IGNORED, emptySet())!!.contains(pkg)
    fun setIgnored(pkg: String, ignored: Boolean) {
        val set = meta.getStringSet(KEY_IGNORED, emptySet())!!.toMutableSet()
        if (ignored) set.add(pkg) else set.remove(pkg)
        meta.edit().putStringSet(KEY_IGNORED, set).apply()
    }

    /** Priority apps always get one of the dots, ahead of everything else. */
    fun isPriority(pkg: String) = meta.getStringSet(KEY_PRIORITY, emptySet())!!.contains(pkg)
    fun setPriority(pkg: String, on: Boolean) {
        val set = meta.getStringSet(KEY_PRIORITY, emptySet())!!.toMutableSet()
        if (on) set.add(pkg) else set.remove(pkg)
        meta.edit().putStringSet(KEY_PRIORITY, set).apply()
    }

    /** Packages that have ever posted a notification while we were listening (for the editor). */
    fun seen(): Set<String> = meta.getStringSet(KEY_SEEN, emptySet())!!
    fun markSeen(pkg: String) {
        val set = meta.getStringSet(KEY_SEEN, emptySet())!!
        if (pkg !in set) meta.edit().putStringSet(KEY_SEEN, set + pkg).apply()
    }

    fun label(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Priority for ordering dots: lower = more important. Unknown apps go last. */
    fun priorityOf(pkg: String): Int = PRIORITY.indexOf(pkg).let { if (it < 0) PRIORITY.size else it }

    companion object {
        private const val TAG = "CoverLED"
        private const val KEY_IGNORED = "ignored"
        private const val KEY_SEEN = "seen"
        private const val KEY_PRIORITY = "priority"
        /** The 6th dot when more than MAX_DOTS apps are pending. */
        val OTHERS_COLOR = Color.WHITE
        val DEFAULT_COLOR = Color.rgb(255, 152, 0)   // orange: "something else"

        /** Choices offered in the editor (string resource → color). */
        val PALETTE: List<Pair<Int, Int>> = listOf(
            R.string.color_red to Color.rgb(244, 67, 54), R.string.color_pink to Color.rgb(233, 30, 99), R.string.color_purple to Color.rgb(156, 39, 176),
            R.string.color_indigo to Color.rgb(63, 81, 181), R.string.color_blue to Color.rgb(33, 150, 243), R.string.color_cyan to Color.rgb(0, 188, 212),
            R.string.color_teal to Color.rgb(0, 150, 136), R.string.color_green to Color.rgb(76, 175, 80), R.string.color_lime to Color.rgb(205, 220, 57),
            R.string.color_yellow to Color.rgb(255, 235, 59), R.string.color_orange to Color.rgb(255, 152, 0), R.string.color_white to Color.WHITE,
        )
        private val PRIORITY = listOf(
            "com.samsung.android.dialer", "com.google.android.dialer",
            "com.whatsapp", "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "org.telegram.messenger", "com.Slack", "com.google.android.gm",
            "com.google.android.calendar", "com.samsung.android.calendar",
        )
    }
}
