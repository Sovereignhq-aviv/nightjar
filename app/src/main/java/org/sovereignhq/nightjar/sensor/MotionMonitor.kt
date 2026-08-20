package org.sovereignhq.nightjar.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Counts how often the phone gets nudged. Useful when the phone sits on the mattress, where it
 * feels every turn; near-silent on a nightstand, which is fine - it is a bonus signal layered
 * on top of sound, never the only one.
 *
 * Gravity is removed with a slow moving average rather than a high-pass filter so a phone that
 * gets picked up and set down at a new angle settles again within a few seconds.
 */
class MotionMonitor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var baseline = 9.81f
    private var movements = 0
    private var peak = 0f
    private var wasMoving = false

    val available: Boolean get() = accelerometer != null

    fun start() {
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (accelerometer != null) sensorManager.unregisterListener(this)
    }

    /** Movement count and strongest jolt since the previous call, then resets. */
    @Synchronized
    fun drain(): Pair<Int, Float> {
        val result = movements to peak
        movements = 0
        peak = 0f
        return result
    }

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        baseline += (magnitude - baseline) * 0.05f
        val delta = abs(magnitude - baseline)

        // Rising-edge counting, so one long turn is one movement rather than twenty.
        if (delta > MOVE_THRESHOLD) {
            if (!wasMoving) {
                movements++
                wasMoving = true
            }
            if (delta > peak) peak = delta
        } else if (delta < MOVE_THRESHOLD * 0.5f) {
            wasMoving = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** m/s^2 away from resting gravity. Tuned to ignore building vibration. */
        const val MOVE_THRESHOLD = 0.16f
    }
}
