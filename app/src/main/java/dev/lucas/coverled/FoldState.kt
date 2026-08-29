package dev.lucas.coverled

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fold-state monitor (spec §5.3) usable from a Service.
 *
 * DeviceStateManager is still a system API on SDK 36 and Jetpack WindowManager needs a UI context,
 * so we use the public hinge-angle sensor (API 30+): ~0° = closed. If the device has no such
 * sensor we assume "closed" — the indicator only renders on the cover display anyway.
 */
class FoldState(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val hinge: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    private val _closed = MutableStateFlow(true)
    val closed: StateFlow<Boolean> = _closed

    fun start() {
        if (hinge == null) { Log.w(TAG, "no hinge-angle sensor; assuming closed"); return }
        sm?.registerListener(this, hinge, SensorManager.SENSOR_DELAY_UI)
        Log.i(TAG, "hinge sensor registered (${hinge.name}, wakeUp=${hinge.isWakeUpSensor})")
    }

    fun stop() { sm?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        val angle = e.values.firstOrNull() ?: return
        val closed = angle < CLOSED_MAX_DEG
        if (closed != _closed.value) Log.i(TAG, "hinge=${angle.toInt()}° -> closed=$closed")
        _closed.value = closed
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "CoverLED"
        private const val CLOSED_MAX_DEG = 15f
    }
}
