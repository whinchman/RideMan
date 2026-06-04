package com.two17industries.rideman.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorRepository(context: Context) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val hasBarometer: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /** Altitude in meters from barometric pressure, using the standard atmosphere model. */
    fun altitudeMeters(): Flow<Double> = callbackFlow {
        val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressure == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val hpa = e.values[0]
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa
                ).toDouble()
                trySend(altitude)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, pressure, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }

    /** Heading 0..359 degrees (magnetic) from the rotation-vector sensor. */
    fun headingDegrees(): Flow<Float> = callbackFlow {
        val rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rot == null) { close(); return@callbackFlow }
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, e.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (deg < 0f) deg += 360f
                trySend(deg)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, rot, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }
}
