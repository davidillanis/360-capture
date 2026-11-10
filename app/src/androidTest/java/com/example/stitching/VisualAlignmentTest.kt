package com.example.stitching

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.capture.poseDistance
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class VisualAlignmentTest {
    @Before
    fun loadNative() { assertTrue(OpenCVLoader.initLocal()) }

    @Test
    fun texturedJpegsCorrectSensorErrorsAcrossRingClosureElevationsAndPoles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "visual-test-${System.nanoTime()}").apply { mkdirs() }
        val texture = texture()
        val k = CameraIntrinsics(512, 640, 390.0, 390.0)
        val truePoses = (0 until 10).map { CameraPose.lookingAt(it * 36.0, 0.0) } + listOf(
            CameraPose.lookingAt(0.0, 50.0), CameraPose.lookingAt(36.0, 90.0, 21.0),
            CameraPose.lookingAt(36.0, -50.0)
        )
        val sources = truePoses.mapIndexed { i, pose ->
            val gain = if (i == 0) 1.0 else 0.8 + (i % 4) * 0.1
            val bitmap = render(texture, pose, k, gain)
            val file = File(directory, "photo_$i.jpg")
            try { assertTrue(ImageUtils.saveJpeg(bitmap, file, 97)) } finally { bitmap.recycle() }
            val error = if (i == 0) Rotation3.identity() else Rotation3.exp(
                Direction(sin(i.toDouble()) * 0.04, cos(i.toDouble()) * 0.035, 0.025 + i * 0.002))
            SliceSource(file, error.apply(pose), k)
        }
        try {
            val candidates = VisualAlignment.candidatePairs(sources)
            assertTrue("closing pair missing", 0 to 9 in candidates)
            val result = VisualAlignment.refine(sources)
            val before = sources.indices.map { poseDistance(sources[it].pose, truePoses[it]) }.average()
            val after = sources.indices.map { poseDistance(result.poses[it], truePoses[it]) }
            assertTrue("Pairs: ${result.matchedPairs}; support: ${result.supportedPhotos}", result.supportedPhotos.size == sources.size)
            assertTrue("before=$before, after=$after", after.average() < before * 0.25)
            assertTrue("Pose errors: $after", after.max() < 0.85)
            assertTrue(result.matchedPairs >= 13)
            val correctedBrightness = result.gains.mapIndexed { i, gain -> (if (i == 0) 1.0 else 0.8 + (i % 4) * 0.1) * gain.average() }
            assertTrue("Exposures: $correctedBrightness", correctedBrightness.max() - correctedBrightness.min() < 0.07)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun featurelessJpegsRetainSensorPosesInsteadOfInventingAnAlignment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("blank-alignment", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.GRAY)
            ImageUtils.saveJpeg(bitmap, file)
            val k = CameraIntrinsics(320, 400, 240.0, 240.0)
            val sources = listOf(SliceSource(file, CameraPose.lookingAt(0.0, 0.0), k), SliceSource(file, CameraPose.lookingAt(30.0, 5.0), k))
            val result = VisualAlignment.refine(sources)
            assertEquals(0, result.matchedPairs)
            for (i in sources.indices) assertTrue(poseDistance(result.poses[i], sources[i].pose) < 1e-5)
        } finally { bitmap.recycle(); file.delete() }
    }

    private fun texture(): ByteArray {
        val random = Random(893)
        val mat = Mat(1024, 2048, CvType.CV_8UC3, Scalar(100.0, 100.0, 100.0))
        try {
            repeat(18000) {
                val x = random.nextInt(2048).toDouble()
                val y = random.nextInt(1024).toDouble()
                val color = Scalar(random.nextDouble(40.0, 195.0), random.nextDouble(40.0, 195.0), random.nextDouble(40.0, 195.0))
                Imgproc.rectangle(mat, Point(x, y), Point(x + random.nextInt(3, 22), y + random.nextInt(3, 22)), color, -1)
            }
            return ByteArray(2048 * 1024 * 3).also { mat.get(0, 0, it) }
        } finally { mat.release() }
    }

    private fun render(texture: ByteArray, pose: CameraPose, k: CameraIntrinsics, gain: Double): Bitmap {
        val pixels = IntArray(k.width * k.height)
        for (y in 0 until k.height) for (x in 0 until k.width) {
            val ray = SphericalGeometry.cameraRay(x.toDouble(), y.toDouble(), pose, k)
            val u = (atan2(ray.x, ray.y) / (2 * PI) + 0.5) * 2048 - 0.5
            val v = ((0.5 - asin(ray.z) / PI) * 1024 - 0.5).coerceIn(0.0, 1022.999)
            val left = kotlin.math.floor(u).toInt()
            val top = v.toInt()
            val dx = u - left
            val dy = v - top
            fun sample(px: Int, py: Int, c: Int) = texture[(py * 2048 + (px + 2048) % 2048) * 3 + c].toInt() and 255
            val color = IntArray(3) { c ->
                val value = (sample(left, top, c) * (1 - dx) + sample(left + 1, top, c) * dx) * (1 - dy) +
                    (sample(left, top + 1, c) * (1 - dx) + sample(left + 1, top + 1, c) * dx) * dy
                (value * gain).roundToInt().coerceIn(0, 255)
            }
            pixels[y * k.width + x] = android.graphics.Color.rgb(color[0], color[1], color[2])
        }
        return Bitmap.createBitmap(pixels, k.width, k.height, Bitmap.Config.ARGB_8888)
    }
}
