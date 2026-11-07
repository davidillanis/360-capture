package com.example.stitching

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class EquirectangularStitcherTest {
    @Before
    fun loadNativeLibrary() {
        assertTrue(OpenCVLoader.initLocal())
    }

    @Test
    fun sixSyntheticPhotosReconstructKnownSphereIncludingSeamAndPoles() {
        val k = CameraIntrinsics(241, 241, 105.0, 105.0)
        EquirectangularStitcher(120).use { stitcher ->
            val poses = listOf(
                CameraPose.lookingAt(0.0, 0.0), CameraPose.lookingAt(90.0, 0.0),
                CameraPose.lookingAt(180.0, 0.0), CameraPose.lookingAt(270.0, 0.0),
                CameraPose.lookingAt(0.0, 90.0), CameraPose.lookingAt(0.0, -90.0)
            )
            for (pose in poses) {
                val source = syntheticPhoto(pose, k)
                try {
                    stitcher.add(source, pose, k)
                } finally {
                    source.release()
                }
            }
            val result = stitcher.finish()
            try {
                assertEquals(240, result.image.cols())
                assertEquals(120, result.image.rows())
                assertTrue(result.coverage.isComplete)
                assertTrue(result.coverage.zenithCovered)
                assertTrue(result.coverage.nadirCovered)
                // An analytic world-direction colour field is continuous at the panorama seam.
                for (y in 0 until 120) {
                    for (x in 0 until 240) {
                        val ray = SphericalGeometry.ray(x, y, 240, 120)
                        val expected = colour(ray)
                        val actual = result.image.get(y, x)
                        for (channel in 0..2) {
                            assertTrue("Pixel ($x,$y), channel $channel: ${actual[channel]} vs ${expected[channel]}",
                                abs(actual[channel] - expected[channel]) <= 3.0)
                        }
                    }
                }
            } finally {
                result.image.release()
            }
        }
    }

    @Test
    fun invalidPixelsStayBlackAndOtherPhotosDoNotPolluteValidColours() {
        val k = CameraIntrinsics(201, 201, 160.0, 160.0)
        val red = Mat(201, 201, CvType.CV_8UC4, Scalar(240.0, 30.0, 10.0, 255.0))
        val blue = Mat(201, 201, CvType.CV_8UC4, Scalar(10.0, 20.0, 230.0, 255.0))
        try {
            EquirectangularStitcher(100).use { stitcher ->
                stitcher.add(red, CameraPose.lookingAt(359.0, 0.0), k)
                stitcher.add(blue, CameraPose.lookingAt(180.0, 0.0), k)
                val result = stitcher.finish()
                try {
                    assertArrayEquals(doubleArrayOf(240.0, 30.0, 10.0), result.image.get(50, 100), 1.0)
                    assertArrayEquals(doubleArrayOf(10.0, 20.0, 230.0), result.image.get(50, 0), 1.0)
                    assertArrayEquals(doubleArrayOf(10.0, 20.0, 230.0), result.image.get(50, 199), 1.0)
                    assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0), result.image.get(0, 100), 0.0)
                    assertFalse(result.coverage.isComplete)
                    assertFalse(result.coverage.zenithCovered)
                    assertFalse(result.coverage.nadirCovered)
                } finally {
                    result.image.release()
                }
            }
        } finally {
            red.release()
            blue.release()
        }
    }

    @Test
    fun tiltedAndRolledPhotoHasCorrectPositionAndColours() {
        val k = CameraIntrinsics(321, 241, 180.0, 180.0)
        val pose = CameraPose.lookingAt(243.0, 63.0, 32.0)
        val source = syntheticPhoto(pose, k)
        try {
            EquirectangularStitcher(100).use { stitcher ->
                stitcher.add(source, pose, k)
                val result = stitcher.finish()
                try {
                    var checked = 0
                    for (y in 0 until 100) {
                        for (x in 0 until 200) {
                            val ray = SphericalGeometry.ray(x, y, 200, 100)
                            if (SphericalGeometry.project(ray, pose, k, margin = 2.0) == null) continue
                            assertArrayEquals(colour(ray), result.image.get(y, x), 3.0)
                            checked++
                        }
                    }
                    assertTrue(checked > 100)
                } finally {
                    result.image.release()
                }
            }
        } finally {
            source.release()
        }
    }

    @Test
    fun jpegPipelineProducesTwoToOneWithoutStretchingMissingRegions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("spherical-test", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(401, 301, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.RED)
            assertTrue(ImageUtils.saveJpeg(bitmap, file))
            val result = requireNotNull(PanoramaStitcher.stitch(listOf(SliceSource(
                file, CameraPose.lookingAt(0.0, 0.0), CameraIntrinsics(401, 301, 250.0, 250.0)
            ))))
            try {
                assertEquals(2048, result.bitmap.width)
                assertEquals(1024, result.bitmap.height)
                assertFalse(result.coverage.isComplete)
                assertTrue(android.graphics.Color.red(result.bitmap.getPixel(1024, 512)) > 240)
                assertEquals(android.graphics.Color.BLACK, result.bitmap.getPixel(1024, 0))
            } finally {
                result.bitmap.recycle()
            }
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    private fun colour(ray: Direction) = doubleArrayOf((ray.x + 1) * 127.5, (ray.y + 1) * 127.5, (ray.z + 1) * 127.5)

    private fun syntheticPhoto(pose: CameraPose, k: CameraIntrinsics): Mat {
        val data = ByteArray(k.width * k.height * 4)
        for (y in 0 until k.height) {
            for (x in 0 until k.width) {
                val rgb = colour(SphericalGeometry.cameraRay(x.toDouble(), y.toDouble(), pose, k))
                val index = (y * k.width + x) * 4
                for (channel in 0..2) data[index + channel] = rgb[channel].roundToInt().coerceIn(0, 255).toByte()
                data[index + 3] = 255.toByte()
            }
        }
        return Mat(k.height, k.width, CvType.CV_8UC4).apply { put(0, 0, data) }
    }
}
