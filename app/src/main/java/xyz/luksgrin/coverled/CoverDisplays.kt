package xyz.luksgrin.coverled

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

/**
 * Resolves the Galaxy Z Flip cover display.
 *
 * On the Z Flip5 the cover screen is display id 1 (748x720). Android 15+/One UI may hide
 * it from DisplayManager.getDisplays(), so we try progressively more explicit lookups.
 */
object CoverDisplays {
    private const val TAG = "CoverLED"
    private const val LIKELY_COVER_ID = 1

    private fun dm(context: Context) =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun all(context: Context): List<Display> {
        val m = dm(context)
        val seen = LinkedHashMap<Int, Display>()
        m.displays.forEach { seen[it.displayId] = it }
        m.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).forEach { seen[it.displayId] = it }
        m.getDisplay(LIKELY_COVER_ID)?.let { seen[it.displayId] = it }
        return seen.values.toList()
    }

    fun cover(context: Context): Display? {
        val candidates = all(context).filter { it.displayId != Display.DEFAULT_DISPLAY }
        val pick = candidates.minByOrNull { area(it) }
        Log.i(TAG, "displays: ${describe(context).replace('\n', ';')} -> cover=${pick?.displayId}")
        return pick
    }

    fun describe(context: Context): String = buildString {
        all(context).forEach { d ->
            val p = Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }
            append("id=${d.displayId} \"${d.name}\" ${p.x}x${p.y} ${stateName(d.state)} flags=0x${Integer.toHexString(d.flags)}")
            if (d.displayId == Display.DEFAULT_DISPLAY) append(" [main]")
            append('\n')
        }
        if (isEmpty()) append("(no displays reported)\n")
    }

    private fun area(d: Display): Int {
        val p = Point(); @Suppress("DEPRECATION") d.getRealSize(p); return p.x * p.y
    }

    private fun stateName(s: Int) = when (s) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($s)"
    }
}
