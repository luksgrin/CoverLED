package dev.lucas.coverled

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Per-app color editor + ignore list (spec §3.2–3.3). Lists every package that has posted a
 * notification while CoverLED was listening. Tap the swatch to pick a color or go back to Auto.
 */
class ColorsActivity : AppCompatActivity() {

    private lateinit var colors: AppColors
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = AppColors(this)
        title = "App colors & ignore list"

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; applySystemInsetsPadding(top = true) }
        root.addView(com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = getString(R.string.cat_apps)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "Apps appear here after their first notification. Tap a color to change it; " +
                "Auto uses the color the app declares for its LED, else its accent, else its icon. " +
                "Priority apps always get a dot (max ${Settings.MAX_DOTS}; a white dot means \"more\")."
            setPadding(dp(16), dp(12), dp(16), dp(8))
        })
        adapter = Adapter()
        root.addView(ListView(this).apply { this.adapter = this@ColorsActivity.adapter })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); adapter.reload() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun swatch(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setSize(dp(28), dp(28))
        setStroke(dp(1), Color.GRAY)
    }

    private fun pickColor(pkg: String) {
        val names = listOf("Auto (${colors.source(pkg).let { if (it == "custom") "reset" else it }})") +
            AppColors.PALETTE.map { it.first }
        AlertDialog.Builder(this)
            .setTitle(colors.label(pkg))
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) { colors.setUserColor(pkg, null); colors.resetAuto(pkg) }
                else colors.setUserColor(pkg, AppColors.PALETTE[which - 1].second)
                adapter.reload()
            }
            .show()
    }

    private inner class Adapter : BaseAdapter() {
        private var pkgs: List<String> = emptyList()

        fun reload() {
            pkgs = colors.seen().sortedBy { colors.label(it).lowercase() }
            notifyDataSetChanged()
        }

        override fun getCount() = pkgs.size
        override fun getItem(i: Int) = pkgs[i]
        override fun getItemId(i: Int) = pkgs[i].hashCode().toLong()

        override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
            val pkg = pkgs[i]
            val row = (convert as? LinearLayout) ?: LinearLayout(this@ColorsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(ImageView(context).apply { tag = "icon" }, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(12), 0)
                    addView(TextView(context).apply { tag = "label"; textSize = 16f })
                    addView(TextView(context).apply { tag = "sub"; textSize = 12f })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ImageView(context).apply { tag = "swatch"; setPadding(dp(8), dp(8), dp(8), dp(8)) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(CheckBox(context).apply { tag = "priority"; text = "Priority" })
                    addView(CheckBox(context).apply { tag = "ignore"; text = "Ignore" })
                })
            }
            row.findViewWithTag<ImageView>("icon").setImageDrawable(
                runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            )
            row.findViewWithTag<TextView>("label").text = colors.label(pkg)
            val ignored = colors.isIgnored(pkg)
            row.findViewWithTag<TextView>("sub").text = "$pkg · ${colors.source(pkg)}${if (ignored) " · ignored" else ""}"
            row.findViewWithTag<ImageView>("swatch").apply {
                setImageDrawable(swatch(colors.colorFor(pkg)))
                alpha = if (ignored) 0.3f else 1f
                setOnClickListener { pickColor(pkg) }
            }
            row.findViewWithTag<CheckBox>("priority").apply {
                setOnCheckedChangeListener(null)
                isChecked = colors.isPriority(pkg)
                isEnabled = !ignored
                setOnCheckedChangeListener { _, v -> colors.setPriority(pkg, v) }
            }
            row.findViewWithTag<CheckBox>("ignore").apply {
                setOnCheckedChangeListener(null)
                isChecked = ignored
                setOnCheckedChangeListener { _, v -> colors.setIgnored(pkg, v); reload() }
            }
            return row
        }
    }
}
