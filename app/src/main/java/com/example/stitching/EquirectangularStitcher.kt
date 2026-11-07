package com.example.stitching

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class StitchedImage(val image: Mat, val coverage: CoverageSummary)

/**
 * Inverse spherical projection using each upright photo's camera basis and intrinsics.
 * Images are added one at a time; remapping/blending uses tiles to bound native memory.
 * This is a sensor-based baseline, without visual alignment or exposure compensation.
 */
class EquirectangularStitcher(private val height: Int = 1024) : AutoCloseable {
    private val width = height * 2
    private val color = Mat.zeros(height, width, CvType.CV_32FC3)
    private val weight = Mat.zeros(height, width, CvType.CV_32FC1)
    private val sinLongitude = DoubleArray(width) { sin(((it + 0.5) / width * 2 - 1) * PI) }
    private val cosLongitude = DoubleArray(width) { cos(((it + 0.5) / width * 2 - 1) * PI) }

    fun add(image: Mat, pose: CameraPose, intrinsics: CameraIntrinsics, checkCancelled: () -> Unit = {}) {
        require(image.cols() == intrinsics.width && image.rows() == intrinsics.height)
        require(image.type() == CvType.CV_8UC4)
        val rgb = Mat()
        val source = Mat()
        try {
            Imgproc.cvtColor(image, rgb, Imgproc.COLOR_RGBA2RGB)
            // Keep the 0..255 range; converting 0..1 straight to CV_8U would produce a black image.
            rgb.convertTo(source, CvType.CV_32FC3)
            for (top in 0 until height step TILE_HEIGHT) {
                checkCancelled()
                val rows = minOf(TILE_HEIGHT, height - top)
                val mapX = Mat(rows, width, CvType.CV_32FC1)
                val mapY = Mat(rows, width, CvType.CV_32FC1)
                val feather = Mat(rows, width, CvType.CV_32FC1)
                val featherRgb = Mat()
                val warped = Mat()
                val colors = color.rowRange(top, top + rows)
                val weights = weight.rowRange(top, top + rows)
                try {
                    val xs = FloatArray(rows * width) { -1f }
                    val ys = FloatArray(rows * width) { -1f }
                    val ws = FloatArray(rows * width)
                    fillMap(pose, intrinsics, top, rows, xs, ys, ws)
                    mapX.put(0, 0, xs)
                    mapY.put(0, 0, ys)
                    feather.put(0, 0, ws)
                    Imgproc.remap(source, warped, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))
                    Core.merge(listOf(feather, feather, feather), featherRgb)
                    Core.multiply(warped, featherRgb, warped)
                    Core.add(colors, warped, colors)
                    Core.add(weights, feather, weights)
                } finally {
                    listOf(mapX, mapY, feather, featherRgb, warped, colors, weights).forEach { it.release() }
                }
            }
        } finally {
            rgb.release()
            source.release()
        }
    }

    private fun fillMap(
        pose: CameraPose, k: CameraIntrinsics, top: Int, rows: Int,
        xs: FloatArray, ys: FloatArray, weights: FloatArray
    ) {
        val featherWidth = minOf(k.width, k.height) * 0.15
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
                weights[index] = (edge / featherWidth).coerceIn(0.001, 1.0).toFloat()
            }
        }
    }

    fun finish(checkCancelled: () -> Unit = {}): StitchedImage {
        val output = Mat(height, width, CvType.CV_8UC3)
        val coverage = SphericalCoverage(width, height)
        try {
            val rowWeights = FloatArray(width)
            for (y in 0 until height) {
                checkCancelled()
                weight.get(y, 0, rowWeights)
                coverage.setRow(y, rowWeights)
            }
            for (top in 0 until height step TILE_HEIGHT) {
                checkCancelled()
                val bottom = minOf(top + TILE_HEIGHT, height)
                val colors = color.rowRange(top, bottom)
                val weights = weight.rowRange(top, bottom)
                val destination = output.rowRange(top, bottom)
                val safeWeight = Mat()
                val weightRgb = Mat()
                val average = Mat()
                try {
                    Core.max(weights, Scalar(1e-9), safeWeight)
                    Core.merge(listOf(safeWeight, safeWeight, safeWeight), weightRgb)
                    Core.divide(colors, weightRgb, average)
                    average.convertTo(destination, CvType.CV_8UC3)
                } finally {
                    listOf(colors, weights, destination, safeWeight, weightRgb, average).forEach { it.release() }
                }
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
    }

    companion object {
        private const val TILE_HEIGHT = 64
    }
}
