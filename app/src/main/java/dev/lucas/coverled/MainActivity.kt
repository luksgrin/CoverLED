package dev.lucas.coverled

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.content).applySystemInsetsPadding()
        handleDebugIntent(intent)

        findViewById<Button>(R.id.btnAccess).setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        category(R.id.catBeat, "💓", R.string.cat_beat, R.string.cat_beat_sub) { startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_BEAT)) }
        category(R.id.catLayout, "⬡", R.string.cat_layout, R.string.cat_layout_sub) { startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_LAYOUT)) }
        category(R.id.catShape, "★", R.string.cat_shape, R.string.cat_shape_sub) { startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_SHAPE)) }
        category(R.id.catApps, "🎨", R.string.cat_apps, R.string.cat_apps_sub) { startActivity(Intent(this, ColorsActivity::class.java)) }
        category(R.id.catPosition, "⌖", R.string.cat_position, R.string.cat_position_sub) { startActivity(Intent(this, PositionActivity::class.java)) }
        category(R.id.catDev, "🛠", R.string.cat_dev, R.string.cat_dev_sub) { startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_DEV)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationState.get(this@MainActivity).snapshot.collect { snap ->
                    val colors = AppColors(this@MainActivity)
                    findViewById<TextView>(R.id.txtState).text =
                        if (snap.isEmpty()) getString(R.string.pending_none)
                        else getString(R.string.pending_list, snap.keys.joinToString { colors.label(it) })
                }
            }
        }
    }

    private fun category(id: Int, emoji: String, title: Int, subtitle: Int, onClick: () -> Unit) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.emoji).text = emoji
        v.findViewById<TextView>(R.id.title).setText(title)
        v.findViewById<TextView>(R.id.subtitle).setText(subtitle)
        v.setOnClickListener { onClick() }
    }

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
        findViewById<TextView>(R.id.txtNotif).text = (if (notif) "✅ " else "⚠️ ") + getString(if (notif) R.string.setup_notif_ok else R.string.setup_notif_missing)
        findViewById<TextView>(R.id.txtOverlay).text = (if (overlay) "✅ " else "⚠️ ") + getString(if (overlay) R.string.setup_overlay_ok else R.string.setup_overlay_missing)
        findViewById<View>(R.id.btnAccess).visibility = if (notif) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnOverlay).visibility = if (overlay) View.GONE else View.VISIBLE
    }
}
