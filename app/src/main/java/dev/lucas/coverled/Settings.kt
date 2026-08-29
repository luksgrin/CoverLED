package dev.lucas.coverled

import android.content.Context
import android.content.SharedPreferences

/** User-tunable behaviour (spec §11 power). Small enough for SharedPreferences. */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Blink the dots instead of keeping them lit (OLED: dark pixels ≈ no power). */
    var blinkEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLINK, true)
        set(v) = prefs.edit().putBoolean(KEY_BLINK, v).apply()

    /** How one beat looks: hard on/off, a breath (fade in/out), or a heartbeat (lub-dub). */
    var beatStyle: String
        get() = prefs.getString(KEY_STYLE, STYLE_BREATHE) ?: STYLE_BREATHE
        set(v) = prefs.edit().putString(KEY_STYLE, v).apply()

    /** Row, Geometric (triangle / square / …) or Cycle (one dot changing color per beat). */
    var arrangement: String
        get() = prefs.getString(KEY_ARRANGEMENT, ARR_GEOMETRIC) ?: ARR_GEOMETRIC
        set(v) = prefs.edit().putString(KEY_ARRANGEMENT, v).apply()

    /** Dot (or custom shape) size in dp. */
    var dotSizeDp: Int
        get() = prefs.getInt(KEY_DOT_SIZE, 14)
        set(v) = prefs.edit().putInt(KEY_DOT_SIZE, v.coerceIn(8, 64)).apply()

    /** Whether a user-provided PNG shape is used instead of a circle (file lives at [shapeFile]). */
    var customShape: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_SHAPE, false)
        set(v) = prefs.edit().putBoolean(KEY_CUSTOM_SHAPE, v).apply()

    /** Dot position on the cover as fractions of width/height (0..1). Default: center. */
    var dotX: Float
        get() = prefs.getFloat(KEY_DOT_X, 0.5f)
        set(v) = prefs.edit().putFloat(KEY_DOT_X, v.coerceIn(0f, 1f)).apply()
    var dotY: Float
        get() = prefs.getFloat(KEY_DOT_Y, 0.5f)
        set(v) = prefs.edit().putFloat(KEY_DOT_Y, v.coerceIn(0f, 1f)).apply()

    var blinkOnMs: Int
        get() = prefs.getInt(KEY_BLINK_ON, 1400)
        set(v) = prefs.edit().putInt(KEY_BLINK_ON, v.coerceIn(200, 3000)).apply()

    var blinkOffMs: Int
        get() = prefs.getInt(KEY_BLINK_OFF, 2500)
        set(v) = prefs.edit().putInt(KEY_BLINK_OFF, v.coerceIn(500, 15000)).apply()

    /** Window brightness 0.01–1.0 (the panel is fully on; this is the backlight/OLED level). */
    var brightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, 0.05f)
        set(v) = prefs.edit().putFloat(KEY_BRIGHTNESS, v.coerceIn(0.01f, 1f)).apply()

    /** Show the charging line under the dots. */
    var showBattery: Boolean
        get() = prefs.getBoolean(KEY_BATTERY, true)
        set(v) = prefs.edit().putBoolean(KEY_BATTERY, v).apply()

    /** Charging-line position as fractions of width/height (0..1). Default: bottom center. */
    var batteryX: Float
        get() = prefs.getFloat(KEY_BAT_X, 0.5f)
        set(v) = prefs.edit().putFloat(KEY_BAT_X, v.coerceIn(0f, 1f)).apply()
    var batteryY: Float
        get() = prefs.getFloat(KEY_BAT_Y, 0.9f)
        set(v) = prefs.edit().putFloat(KEY_BAT_Y, v.coerceIn(0f, 1f)).apply()

    companion object {
        const val KEY_BAT_X = "battery_x"
        const val KEY_BAT_Y = "battery_y"
        const val KEY_BLINK = "blink"
        const val KEY_STYLE = "beat_style"
        const val KEY_DOT_X = "dot_x"
        const val KEY_DOT_Y = "dot_y"
        const val KEY_ARRANGEMENT = "arrangement"
        const val KEY_DOT_SIZE = "dot_size_dp"
        const val KEY_CUSTOM_SHAPE = "custom_shape"
        const val ARR_ROW = "row"
        const val ARR_GEOMETRIC = "geometric"
        const val ARR_CYCLE = "cycle"
        const val MAX_DOTS = 6
        const val SHAPE_FILE = "shape.png"
        /** Custom shape constraints (see README): PNG, transparent background, white/grayscale drawing. */
        const val SHAPE_MAX_INPUT_PX = 1024
        const val SHAPE_MAX_INPUT_BYTES = 2L * 1024 * 1024
        const val SHAPE_STORED_PX = 128
        fun shapeFile(context: android.content.Context) = java.io.File(context.filesDir, SHAPE_FILE)
        const val STYLE_HARD = "hard"
        const val STYLE_BREATHE = "breathe"
        const val STYLE_LUBDUB = "lubdub"
        const val KEY_BLINK_ON = "blink_on_ms"
        const val KEY_BLINK_OFF = "blink_off_ms"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_BATTERY = "show_battery"
    }
}
