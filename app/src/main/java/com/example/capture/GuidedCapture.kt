package com.example.capture

import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.stitching.Direction
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos

data class CaptureTarget(val azimuth: Double, val elevation: Double, val band: String) {
    val direction: Direction get() = CameraPose.lookingAt(azimuth, elevation).forward
}

object GuidedCapturePlanner {
    /** Reserve image borders and approximately 40% overlap, including the closing pair of each ring. */
    fun plan(k: CameraIntrinsics, startAzimuth: Double): List<CaptureTarget> {
        val horizontal = Math.toDegrees(2 * atan(k.width * 0.88 / (2 * k.fx)))
        val vertical = Math.toDegrees(2 * atan(k.height * 0.88 / (2 * k.fy)))
        require(horizontal in 20.0..120.0 && vertical in 20.0..120.0) { "Campo de visión no compatible" }
        val step = minOf(50.0, vertical * 0.6)
        val poleReach = (vertical + minOf(horizontal, vertical)) * 0.375
        val elevations = mutableListOf<Double>()
        var elevation = step
        while (elevation < 90.0) {
            elevations += elevation
            if (90.0 - elevation <= poleReach) break
            elevation += step
        }
        return buildList {
            fun ring(pitch: Double, label: String, reverse: Boolean) {
                val count = ceil(360 * cos(Math.toRadians(pitch)) / (horizontal * 0.6)).toInt().coerceAtLeast(3)
                for (i in 0 until count) {
                    val yaw = (startAzimuth + (if (reverse) -1 else 1) * i * 360.0 / count + 720) % 360
                    add(CaptureTarget(yaw, pitch, label))
                }
            }
            ring(0.0, "Horizonte", false)
            elevations.forEachIndexed { index, pitch -> ring(pitch, "Hacia el techo", index % 2 == 0) }
            add(CaptureTarget(startAzimuth, 90.0, "Cenit · justo arriba"))
            elevations.forEachIndexed { index, pitch -> ring(-pitch, "Hacia el suelo", index % 2 != 0) }
            add(CaptureTarget(startAzimuth, -90.0, "Nadir · justo abajo"))
        }
    }
}

data class CaptureHold(
    val progress: Float = 0f,
    val aligned: Boolean = false,
    val ready: Boolean = false,
    val moving: Boolean = false
)

/** Pure, timestamp-based shutter gate. No timer can fire while the user is moving or the app is paused. */
class GuidedCaptureGate {
    private var previousPose: CameraPose? = null
    private var previousTime = 0L
    private var anchor: CameraPose? = null
    private var stableSince = 0L
    private var fired = false
    private var currentTarget: CaptureTarget? = null
    private var fastSamples = 0

    fun reset() {
        previousPose = null
        previousTime = 0L
        anchor = null
        stableSince = 0L
        fired = false
        currentTarget = null
        fastSamples = 0
    }

    fun update(pose: CameraPose, target: CaptureTarget, timeNanos: Long): CaptureHold {
        if (currentTarget != target) {
            reset()
            currentTarget = target
        }
        if (fired) return CaptureHold(1f, aligned = true)
        val pointingError = angularDistance(pose.forward, target.direction)
        val roll = Math.toDegrees(atan2(pose.right.z, pose.up.z))
        // Once the hold starts, allow a little hysteresis so sensor jitter at 3° does not
        // repeatedly switch the countdown on and off.
        val aligned = pointingError <= (if (anchor == null) 3.0 else 4.0) &&
            (abs(target.elevation) > 80 || abs(roll) <= (if (anchor == null) 8.0 else 11.0))
        val dt = timeNanos - previousTime
        val oldPose = previousPose
        val continuous = oldPose != null && dt in 1..250_000_000L
        val speed = if (continuous) poseDistance(oldPose!!, pose) / (dt / 1e9) else Double.POSITIVE_INFINITY
        previousPose = pose
        previousTime = timeNanos
        if (!continuous || !aligned || (anchor != null && poseDistance(anchor!!, pose) > 1.5)) {
            anchor = null
            stableSince = 0L
            fastSamples = 0
            return CaptureHold(aligned = aligned)
        }
        // A noisy sample can jump away and back in two readings. Only treat repeated
        // high velocity as real movement when the phone has also left its hold position.
        fastSamples = if (speed > 12.0 && anchor != null && poseDistance(anchor!!, pose) > 0.9) fastSamples + 1 else 0
        if (fastSamples >= 2) {
            anchor = null
            stableSince = 0L
            return CaptureHold(aligned = aligned, moving = true)
        }
        if (anchor == null) {
            anchor = pose
            stableSince = timeNanos
        }
        val progress = ((timeNanos - stableSince) / 850_000_000.0).coerceIn(0.0, 1.0).toFloat()
        val ready = progress >= 1f && fastSamples == 0
        fired = ready
        return CaptureHold(progress, aligned, ready, moving = fastSamples > 0)
    }
}

/** Manual fallback still needs the actual camera aimed near the next planned photo. */
fun canCaptureManually(pose: CameraPose?, target: CaptureTarget?): Boolean {
    if (pose == null || target == null || angularDistance(pose.forward, target.direction) > 6.0) return false
    val roll = Math.toDegrees(atan2(pose.right.z, pose.up.z))
    return abs(target.elevation) > 80 || abs(roll) <= 12.0
}

fun angularDistance(a: Direction, b: Direction): Double = Math.toDegrees(acos(a.dot(b).coerceIn(-1.0, 1.0)))

fun poseDistance(a: CameraPose, b: CameraPose): Double = Math.toDegrees(acos(
    ((a.right.dot(b.right) + a.up.dot(b.up) + a.forward.dot(b.forward) - 1) / 2).coerceIn(-1.0, 1.0)
))
