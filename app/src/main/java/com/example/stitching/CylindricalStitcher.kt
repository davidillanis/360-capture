package com.example.stitching

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class StitchImage(
    val mat: Mat,
    val azimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f
)

/**
 * Deterministic fallback stitcher. Projects each image onto a 2:1 equirectangular
 * canvas using the azimuth/pitch/roll read from the device sensors at capture time.
 * Works even when no features can be matched.
 */
class CylindricalStitcher(
    private val canvasHeight: Int = 800,
    private val fovHorizontalDeg: Float = 67f
) {

    fun stitch(images: List<StitchImage>): Mat {
        require(images.isNotEmpty())
        val width = canvasHeight * 2
        val height = canvasHeight
        val fPano = width / (2.0 * PI)

        val fCam: Double
        val cx: Double
        val cy: Double
        val cols: Int
        val rows: Int

        // Normalize every image to the same height so a single focal applies.
        val normalized = images.map { img ->
            if (img.mat.rows() == canvasHeight) {
                img
            } else {
                val scale = canvasHeight.toDouble() / img.mat.rows()
                val w = (img.mat.cols() * scale).toInt()
                val resized = Mat()
                Imgproc.resize(img.mat, resized, Size(w.toDouble(), canvasHeight.toDouble()))
                img.copy(mat = resized)
            }
        }

        val first = normalized[0]
        cols = first.mat.cols()
        rows = first.mat.rows()
        val fovH = Math.toRadians(fovHorizontalDeg.toDouble())
        fCam = (cols / 2.0) / tan(fovH / 2.0)
        cx = cols / 2.0
        cy = rows / 2.0

        val accColor = Mat(width, height, CvType.CV_32FC3, Scalar(0.0))
        val accWeight = Mat(width, height, CvType.CV_32F, Scalar(0.0))

        val mapX = Mat(width, height, CvType.CV_32FC1)
        val mapY = Mat(width, height, CvType.CV_32FC1)
        val mapMask = Mat(width, height, CvType.CV_32F)
        val dataX = FloatArray(width * height)
        val dataY = FloatArray(width * height)
        val dataMask = FloatArray(width * height)

        for (img in normalized) {
            val theta = Math.toRadians(img.azimuthDeg.toDouble())
            val pitch = Math.toRadians(img.pitchDeg.toDouble())
            val roll = Math.toRadians(img.rollDeg.toDouble())
            val cosRoll = cos(roll)
            val sinRoll = sin(roll)

            dataMask.fill(0f)

            for (py in 0 until height) {
                val phi = (py - height / 2.0) / fPano
                val tanPhi = tan(phi)
                for (px in 0 until width) {
                    val lambda = (px - width / 2.0) / fPano - theta
                    if (abs(lambda) > fovH / 2.0) continue
                    val cosL = cos(lambda)
                    val tanL = tan(lambda)
                    var u = fCam * tanL + cx
                    var v = fCam * (tanPhi / cosL) + cy
                    // Correct for horizon roll.
                    val ru = u - cx
                    val rv = v - cy
                    u = cx + ru * cosRoll - rv * sinRoll
                    v = cy + ru * sinRoll + rv * cosRoll
                    if (u >= 0.0 && u < cols && v >= 0.0 && v < rows) {
                        val idx = py * width + px
                        dataX[idx] = u.toFloat()
                        dataY[idx] = v.toFloat()
                        dataMask[idx] = 1f
                    }
                }
            }

            mapX.put(0, 0, dataX)
            mapY.put(0, 0, dataY)
            mapMask.put(0, 0, dataMask)

            val rgb = Mat()
            Imgproc.cvtColor(img.mat, rgb, Imgproc.COLOR_RGBA2RGB)
            val srcF = Mat()
            rgb.convertTo(srcF, CvType.CV_32FC3, 1.0 / 255.0)

            val warped = Mat()
            Imgproc.remap(srcF, warped, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))
            Core.add(accColor, warped, accColor)
            Core.add(accWeight, mapMask, accWeight)
        }

        val avg = Mat()
        Core.divide(accColor, accWeight, avg, 1.0, CvType.CV_32FC3)
        val result = Mat()
        avg.convertTo(result, CvType.CV_8UC3)
        val invalid = Mat()
        Core.compare(accWeight, Scalar(0.01), invalid, Core.CMP_LE)
        result.setTo(Scalar(0.0), invalid)

        return result
    }
}
