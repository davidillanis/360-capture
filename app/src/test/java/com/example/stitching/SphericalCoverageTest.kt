package com.example.stitching

import org.junit.Assert.*
import org.junit.Test

class SphericalCoverageTest {
    private val k = CameraIntrinsics(401, 401, 170.0, 170.0)

    @Test
    fun `duplicates never increase coverage and horizontal ring does not cover poles`() {
        val coverage = SphericalCoverage()
        val pose = CameraPose.lookingAt(0.0, 0.0)
        coverage.add(pose, k)
        val first = coverage.summary()
        repeat(24) { coverage.add(pose, k) }
        assertEquals(first, coverage.summary())
        assertFalse(coverage.summary().isComplete)
        for (az in listOf(90.0, 180.0, 270.0)) coverage.add(CameraPose.lookingAt(az, 0.0), k)
        assertFalse(coverage.summary().zenithCovered)
        assertFalse(coverage.summary().nadirCovered)
        assertNotNull(coverage.nearestMissing(pose.forward))
    }

    @Test
    fun `six overlapping cube faces cover sphere and all display cells`() {
        val coverage = SphericalCoverage()
        for ((yaw, pitch) in listOf(0.0 to 0.0, 90.0 to 0.0, 180.0 to 0.0, 270.0 to 0.0, 0.0 to 90.0, 0.0 to -90.0)) {
            coverage.add(CameraPose.lookingAt(yaw, pitch), k)
        }
        assertEquals(1.0, coverage.summary().fraction, 1e-6)
        assertTrue(coverage.summary().isComplete)
        assertTrue(coverage.summary().zenithCovered)
        assertTrue(coverage.summary().nadirCovered)
        assertEquals(24, coverage.coveredCells().size)
        assertNull(coverage.nearestMissing(Direction(0.0, 1.0, 0.0)))
    }

    @Test
    fun `percentage uses sphere area and still reports a small polar hole`() {
        val coverage = SphericalCoverage(360, 180)
        for (y in 1 until 180) coverage.setRow(y, FloatArray(360) { 1f })
        val result = coverage.summary()
        assertTrue(result.fraction > 0.999)
        assertFalse(result.isComplete)
        assertFalse(result.zenithCovered)
        assertTrue(result.nadirCovered)
        assertEquals(360, result.missingPixels)
    }
}
