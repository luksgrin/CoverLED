package dev.lucas.coverled

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Edge-to-edge helper: keep [view]'s content clear of status bar / camera cutout / nav bar. */
fun View.applySystemInsetsPadding(top: Boolean = false, bottom: Boolean = true) {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.updatePadding(
            left = base[0] + sys.left,
            top = base[1] + if (top) sys.top else 0,
            right = base[2] + sys.right,
            bottom = base[3] + if (bottom) sys.bottom else 0,
        )
        insets
    }
}
