package com.example.stitching

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.Feature2D
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Stitches a sequence of images into a single panorama using ORB features,
 * pairwise homographies, perspective warping and alpha-feathered accumulation.
 * Images must be supplied as RGBA Mats, roughly ordered by rotation.
 */
class FeatureStitcher(
    private val maxFeatures: Int = 1200,
    private val minMatches: Int = 16,
    private val minInliers: Int = 12,
    private val maxCanvasWidth: Int = 8000,
    private val maxCanvasArea: Long = 6_000_000
) {

    private val orb: ORB by lazy { ORB.create(maxFeatures) }
    private val matcher: DescriptorMatcher by lazy {
        DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
    }

    /**
     * @return the stitched panorama (RGB) or null when feature matching failed.
     */
    fun stitch(rgbaImages: List<Mat>): Mat? {
        if (rgbaImages.isEmpty()) return null
        if (rgbaImages.size == 1) {
            val out = Mat()
            Imgproc.cvtColor(rgbaImages[0], out, Imgproc.COLOR_RGBA2RGB)
            return out
        }

        // 1) Compute absolute homographies: H_abs[i] maps image i into the canvas.
        val homographies = mutableListOf<Mat>()
        homographies.add(Mat.eye(3, 3, CvType.CV_64F))
        for (i in 0 until rgbaImages.size - 1) {
            val hRel = estimateHomography(rgbaImages[i], rgbaImages[i + 1]) ?: return null
            val hAcc = Mat()
            Core.gemm(homographies[i], hRel, 1.0, Mat(), 0.0, hAcc, 0)
            homographies.add(hAcc)
        }

        val reference = rgbaImages[0]
        for (h in homographies) {
            if (!isSane(h, reference)) return null
        }

        // 2) Compute canvas bounds by warping image corners.
        val bounds = computeBounds(rgbaImages, homographies)
            ?: return null
        val width = (bounds[2] - bounds[0]).toInt()
        val height = (bounds[3] - bounds[1]).toInt()
        if (width <= 0 || height <= 0 || width > maxCanvasWidth || height > 2 * reference.height()) {
            return null
        }
        if (width.toLong() * height > maxCanvasArea) return null

        // 3) Shift all homographies so the canvas starts at (0,0).
        val shift = Mat.eye(3, 3, CvType.CV_64F).apply {
            put(0, 2, -bounds[0])
            put(1, 2, -bounds[1])
        }
        val shifted = homographies.map { h ->
            val out = Mat()
            Core.gemm(shift, h, 1.0, Mat(), 0.0, out, 0)
            out
        }

        // 4) Warp + accumulate blend.
        return blendIntoCanvas(rgbaImages, shifted, Size(width.toDouble(), height.toDouble()))
    }

    private fun estimateHomography(a: Mat, b: Mat): Mat? {
        val grayA = Mat()
        val grayB = Mat()
        Imgproc.cvtColor(a, grayA, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(b, grayB, Imgproc.COLOR_RGBA2GRAY)

        val kpA = MatOfKeyPoint()
        val kpB = MatOfKeyPoint()
        val descA = Mat()
        val descB = Mat()

        orb.detectAndCompute(grayA, Mat(), kpA, descA)
        orb.detectAndCompute(grayB, Mat(), kpB, descB)

        if (descA.rows() < minMatches || descB.rows() < minMatches) return null

        val knn = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(descA, descB, knn, 2)

        val kpAarr = kpA.toArray()
        val kpBarr = kpB.toArray()

        val good = ArrayList<DMatch>(knn.size)
        for (pair in knn) {
            val ms = pair.toArray()
            if (ms.size < 2) continue
            if (ms[0].distance < 0.78f * ms[1].distance) {
                good.add(ms[0])
            }
        }
        if (good.size < minMatches) return null

        val src = Array(good.size) { kpAarr[good[it].queryIdx].pt }
        val dst = Array(good.size) { kpBarr[good[it].trainIdx].pt }
        val srcMat = MatOfPoint2f(*src)
        val dstMat = MatOfPoint2f(*dst)

        val mask = Mat()
        val h = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, 3.0, mask)
        val inliers = Core.countNonZero(mask)
        if (inliers < minInliers) return null
        if (!isSane(h, a)) return null
        return h
    }

    /** Rejects degenerate homographies with non-finite or wildly displaced mappings. */
    private fun isSane(h: Mat, ref: Mat): Boolean {
        val hd = h.get(0, 0) to h.get(2, 2)
        if (!hd.first[0].isFinite()) return false
        val det = Core.determinant(h)
        if (!det.isFinite() || abs(det) < 1e-6) return false

        val w = ref.cols().toDouble()
        val hh = ref.rows().toDouble()
        val corners = arrayOf(
            Point(0.0, 0.0), Point(w, 0.0), Point(0.0, hh), Point(w, hh)
        )
        val src = MatOfPoint2f(*corners)
        val dst = MatOfPoint2f()
        Core.perspectiveTransform(src, dst, h)
        val limit = 25.0 * maxOf(w, hh)
        for (p in dst.toArray()) {
            if (!p.x.isFinite() || !p.y.isFinite()) return false
            if (abs(p.x) > limit || abs(p.y) > limit) return false
        }
        return true
    }

    /** Warps the image corners and returns [minX, minY, maxX, maxY]. */
    private fun computeBounds(images: List<Mat>, homographies: List<Mat>): DoubleArray? {
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        for ((i, img) in images.withIndex()) {
            val w = img.cols().toDouble()
            val h = img.rows().toDouble()
            val corners = arrayOf(
                Point(0.0, 0.0), Point(w, 0.0), Point(0.0, h), Point(w, h)
            )
            val src = MatOfPoint2f(*corners)
            val dst = MatOfPoint2f()
            Core.perspectiveTransform(src, dst, homographies[i])
            for (p in dst.toArray()) {
                minX = minOf(minX, p.x)
                minY = minOf(minY, p.y)
                maxX = maxOf(maxX, p.x)
                maxY = maxOf(maxY, p.y)
            }
        }
        if (maxX - minX <= 0 || maxY - minY <= 0) return null
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    /** Warps every image into the canvas and feathered-averages overlapping regions. */
    private fun blendIntoCanvas(images: List<Mat>, homographies: List<Mat>, canvas: Size): Mat {
        val accColor = Mat(canvas, CvType.CV_32FC3, Scalar(0.0))
        val accWeight = Mat(canvas, CvType.CV_32F, Scalar(0.0))
        val white = Mat(images[0].rows(), images[0].cols(), CvType.CV_8UC1, Scalar(255.0))

        for ((i, img) in images.withIndex()) {
            val rgb = Mat()
            Imgproc.cvtColor(img, rgb, Imgproc.COLOR_RGBA2RGB)
            val srcF = Mat()
            rgb.convertTo(srcF, CvType.CV_32FC3, 1.0 / 255.0)

            val warped = Mat()
            Imgproc.warpPerspective(srcF, warped, homographies[i], canvas, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))

            val maskW = Mat()
            Imgproc.warpPerspective(white, maskW, homographies[i], canvas, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))
            val maskF = Mat()
            maskW.convertTo(maskF, CvType.CV_32F, 1.0 / 255.0)

            Core.add(accColor, warped, accColor)
            Core.add(accWeight, maskF, accWeight)
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
