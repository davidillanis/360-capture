package com.example.sensor

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class RotationVectorSmootherTest {
    @Test
    fun `handheld sensor noise is smoothed but intentional turns are followed`() {
        val filter = RotationVectorSmoother()
        var output = quaternion(0.0)
        for (i in 0..75) {
            val angle = if (i % 2 == 0) 0.45 else -0.45
            output = filter.update(quaternion(angle), 1_000_000_000L + i * 20_000_000L)
        }
        assertTrue(abs(angle(output)) < 0.2)
        for (i in 76..110) {
            output = filter.update(quaternion(25.0), 1_000_000_000L + i * 20_000_000L)
        }
        assertTrue(abs(angle(output) - 25.0) < 0.5)
    }

    @Test
    fun `quaternion sign changes and north wrap do not move the guide around the sphere`() {
        val filter = RotationVectorSmoother()
        filter.update(quaternion(359.0), 1_000_000_000L)
        val oppositeSign = quaternion(1.0).map { -it }.toFloatArray()
        val output = filter.update(oppositeSign, 1_050_000_000L)
        val error = (angle(output) + 360.0) % 360.0
        assertTrue("Expected a short turn across 0°, got $error", error < 2.0 || error > 358.0)
    }

    private fun quaternion(degrees: Double): FloatArray {
        val half = Math.toRadians(degrees / 2)
        return floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
    }

    private fun angle(vector: FloatArray) = Math.toDegrees(2 * atan2(vector[2].toDouble(), vector[3].toDouble()))
}
