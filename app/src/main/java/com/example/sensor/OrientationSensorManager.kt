package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import com.example.stitching.CameraPose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2

data class OrientationData(
    val pitch: Float = 0f, // Elevation of the back camera, positive upwards.
    val roll: Float = 0f,
    val azimuth: Float = 0f,
    val unwrappedAzimuth: Float = 0f,
    val isLevel: Boolean = false,
    val pose: CameraPose? = null,
    val timestampNanos: Long = 0L
)

class OrientationSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    val isAvailable: Boolean get() = rotationSensor != null

    private data class Sample(val matrix: FloatArray, val timestampNanos: Long)
    @Volatile private var latest: Sample? = null
    private val _orientationFlow = MutableStateFlow(OrientationData())
    val orientationFlow: StateFlow<OrientationData> = _orientationFlow.asStateFlow()
    private var lastAzimuth: Float? = null
    private var unwrappedAzimuth = 0f

    fun startListening() {
        rotationSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        latest = null
    }

    /** Snapshot at capture-start, in the same upright orientation as the saved JPEG. */
    fun snapshot(displayRotationDegrees: Int): OrientationData? {
        val sample = latest ?: return null
        if (SystemClock.elapsedRealtimeNanos() - sample.timestampNanos > 1_000_000_000L) return null
        val pose = CameraPose.fromDeviceRotation(sample.matrix, displayRotationDegrees)
        val pitch = pose.forward.elevation.toFloat()
        val roll = Math.toDegrees(atan2(pose.right.z, pose.up.z)).toFloat()
        return OrientationData(
            pitch = pitch, roll = roll, azimuth = pose.forward.azimuth.toFloat(),
            isLevel = abs(pitch) < 5 && abs(roll) < 5,
            pose = pose, timestampNanos = sample.timestampNanos
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        latest = Sample(matrix, event.timestamp)
        @Suppress("DEPRECATION")
        val rotation = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val orientation = snapshot(rotation) ?: return
        val previous = lastAzimuth
        unwrappedAzimuth = if (previous == null) orientation.azimuth else
            unwrappedAzimuth + (orientation.azimuth - previous + 540f) % 360f - 180f
        lastAzimuth = orientation.azimuth
        _orientationFlow.value = orientation.copy(unwrappedAzimuth = unwrappedAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
