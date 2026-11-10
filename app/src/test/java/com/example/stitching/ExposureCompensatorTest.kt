package com.example.stitching

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

class ExposureCompensatorTest {
    @Test
    fun `overlap metering removes exposure and colour steps without changing isolated photos`() {
        val k = CameraIntrinsics(160, 120, 110.0, 110.0)
        fun frame(channels: List<Int>) = ExposureFrame(ByteArray(160 * 120 * 3) { channels[it % 3].toByte() }, k)
        val a = frame(listOf(100, 110, 120))
        val b = frame(listOf(70, 88, 108))
        val edge = requireNotNull(ExposureCompensator.measure(0, 1, a, b, CameraPose.lookingAt(350.0, 0.0), CameraPose.lookingAt(15.0, 0.0)))
        val gains = ExposureCompensator.gains(3, listOf(edge))
        for (c in 0..2) {
            val first = listOf(100, 110, 120)[c] * gains[0][c]
            val second = listOf(70, 88, 108)[c] * gains[1][c]
            assertEquals(first, second, 1.0)
        }
        assertArrayEquals(doubleArrayOf(1.0, 1.0, 1.0), gains[2], 1e-9)
    }

    @Test
    fun `clipped or non overlapping regions do not create arbitrary brightness corrections`() {
        val k = CameraIntrinsics(160, 120, 110.0, 110.0)
        val clipped = ExposureFrame(ByteArray(160 * 120 * 3) { 255.toByte() }, k)
        val dark = ExposureFrame(ByteArray(160 * 120 * 3) { 10 }, k)
        assertNull(ExposureCompensator.measure(0, 1, clipped, dark, CameraPose.lookingAt(0.0, 0.0), CameraPose.lookingAt(0.0, 0.0)))
        val valid = ExposureFrame(ByteArray(160 * 120 * 3) { 100 }, k)
        assertNull(ExposureCompensator.measure(0, 1, valid, valid, CameraPose.lookingAt(0.0, 0.0), CameraPose.lookingAt(180.0, 0.0)))
        val gains = ExposureCompensator.gains(2, listOf(ExposureEdge(0, 1, DoubleArray(3) { ln(10.0) }, 1.0)))
        assertTrue(gains.all { it.all { gain -> gain in (1 / 1.6)..1.6 } })
    }
}
