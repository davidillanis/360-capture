package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class OrientationData(
    val pitch: Float = 0f,   // Vertical tilt (-90 to +90)
    val roll: Float = 0f,    // Horizontal level tilt (-180 to +180)
    val azimuth: Float = 0f, // Rotation around 360 (-180 to +180 or 0 to 360)
    val isLevel: Boolean = true
)

class OrientationSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _orientationFlow = MutableStateFlow(OrientationData())
    val orientationFlow: StateFlow<OrientationData> = _orientationFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun startListening() {
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            // Is reasonably level (pitch near 0 and roll near 0)
            val isLevel = abs(pitch) < 5f && abs(roll) < 5f

            _orientationFlow.value = OrientationData(
                pitch = pitch,
                roll = roll,
                azimuth = (azimuth + 360f) % 360f,
                isLevel = isLevel
            )
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val roll = (ax * 5f).coerceIn(-30f, 30f)
            val pitch = ((ay - 9.8f) * 4f).coerceIn(-30f, 30f)
            val isLevel = abs(roll) < 3f && abs(pitch) < 3f

            _orientationFlow.value = OrientationData(
                pitch = pitch,
                roll = roll,
                azimuth = 0f,
                isLevel = isLevel
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
