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

    var blinkOnMs: Int
        get() = prefs.getInt(KEY_BLINK_ON, 800)
        set(v) = prefs.edit().putInt(KEY_BLINK_ON, v.coerceIn(200, 3000)).apply()

    var blinkOffMs: Int
        get() = prefs.getInt(KEY_BLINK_OFF, 3000)
        set(v) = prefs.edit().putInt(KEY_BLINK_OFF, v.coerceIn(500, 15000)).apply()

    /** Window brightness 0.01–1.0 (the panel is fully on; this is the backlight/OLED level). */
    var brightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, 0.05f)
        set(v) = prefs.edit().putFloat(KEY_BRIGHTNESS, v.coerceIn(0.01f, 1f)).apply()

    /** Show the charging line under the dots. */
    var showBattery: Boolean
        get() = prefs.getBoolean(KEY_BATTERY, true)
        set(v) = prefs.edit().putBoolean(KEY_BATTERY, v).apply()

    companion object {
        const val KEY_BLINK = "blink"
        const val KEY_BLINK_ON = "blink_on_ms"
        const val KEY_BLINK_OFF = "blink_off_ms"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_BATTERY = "show_battery"
    }
}
