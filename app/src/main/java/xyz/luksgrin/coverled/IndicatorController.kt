package xyz.luksgrin.coverled

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Single entry point for showing/hiding the LED on the cover display.
 * Phase 2: driven by debug buttons. Phase 5: driven by the notification state manager.
 */
object IndicatorController {
    private const val TAG = "CoverLED"

    /** @param colors ARGB colors, one dot per entry. */
    fun show(context: Context, colors: IntArray): Result<Int> {
        val cover = CoverDisplays.cover(context)
        if (cover == null) {
            Log.e(TAG, "No cover display found")
            return Result.failure(IllegalStateException("No cover display found"))
        }

        val intent = Intent(context, CoverIndicatorActivity::class.java)
            .putExtra(CoverIndicatorActivity.EXTRA_COLORS, colors)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )

        val opts = ActivityOptions.makeBasic().apply { launchDisplayId = cover.displayId }

        return runCatching {
            context.startActivity(intent, opts.toBundle())
            Log.i(TAG, "Launched indicator on display ${cover.displayId}")
            cover.displayId
        }.onFailure { Log.e(TAG, "Failed to launch indicator", it) }
    }

    fun hide(context: Context) {
        context.sendBroadcast(
            Intent(CoverIndicatorActivity.ACTION_HIDE).setPackage(context.packageName)
        )
    }
}
