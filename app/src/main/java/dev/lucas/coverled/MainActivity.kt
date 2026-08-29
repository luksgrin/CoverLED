package dev.lucas.coverled

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Home: setup status + categories. Also the adb entry point for the debug hooks. */
class MainActivity : AppCompatActivity() {

    companion object {
        /** adb: --ei autoshow N shows N dots (0 hides), --ei testnotif 1/0 posts/cancels, --ez clearall true. */
        const val EXTRA_AUTOSHOW = "autoshow"
        const val EXTRA_TESTNOTIF = "testnotif"
        const val EXTRA_CLEARALL = "clearall"
        val PALETTE = intArrayOf(
            Color.rgb(33, 150, 243), Color.rgb(76, 175, 80), Color.rgb(156, 39, 176),
            Color.rgb(244, 67, 54), Color.rgb(255, 235, 59), Color.WHITE
        )
    }

    private lateinit var ui: OneUi
    private lateinit var content: LinearLayout
    private lateinit var setupCard: View
    private lateinit var pendingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDebugIntent(intent)
        ui = OneUi(this)
        val (page, c) = ui.page(getString(R.string.app_name), showBack = false)
        content = c
        content.addView(ui.note(getString(R.string.home_tagline)))

        setupCard = ui.card(); content.addView(setupCard)   // filled in onResume

        pendingText = ui.note("").apply { setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16)) }
        content.addView(ui.header(getString(R.string.pending_title)))
        content.addView(ui.card(pendingText))

        content.addView(ui.card(
            ui.row(getString(R.string.cat_beat), getString(R.string.cat_beat_sub), ui.chevron()) { open(SettingsActivity.SECTION_BEAT) },
            ui.row(getString(R.string.cat_layout), getString(R.string.cat_layout_sub), ui.chevron()) { open(SettingsActivity.SECTION_LAYOUT) },
            ui.row(getString(R.string.cat_shape), getString(R.string.cat_shape_sub), ui.chevron()) { open(SettingsActivity.SECTION_SHAPE) },
            ui.row(getString(R.string.cat_position), getString(R.string.cat_position_sub), ui.chevron()) { startActivity(Intent(this, PositionActivity::class.java)) },
        ))
        content.addView(ui.card(
            ui.row(getString(R.string.cat_apps), getString(R.string.cat_apps_sub), ui.chevron()) { startActivity(Intent(this, ColorsActivity::class.java)) },
        ))
        content.addView(ui.card(
            ui.row(getString(R.string.cat_dev), getString(R.string.cat_dev_sub), ui.chevron()) { open(SettingsActivity.SECTION_DEV) },
        ))
        OneUi.setContent(this, page)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationState.get(this@MainActivity).snapshot.collect { snap ->
                    val colors = AppColors(this@MainActivity)
                    pendingText.text = if (snap.isEmpty()) getString(R.string.pending_none)
                    else getString(R.string.pending_list, snap.keys.joinToString { colors.label(it) })
                }
            }
        }
    }

    private fun open(section: String) = startActivity(SettingsActivity.intent(this, section))

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleDebugIntent(intent) }

    private fun handleDebugIntent(intent: Intent) {
        if (intent.hasExtra(EXTRA_AUTOSHOW)) {
            val n = intent.getIntExtra(EXTRA_AUTOSHOW, 1)
            if (n <= 0) IndicatorController.hide(this) else IndicatorController.show(this, PALETTE.copyOf(n.coerceAtMost(PALETTE.size)))
        }
        if (intent.hasExtra(EXTRA_TESTNOTIF)) TestNotification.post(this, intent.getIntExtra(EXTRA_TESTNOTIF, 1) > 0)
        if (intent.getBooleanExtra(EXTRA_CLEARALL, false)) LedNotificationListener.debugClearAll()
    }

    override fun onResume() {
        super.onResume()
        val notif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val overlay = Settings.canDrawOverlays(this)
        val rows = ArrayList<View>()
        rows.add(ui.row(getString(if (notif) R.string.setup_notif_ok else R.string.setup_notif_missing), null,
            if (notif) null else ui.button(getString(R.string.fix), true) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }))
        rows.add(ui.row(getString(if (overlay) R.string.setup_overlay_ok else R.string.setup_overlay_missing), null,
            if (overlay) null else ui.button(getString(R.string.fix), true) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }))
        val idx = content.indexOfChild(setupCard); content.removeView(setupCard)
        setupCard = ui.card(*rows.toTypedArray()); content.addView(setupCard, idx)
    }
}
