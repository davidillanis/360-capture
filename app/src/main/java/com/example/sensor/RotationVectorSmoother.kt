package com.example.sensor

import kotlin.math.sqrt

/** Low-pass the rotation itself (not Euler angles, which wrap at north and the poles). */
class RotationVectorSmoother {
    private var previous: FloatArray? = null
    private var previousTime = 0L

    fun reset() {
        previous = null
        previousTime = 0L
    }

    /** Returns an [x, y, z, w] rotation vector accepted by SensorManager. */
    fun update(vector: FloatArray, timeNanos: Long): FloatArray {
        require(vector.size >= 3)
        val raw = floatArrayOf(vector[0], vector[1], vector[2],
            if (vector.size >= 4) vector[3] else sqrt((1f - vector[0] * vector[0] -
                vector[1] * vector[1] - vector[2] * vector[2]).coerceAtLeast(0f)))
        normalize(raw)
        val old = previous
        val dt = timeNanos - previousTime
        if (old == null || dt !in 1..250_000_000L) {
            previous = raw
            previousTime = timeNanos
            return raw.copyOf()
        }
        // q and -q represent the same orientation: interpolate on the short arc.
        val dot = (0..3).sumOf { (old[it] * raw[it]).toDouble() }
        if (dot < 0) for (i in 0..3) raw[i] = -raw[i]
        val alpha = (dt.toDouble() / (90_000_000.0 + dt)).toFloat()
        val next = FloatArray(4) { i -> old[i] * (1f - alpha) + raw[i] * alpha }
        normalize(next)
        previous = next
        previousTime = timeNanos
        return next.copyOf()
    }

    private fun normalize(q: FloatArray) {
        val length = sqrt(q.sumOf { (it * it).toDouble() }).toFloat()
        if (length > 0f) for (i in 0..3) q[i] /= length
    }
}
