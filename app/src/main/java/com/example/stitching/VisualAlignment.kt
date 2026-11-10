package com.example.stitching

import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.hypot

data class AlignmentResult(
    val poses: List<CameraPose>,
    val gains: List<DoubleArray>,
    val matchedPairs: Int,
    val candidatePairs: Int,
    val supportedPhotos: Set<Int>,
    val maximumCorrectionDegrees: Double
) {
    val label: String get() = "Ajuste visual: ${supportedPhotos.size}/${poses.size} fotos · $matchedPairs uniones" +
        if (supportedPhotos.size < poses.size) ". Revisa las uniones sin coincidencias." else ""
}

/** ORB matches establish ray-to-ray rotations; the output remains a genuine spherical projection. */
object VisualAlignment {
    private class Frame(
        val descriptors: Mat, val rays: List<Direction>, val exposure: ExposureFrame
    ) : AutoCloseable {
        override fun close() = descriptors.release()
    }

    fun refine(sources: List<SliceSource>, checkCancelled: () -> Unit = {}, onProgress: (Float, String) -> Unit = { _, _ -> }): AlignmentResult {
        val frames = mutableListOf<Frame>()
        val detector = ORB.create(2400)
        val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
        try {
            for ((i, source) in sources.withIndex()) {
                checkCancelled()
                onProgress(i.toFloat() / sources.size * 0.35f, "Buscando detalles en foto ${i + 1}/${sources.size}…")
                frames += extract(source, detector)
            }
            val candidates = candidatePairs(sources)
            val edges = mutableListOf<RotationEdge>()
            for ((index, pair) in candidates.withIndex()) {
                checkCancelled()
                onProgress(0.35f + index.toFloat() / candidates.size.coerceAtLeast(1) * 0.5f,
                    "Alineando solapamientos ${index + 1}/${candidates.size}…")
                val (a, b) = pair
                val correspondences = match(frames[a], frames[b], matcher)
                val fit = RotationAlignment.robust(correspondences, checkCancelled) ?: continue
                edges += RotationEdge(a, b, fit.rotation,
                    (fit.inliers.size / 60.0).coerceIn(0.5, 3.0) / (1 + fit.medianErrorDegrees * 4))
            }
            onProgress(0.86f, "Cerrando la vuelta y ajustando techo y suelo…")
            val corrections = RotationAlignment.optimize(sources.size, edges, checkCancelled)
            val poses = sources.indices.map { corrections[it].apply(sources[it].pose) }
            val exposureEdges = candidates.mapNotNull { (a, b) ->
                checkCancelled()
                ExposureCompensator.measure(a, b, frames[a].exposure, frames[b].exposure, poses[a], poses[b])
            }
            onProgress(1f, "Igualando exposición y color…")
            return AlignmentResult(poses, ExposureCompensator.gains(sources.size, exposureEdges), edges.size,
                candidates.size, edges.flatMap { listOf(it.a, it.b) }.toSet(), corrections.maxOfOrNull { it.degrees } ?: 0.0)
        } finally {
            frames.forEach { it.close() }
            detector.clear()
            matcher.clear()
        }
    }

    private fun extract(source: SliceSource, detector: ORB): Frame {
        val decoded = ImageUtils.decodeUpright(source.file, 960)
        val bitmap = ImageUtils.scaleDown(decoded, 960)
        val rgba = Mat()
        val gray = Mat()
        val mask = Mat()
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        val small = Mat()
        val rgb = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            detector.detectAndCompute(gray, mask, keypoints, descriptors)
            val points = keypoints.toArray()
            val k = source.intrinsics.scaledTo(rgba.cols(), rgba.rows())
            val scale = 160.0 / maxOf(rgba.cols(), rgba.rows())
            Imgproc.resize(rgba, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, rgb, Imgproc.COLOR_RGBA2RGB)
            val pixels = ByteArray(rgb.rows() * rgb.cols() * 3)
            rgb.get(0, 0, pixels)
            return Frame(descriptors,
                points.map { SphericalGeometry.cameraRay(it.pt.x, it.pt.y, source.pose, k) },
                ExposureFrame(pixels, source.intrinsics.scaledTo(rgb.cols(), rgb.rows())))
        } catch (error: Throwable) {
            descriptors.release()
            throw error
        } finally {
            listOf(rgba, gray, mask, keypoints, small, rgb).forEach { it.release() }
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    /** Angular neighbourhood selection naturally includes the first/last photos of every ring. */
    internal fun candidatePairs(sources: List<SliceSource>): List<Pair<Int, Int>> {
        fun radius(k: CameraIntrinsics) = atan(hypot(k.width / (2 * k.fx), k.height / (2 * k.fy)))
        val distances = sources.indices.associateWith { i -> sources.indices.filter { it != i }.mapNotNull { j ->
            val angle = acos(sources[i].pose.forward.dot(sources[j].pose.forward).coerceIn(-1.0, 1.0))
            if (angle < radius(sources[i].intrinsics) + radius(sources[j].intrinsics) + Math.toRadians(10.0)) j to angle else null
        }.sortedBy { it.second }.take(10) }
        return distances.flatMap { (i, others) -> others.map { (j, _) -> minOf(i, j) to maxOf(i, j) } }
            .distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun match(a: Frame, b: Frame, matcher: BFMatcher): List<RayMatch> {
        if (a.descriptors.rows() < 2 || b.descriptors.rows() < 2) return emptyList()
        val forward = mutableListOf<MatOfDMatch>()
        val reverse = mutableListOf<MatOfDMatch>()
        try {
            matcher.knnMatch(a.descriptors, b.descriptors, forward, 2)
            matcher.knnMatch(b.descriptors, a.descriptors, reverse, 2)
            val back = reverse.mapNotNull { row -> row.toArray().takeIf { it.size == 2 && it[0].distance < it[1].distance * 0.76f }
                ?.let { it[0].queryIdx to it[0].trainIdx } }.toMap()
            return forward.mapNotNull { row ->
                val matches = row.toArray()
                if (matches.size != 2) return@mapNotNull null
                val first = matches[0]
                if (first.distance > 64 || first.distance >= matches[1].distance * 0.76f || back[first.trainIdx] != first.queryIdx) null else first
            }.sortedBy { it.distance }.take(400).map {
                RayMatch(b.rays[it.trainIdx], a.rays[it.queryIdx])
            }
        } finally {
            forward.forEach { it.release() }
            reverse.forEach { it.release() }
        }
    }
}
