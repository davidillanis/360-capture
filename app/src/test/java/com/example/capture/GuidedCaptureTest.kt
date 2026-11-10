package com.example.capture

import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.stitching.SphericalCoverage
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.tan

class GuidedCaptureTest {
    @Test
    fun `plans cover the entire sphere for portrait landscape and narrow cameras with aiming tolerance`() {
        for ((horizontal, vertical) in listOf(48.0 to 64.0, 64.0 to 48.0, 80.0 to 95.0, 28.0 to 40.0)) {
            val k = CameraIntrinsics(1000, 1000, 500 / tan(Math.toRadians(horizontal / 2)), 500 / tan(Math.toRadians(vertical / 2)))
            val targets = GuidedCapturePlanner.plan(k, 357.0)
            val coverage = SphericalCoverage(360, 180)
            assertTrue(targets.any { it.elevation == 90.0 })
            assertTrue(targets.any { it.elevation == -90.0 })
            for ((i, target) in targets.withIndex()) {
                coverage.add(CameraPose.lookingAt(target.azimuth + if (i % 2 == 0) 2.0 else -2.0,
                    (target.elevation + 1.0).coerceIn(-90.0, 90.0), 4.0), k)
            }
            assertTrue("$horizontal × $vertical: ${coverage.summary()}", coverage.summary().isComplete)
        }
    }

    @Test
    fun `shutter fires exactly once only after a continuous steady hold`() {
        val target = CaptureTarget(359.0, 0.0, "test")
        val pose = CameraPose.lookingAt(0.5, 0.0)
        val gate = GuidedCaptureGate()
        val samples = (0..30).map { gate.update(pose, target, 1_000_000_000L + it * 50_000_000L) }
        assertTrue(samples.take(18).none { it.ready })
        assertEquals(1, samples.count { it.ready })
        gate.reset()
        assertFalse(gate.update(pose, target, 5_000_000_000L).ready)
    }

    @Test
    fun `motion wrong target roll and sensor gaps cannot trigger the shutter`() {
        val target = CaptureTarget(0.0, 0.0, "test")
        for (scenario in 0..4) {
            val gate = GuidedCaptureGate()
            for (i in 0..60) {
                val pose = when (scenario) {
                    0 -> CameraPose.lookingAt(10.0, 0.0)
                    1 -> CameraPose.lookingAt(0.0, 0.0, 20.0)
                    2 -> CameraPose.lookingAt(if (i % 2 == 0) 1.0 else -1.0, 0.0)
                    3 -> CameraPose.lookingAt(0.0, 0.0)
                    else -> CameraPose.lookingAt((i % 20) * 0.12, 0.0) // sustained drift beyond the hold window
                }
                val dt = if (scenario == 3) 400_000_000L else 50_000_000L
                assertFalse("scenario $scenario, sample $i", gate.update(pose, target, 1_000_000_000L + i * dt).ready)
            }
        }
    }

    @Test
    fun `pole aiming is independent of undefined horizon roll but still needs stability`() {
        val gate = GuidedCaptureGate()
        val target = CaptureTarget(0.0, 90.0, "zenith")
        val pose = CameraPose.lookingAt(130.0, 90.0, 50.0)
        assertTrue((0..22).any { gate.update(pose, target, 1_000_000_000L + it * 50_000_000L).ready })
    }

    @Test
    fun `handheld sensor jitter and brief excursions near the aim threshold do not restart the circle`() {
        val gate = GuidedCaptureGate()
        val target = CaptureTarget(0.0, 0.0, "test")
        val samples = (0..35).map { i ->
            val yaw = if (i % 7 == 0) 3.3 else 2.7 + (if (i % 2 == 0) 0.18 else -0.18)
            gate.update(CameraPose.lookingAt(yaw, 0.0), target, 1_000_000_000L + i * 50_000_000L)
        }
        assertTrue(samples.any { it.progress > 0.5f })
        assertEquals(1, samples.count { it.ready })
    }

    @Test
    fun `manual fallback requires the next target and an upright phone`() {
        val target = CaptureTarget(0.0, 0.0, "test")
        assertTrue(canCaptureManually(CameraPose.lookingAt(5.5, 0.0), target))
        assertFalse(canCaptureManually(CameraPose.lookingAt(8.0, 0.0), target))
        assertFalse(canCaptureManually(CameraPose.lookingAt(0.0, 0.0, 20.0), target))
        assertFalse(canCaptureManually(null, target))
    }
}
