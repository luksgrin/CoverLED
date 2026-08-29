package dev.lucas.coverled

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * Tiny One UI-flavoured UI kit: page with big title, rounded list cards, rows with title/subtitle
 * and a control on the right, section headers, radio rows, slider rows, pill buttons.
 * Programmatic on purpose: every screen shares exactly the same metrics.
 */
class OneUi(private val ctx: Context) {
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun dp(v: Float) = v * ctx.resources.displayMetrics.density
    private fun color(id: Int) = ContextCompat.getColor(ctx, id)

    // ---------------------------------------------------------------- page
    /** Scrollable page: optional back arrow row, big title, then content. Applies system insets. */
    fun page(title: CharSequence, showBack: Boolean, onBack: () -> Unit = {}): Pair<View, LinearLayout> {
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(24)) }
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (showBack) {
            column.addView(ImageView(ctx).apply {
                setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.ou_text))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ripple()
                contentDescription = "Back"
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8); topMargin = dp(8) })
        }
        column.addView(TextView(ctx).apply {
            text = title; textSize = 30f; setTextColor(color(R.color.ou_text)); typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(dp(24), if (showBack) dp(12) else dp(40), dp(24), dp(28))
        })
        column.addView(content)
        val scroll = ScrollView(ctx).apply { addView(column); isFillViewport = true; clipToPadding = false; setBackgroundColor(color(R.color.ou_bg)) }
        scroll.applySystemInsetsPadding(top = true)
        return scroll to content
    }

    // ---------------------------------------------------------------- containers
    fun header(text: CharSequence): TextView = TextView(ctx).apply {
        this.text = text; textSize = 14f; setTextColor(color(R.color.ou_accent)); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(24), dp(20), dp(24), dp(8))
    }

    /** A rounded white card holding rows; dividers are inserted between children automatically. */
    fun card(vararg rows: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(26f); setColor(color(R.color.ou_card)) }
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(View(ctx).apply { setBackgroundColor(color(R.color.ou_divider)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { leftMargin = dp(24); rightMargin = dp(24) })
            addView(r)
        }
    }

    // ---------------------------------------------------------------- rows
    /** Title + optional subtitle on the left, optional control on the right. */
    fun row(title: CharSequence, subtitle: CharSequence? = null, end: View? = null, onClick: (() -> Unit)? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64); setPadding(dp(24), dp(14), dp(if (end == null) 24 else 16), dp(14))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply { text = title; textSize = 17f; setTextColor(color(R.color.ou_text)) })
                if (!subtitle.isNullOrEmpty()) addView(TextView(ctx).apply {
                    text = subtitle; textSize = 14f; setTextColor(color(R.color.ou_text_secondary)); setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (end != null) addView(end, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
            if (onClick != null) { background = ripple(); isClickable = true; setOnClickListener { onClick() } }
        }

    fun chevron(): ImageView = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_chevron); imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.ou_text_secondary))
    }

    fun switchRow(title: CharSequence, subtitle: CharSequence?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = MaterialSwitch(ctx).apply { isChecked = checked; setOnCheckedChangeListener { _, v -> onChange(v) } }
        return row(title, subtitle, sw) { sw.toggle() }
    }

    /** One radio row per option; selecting one deselects the others. */
    fun radioRows(options: List<Triple<String, CharSequence, CharSequence?>>, selected: String, onSelect: (String) -> Unit): List<View> {
        val radios = HashMap<String, RadioButton>()
        fun select(key: String) { radios.forEach { (k, r) -> r.isChecked = k == key }; onSelect(key) }
        return options.map { (key, title, sub) ->
            val rb = RadioButton(ctx).apply { isChecked = key == selected; setOnClickListener { select(key) } }
            radios[key] = rb
            row(title, sub, rb) { select(key) }
        }
    }

    /** Title with the current value on the right, slider underneath. */
    fun sliderRow(title: CharSequence, from: Float, to: Float, step: Float, value: Float, format: (Float) -> CharSequence, onChange: (Float) -> Unit): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(14), dp(24), dp(6))
            val valueView = TextView(ctx).apply { textSize = 14f; setTextColor(color(R.color.ou_accent)); text = format(value) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(ctx).apply { text = title; textSize = 17f; setTextColor(color(R.color.ou_text)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(valueView)
            })
            addView(Slider(ctx).apply {
                valueFrom = from; valueTo = to; stepSize = step; this.value = value.coerceIn(from, to)
                addOnChangeListener { _, v, fromUser -> valueView.text = format(v); if (fromUser) onChange(v) }
            })
        }

    fun note(text: CharSequence): TextView = TextView(ctx).apply {
        this.text = text; textSize = 14f; setTextColor(color(R.color.ou_text_secondary)); setPadding(dp(24), dp(4), dp(24), dp(12))
    }

    // ---------------------------------------------------------------- buttons
    /** Full-width pill button — never wraps into an awkward grid. */
    fun button(text: CharSequence, primary: Boolean = false, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx).apply {
            this.text = text; isAllCaps = false; textSize = 16f; cornerRadius = dp(24); minHeight = dp(48)
            insetTop = 0; insetBottom = 0
            if (primary) { setBackgroundColor(color(R.color.ou_accent)); setTextColor(0xFFFFFFFF.toInt()) }
            else { setBackgroundColor(color(R.color.ou_button)); setTextColor(color(R.color.ou_text)) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    /** Several short buttons side by side, each on one line (text auto-shrinks rather than wraps). */
    fun buttonBar(vararg items: Pair<CharSequence, () -> Unit>): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEachIndexed { i, (t, f) ->
            addView(button(t, false, f).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 11, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = dp(8); topMargin = dp(8) })
        }
    }

    fun ripple(): android.graphics.drawable.RippleDrawable =
        android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000), null, null)

    companion object {
        fun setContent(activity: Activity, view: View) {
            activity.setContentView(FrameLayout(activity).apply { addView(view) })
        }
    }
}
