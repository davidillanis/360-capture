package com.example.capture

import com.example.stitching.CameraPose
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureGuidanceTest {
    @Test
    fun `arrows choose the short turn across north and for targets behind the camera`() {
        assertEquals(CaptureCue.RIGHT, captureCue(CameraPose.lookingAt(350.0, 0.0), target(10.0)))
        assertEquals(CaptureCue.LEFT, captureCue(CameraPose.lookingAt(10.0, 0.0), target(350.0)))
        assertEquals(CaptureCue.RIGHT, captureCue(CameraPose.lookingAt(0.0, 0.0), target(170.0)))
        assertEquals(CaptureCue.LEFT, captureCue(CameraPose.lookingAt(0.0, 0.0), target(190.0)))
        assertEquals(CaptureCue.RIGHT, captureCue(CameraPose.lookingAt(0.0, 0.0), target(180.0)))
    }

    @Test
    fun `up and down remain meaningful near the poles regardless of heading`() {
        assertEquals(CaptureCue.UP, captureCue(CameraPose.lookingAt(170.0, 75.0), target(0.0, 90.0)))
        assertEquals(CaptureCue.DOWN, captureCue(CameraPose.lookingAt(240.0, -75.0), target(0.0, -90.0)))
    }

    @Test
    fun `hold instruction agrees with shutter alignment including the roll exception at poles`() {
        assertEquals(CaptureCue.LEVEL, captureCue(CameraPose.lookingAt(0.0, 0.0, 15.0), target(0.0)))
        val nearTarget = CameraPose.lookingAt(2.0, 0.0, 5.0)
        assertEquals(CaptureCue.HOLD, captureCue(nearTarget, target(0.0)))
        assertEquals(true, GuidedCaptureGate().update(nearTarget, target(0.0), 1L).aligned)
        val pole = CameraPose.lookingAt(220.0, 90.0, 100.0)
        assertEquals(CaptureCue.HOLD, captureCue(pole, target(0.0, 90.0)))
        assertEquals(true, GuidedCaptureGate().update(pole, target(0.0, 90.0), 1L).aligned)
    }

    @Test
    fun `arrows follow the preview basis on a sideways phone`() {
        assertEquals(CaptureCue.DOWN, captureCue(CameraPose.lookingAt(0.0, 0.0, 90.0), target(30.0)))
        assertEquals(CaptureCue.WAITING, captureCue(null, target(0.0)))
    }

    private fun target(azimuth: Double, elevation: Double = 0.0) = CaptureTarget(azimuth, elevation, "Guía")
}
