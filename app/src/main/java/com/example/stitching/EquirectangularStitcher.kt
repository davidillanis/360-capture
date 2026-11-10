package com.example.stitching

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt

data class StitchedImage(val image: Mat, val coverage: CoverageSummary)

/**
 * Inverse spherical projection using each upright photo's camera basis and intrinsics.
 * Images are added one at a time; remapping/blending uses tiles to bound native memory.
 * Retains the two most central sources. Only a narrow, photometrically consistent seam is blended;
 * high-contrast disagreements keep a single source instead of creating translucent duplicates.
 */
class EquirectangularStitcher(private val height: Int = 1024) : AutoCloseable {
    private val width = height * 2
    private val color = Mat.zeros(height, width, CvType.CV_8UC3)
    private val weight = Mat.zeros(height, width, CvType.CV_32FC1)
    private val secondColor = Mat.zeros(height, width, CvType.CV_8UC3)
    private val secondWeight = Mat.zeros(height, width, CvType.CV_32FC1)
    private val sinLongitude = DoubleArray(width) { sin(((it + 0.5) / width * 2 - 1) * PI) }
    private val cosLongitude = DoubleArray(width) { cos(((it + 0.5) / width * 2 - 1) * PI) }

    fun add(image: Mat, pose: CameraPose, intrinsics: CameraIntrinsics,
            gains: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0), checkCancelled: () -> Unit = {}) {
        require(image.cols() == intrinsics.width && image.rows() == intrinsics.height)
        require(image.type() == CvType.CV_8UC4)
        require(gains.size == 3 && gains.all { it.isFinite() && it > 0 })
        val rgb = Mat()
        try {
            Imgproc.cvtColor(image, rgb, Imgproc.COLOR_RGBA2RGB)
            Core.multiply(rgb, Scalar(gains[0], gains[1], gains[2]), rgb)
            for (top in 0 until height step TILE_HEIGHT) {
                checkCancelled()
                val rows = minOf(TILE_HEIGHT, height - top)
                val mapX = Mat(rows, width, CvType.CV_32FC1)
                val mapY = Mat(rows, width, CvType.CV_32FC1)
                val warped = Mat()
                val colors = color.rowRange(top, top + rows)
                val weights = weight.rowRange(top, top + rows)
                val runnerColors = secondColor.rowRange(top, top + rows)
                val runnerWeights = secondWeight.rowRange(top, top + rows)
                try {
                    val xs = FloatArray(rows * width) { -1f }
                    val ys = FloatArray(rows * width) { -1f }
                    val ws = FloatArray(rows * width)
                    fillMap(pose, intrinsics, top, rows, xs, ys, ws)
                    mapX.put(0, 0, xs)
                    mapY.put(0, 0, ys)
                    if (ws.none { it > 0f }) continue
                    Imgproc.remap(rgb, warped, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))
                    val best = FloatArray(ws.size)
                    val second = FloatArray(ws.size)
                    val pixels = ByteArray(ws.size * 3)
                    val primaryPixels = ByteArray(pixels.size)
                    val secondaryPixels = ByteArray(pixels.size)
                    weights.get(0, 0, best)
                    runnerWeights.get(0, 0, second)
                    warped.get(0, 0, pixels)
                    colors.get(0, 0, primaryPixels)
                    runnerColors.get(0, 0, secondaryPixels)
                    for (i in ws.indices) {
                        if (ws[i] <= 0f) continue
                        val offset = i * 3
                        if (ws[i] > best[i]) {
                            second[i] = best[i]
                            best[i] = ws[i]
                            for (c in 0..2) {
                                secondaryPixels[offset + c] = primaryPixels[offset + c]
                                primaryPixels[offset + c] = pixels[offset + c]
                            }
                        } else if (ws[i] > second[i]) {
                            second[i] = ws[i]
                            for (c in 0..2) secondaryPixels[offset + c] = pixels[offset + c]
                        }
                    }
                    weights.put(0, 0, best)
                    runnerWeights.put(0, 0, second)
                    colors.put(0, 0, primaryPixels)
                    runnerColors.put(0, 0, secondaryPixels)
                } finally {
                    listOf(mapX, mapY, warped, colors, weights, runnerColors, runnerWeights).forEach { it.release() }
                }
            }
        } finally {
            rgb.release()
        }
    }

    private fun fillMap(
        pose: CameraPose, k: CameraIntrinsics, top: Int, rows: Int,
        xs: FloatArray, ys: FloatArray, weights: FloatArray
    ) {
        val featherWidth = minOf(k.width, k.height) * 0.06
        for (row in 0 until rows) {
            val latitude = (0.5 - (top + row + 0.5) / height) * PI
            val cosLatitude = cos(latitude)
            val z = sin(latitude)
            for (col in 0 until width) {
                val x = sinLongitude[col] * cosLatitude
                val y = cosLongitude[col] * cosLatitude
                val depth = x * pose.forward.x + y * pose.forward.y + z * pose.forward.z
                if (depth <= 1e-8) continue
                val u = k.cx + k.fx * (x * pose.right.x + y * pose.right.y + z * pose.right.z) / depth
                val v = k.cy - k.fy * (x * pose.up.x + y * pose.up.y + z * pose.up.z) / depth
                if (u < 0 || v < 0 || u > k.width - 1 || v > k.height - 1) continue
                val index = row * width + col
                xs[index] = u.toFloat()
                ys[index] = v.toFloat()
                val edge = minOf(u, v, k.width - 1 - u, k.height - 1 - v)
                weights[index] = (depth * depth * (edge / featherWidth).coerceIn(0.001, 1.0)).toFloat()
            }
        }
    }

    fun finish(checkCancelled: () -> Unit = {}): StitchedImage {
        val output = Mat(height, width, CvType.CV_8UC3)
        val coverage = SphericalCoverage(width, height)
        try {
            val rowWeights = FloatArray(width)
            val secondaryWeights = FloatArray(width)
            val primary = ByteArray(width * 3)
            val secondary = ByteArray(width * 3)
            for (y in 0 until height) {
                checkCancelled()
                weight.get(y, 0, rowWeights)
                coverage.setRow(y, rowWeights)
                secondWeight.get(y, 0, secondaryWeights)
                color.get(y, 0, primary)
                secondColor.get(y, 0, secondary)
                for (x in 0 until width) {
                    if (secondaryWeights[x] <= 0f) continue
                    val alpha = (0.5f * (1 - (rowWeights[x] - secondaryWeights[x]) / SEAM_WIDTH)).coerceIn(0f, 0.5f)
                    if (alpha <= 0f) continue
                    val offset = x * 3
                    val difference = (0..2).maxOf { abs((primary[offset + it].toInt() and 255) - (secondary[offset + it].toInt() and 255)) }
                    if (difference > 36) continue
                    for (c in 0..2) {
                        val first = primary[offset + c].toInt() and 255
                        val next = secondary[offset + c].toInt() and 255
                        primary[offset + c] = (first * (1 - alpha) + next * alpha).roundToInt().toByte()
                    }
                }
                output.put(y, 0, primary)
            }
            return StitchedImage(output, coverage.summary())
        } catch (error: Throwable) {
            output.release()
            throw error
        }
    }

    override fun close() {
        color.release()
        weight.release()
        secondColor.release()
        secondWeight.release()
    }

    companion object {
        private const val TILE_HEIGHT = 64
        private const val SEAM_WIDTH = 0.025f
    }
}
