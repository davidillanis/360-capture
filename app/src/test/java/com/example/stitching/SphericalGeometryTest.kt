package com.example.stitching

import org.junit.Assert.*
import org.junit.Test

class SphericalGeometryTest {
    private val intrinsics = CameraIntrinsics(401, 301, 200.0, 200.0)

    @Test
    fun `camera centre maps to its optical axis at every elevation including poles`() {
        for (elevation in listOf(-90.0, -65.0, 0.0, 65.0, 90.0)) {
            for (azimuth in listOf(0.0, 90.0, 180.0, 270.0, 359.0)) {
                val pose = CameraPose.lookingAt(azimuth, elevation, 31.0)
                val point = SphericalGeometry.project(pose.forward, pose, intrinsics)!!
                assertEquals(200.0, point.x, 1e-6)
                assertEquals(150.0, point.y, 1e-6)
                assertNull(SphericalGeometry.project(pose.forward * -1.0, pose, intrinsics))
            }
        }
    }

    @Test
    fun `north facing photos wrap across zero and south facing photos span both panorama edges`() {
        val north = CameraPose.lookingAt(359.0, 0.0)
        val acrossZero = SphericalGeometry.project(CameraPose.lookingAt(1.0, 0.0).forward, north, intrinsics)!!
        assertTrue(acrossZero.x > intrinsics.cx)
        val south = CameraPose.lookingAt(180.0, 0.0)
        val left = SphericalGeometry.project(SphericalGeometry.ray(0, 89, 360, 180), south, intrinsics)!!
        val right = SphericalGeometry.project(SphericalGeometry.ray(359, 89, 360, 180), south, intrinsics)!!
        assertEquals(left.x - intrinsics.cx, intrinsics.cx - right.x, 1e-6)
        assertEquals(left.y, right.y, 1e-6)
    }

    @Test
    fun `sensor rotation uses back camera axis and upright image axes`() {
        // Device held upright, rear camera looking north, device top pointing up.
        val matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        val portrait = CameraPose.fromDeviceRotation(matrix, 0)
        assertEquals(1.0, Direction(0.0, 1.0, 0.0).dot(portrait.forward), 1e-6)
        assertEquals(0.0, portrait.forward.elevation, 1e-6)
        assertEquals(1.0, portrait.up.z, 1e-6)
        for (rotation in listOf(90, 180, 270)) {
            val rotated = CameraPose.fromDeviceRotation(matrix, rotation)
            assertEquals(1.0, rotated.forward.dot(portrait.forward), 1e-6)
            assertEquals(0.0, rotated.right.dot(rotated.up), 1e-6)
        }
        val landscape = CameraPose.fromDeviceRotation(matrix, 90)
        assertEquals(1.0, landscape.right.z, 1e-6)
        assertEquals(-1.0, landscape.up.x, 1e-6)
    }

    @Test
    fun `physical sensor and jpeg rotation determine portrait field of view`() {
        val landscape = CameraIntrinsics.fromSensor(4000, 3000, 6.4, 4.8, 4.0, 0)
        val portrait = CameraIntrinsics.fromSensor(3000, 4000, 6.4, 4.8, 4.0, 90)
        assertEquals(2500.0, landscape.fx, 1e-6)
        assertEquals(landscape.fx, portrait.fx, 1e-6)
        assertEquals(landscape.fy, portrait.fy, 1e-6)
        val cropped = CameraIntrinsics.fromSensor(4000, 2250, 6.4, 4.8, 4.0, 0)
        assertEquals(landscape.fy, cropped.fy, 1e-6)
        val scaled = portrait.scaledTo(300, 400)
        assertEquals(250.0, scaled.fx, 1e-6)
        assertEquals(149.5, scaled.cx, 1e-6)
    }

    @Test
    fun `image rays round trip with pitch roll and unequal focal lengths`() {
        val pose = CameraPose.lookingAt(220.0, 72.0, -47.0)
        val k = CameraIntrinsics(501, 401, 250.0, 300.0)
        for ((x, y) in listOf(20.0 to 30.0, 250.0 to 200.0, 480.0 to 370.0)) {
            val projected = SphericalGeometry.project(SphericalGeometry.cameraRay(x, y, pose, k), pose, k)!!
            assertEquals(x, projected.x, 1e-6)
            assertEquals(y, projected.y, 1e-6)
        }
    }
}
