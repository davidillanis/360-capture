package com.example.stitching

import com.example.capture.poseDistance
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RotationAlignmentTest {
    @Test
    fun `robust fit recovers three axis drift despite false feature matches`() {
        val random = Random(31)
        val correction = Rotation3.exp(Direction(0.04, -0.06, 0.05))
        val matches = (0 until 100).map {
            val from = CameraPose.lookingAt(random.nextDouble(335.0, 385.0), random.nextDouble(-25.0, 35.0)).forward
            RayMatch(from, if (it < 65) correction.apply(from) else CameraPose.lookingAt(random.nextDouble(0.0, 360.0), 30.0).forward)
        }
        val result = requireNotNull(RotationAlignment.robust(matches))
        assertTrue((result.rotation * correction.inverse()).degrees < 0.01)
        assertTrue(result.inliers.size >= 65)
        assertTrue(result.inliers.none { it >= 65 })
    }

    @Test
    fun `featureless or contradictory observations retain sensor fallback`() {
        assertNull(RotationAlignment.robust(emptyList()))
        val ray = Direction(0.0, 1.0, 0.0)
        assertNull(RotationAlignment.robust(List(40) { RayMatch(ray, ray) }))
        val random = Random(54)
        val pairs = List(100) {
            RayMatch(CameraPose.lookingAt(random.nextDouble(360.0), random.nextDouble(-80.0, 80.0)).forward,
                CameraPose.lookingAt(random.nextDouble(360.0), random.nextDouble(-80.0, 80.0)).forward)
        }
        assertNull(RotationAlignment.robust(pairs))
    }

    @Test
    fun `global graph closes the ring and preserves isolated and anchored poses`() {
        val expected = List(9) { i -> Rotation3.exp(Direction(0.003 * i, -0.004 * i, 0.008 * i)) }
        val edges = (0 until 8).map { i -> RotationEdge(i, i + 1, expected[i].inverse() * expected[i + 1], 1.0) } +
            RotationEdge(0, 8, expected[8], 2.0) + RotationEdge(2, 6, expected[2].inverse() * expected[6], 1.5)
        val corrected = RotationAlignment.optimize(10, edges)
        for (i in 0..8) assertTrue("node $i", (corrected[i] * expected[i].inverse()).degrees < 0.15)
        assertEquals(0.0, corrected[9].degrees, 1e-9)
        val pose = corrected[8].apply(CameraPose.lookingAt(355.0, 72.0, 32.0))
        assertEquals(0.0, pose.right.dot(pose.up), 1e-9)
        assertEquals(1.0, pose.forward.dot(pose.forward), 1e-9)
        assertTrue(poseDistance(pose, CameraPose.lookingAt(355.0, 72.0, 32.0)) > 2)
    }
}
