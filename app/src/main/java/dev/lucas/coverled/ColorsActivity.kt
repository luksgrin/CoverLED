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
    private lateinit var listCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = AppColors(this)
        title = "App colors & ignore list"

        val ui = OneUi(this)
        val (page, content) = ui.page(getString(R.string.cat_apps), showBack = true) { finish() }
        content.addView(ui.note(getString(R.string.colors_intro, Settings.MAX_DOTS)))
        adapter = Adapter()
        listCard = ui.card()
        content.addView(listCard)
        OneUi.setContent(this, page)
    }

    override fun onResume() { super.onResume(); adapter.reload() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun swatch(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setSize(dp(28), dp(28))
        setStroke(dp(1), Color.GRAY)
    }

    private fun pickColor(pkg: String) {
        val src = colors.source(pkg)
        val names = listOf(getString(R.string.colors_auto, if (src == "custom") getString(R.string.src_reset) else sourceLabel(src))) +
            AppColors.PALETTE.map { getString(it.first) }
        AlertDialog.Builder(this)
            .setTitle(colors.label(pkg))
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) { colors.setUserColor(pkg, null); colors.resetAuto(pkg) }
                else colors.setUserColor(pkg, AppColors.PALETTE[which - 1].second)
                adapter.reload()
            }
            .show()
    }

    private fun sourceLabel(src: String) = when (src) {
        "custom" -> getString(R.string.src_custom); "auto" -> getString(R.string.src_auto); else -> getString(R.string.src_default)
    }

    private inner class Adapter : BaseAdapter() {
        private var pkgs: List<String> = emptyList()

        fun reload() {
            pkgs = colors.seen().sortedBy { colors.label(it).lowercase() }
            notifyDataSetChanged()
            listCard.removeAllViews()
            val ui = OneUi(this@ColorsActivity)
            pkgs.forEachIndexed { i, _ ->
                if (i > 0) listCard.addView(View(this@ColorsActivity).apply { setBackgroundColor(getColor(R.color.ou_divider)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply { leftMargin = ui.dp(24); rightMargin = ui.dp(24) })
                listCard.addView(getView(i, null, listCard))
            }
        }

        override fun getCount() = pkgs.size
        override fun getItem(i: Int) = pkgs[i]
        override fun getItemId(i: Int) = pkgs[i].hashCode().toLong()

        override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
            val pkg = pkgs[i]
            val row = (convert as? LinearLayout) ?: LinearLayout(this@ColorsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(12), dp(16), dp(12))
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
                    addView(CheckBox(context).apply { tag = "priority"; text = getString(R.string.priority) })
                    addView(CheckBox(context).apply { tag = "ignore"; text = getString(R.string.ignore) })
                })
            }
            row.findViewWithTag<ImageView>("icon").setImageDrawable(
                runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            )
            row.findViewWithTag<TextView>("label").text = colors.label(pkg)
            val ignored = colors.isIgnored(pkg)
            row.findViewWithTag<TextView>("sub").text = "$pkg · ${sourceLabel(colors.source(pkg))}${if (ignored) " · ${getString(R.string.ignored)}" else ""}"
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
