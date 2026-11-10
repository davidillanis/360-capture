package com.example.stitching

import com.example.model.SphereGrid
import kotlin.math.PI
import kotlin.math.cos

data class CoverageSummary(
    val fraction: Double,
    val missingPixels: Int,
    val width: Int,
    val height: Int,
    val zenithCovered: Boolean,
    val nadirCovered: Boolean
) {
    val isComplete: Boolean get() = missingPixels == 0
    val label: String
        get() = if (isComplete) "Cobertura 360° × 180° completa" else
            "Cobertura %.1f%% · faltan zonas%s%s".format(
                fraction * 100.0,
                if (zenithCovered) "" else " · cenit",
                if (nadirCovered) "" else " · nadir"
            )
}

/** A sampled visibility mask, not a count of shutter presses. */
class SphericalCoverage(val width: Int = 180, val height: Int = 90) {
    private val covered = BooleanArray(width * height)

    init {
        require(width > 0 && height > 0)
    }

    fun copy(): SphericalCoverage = SphericalCoverage(width, height).also { covered.copyInto(it.covered) }

    fun add(pose: CameraPose, intrinsics: CameraIntrinsics) {
        // Reserve edges for overlap. The final rendered report uses the actual per-pixel mask.
        val margin = minOf(intrinsics.width, intrinsics.height) * 0.06
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!covered[index]) {
                    covered[index] = SphericalGeometry.project(
                        SphericalGeometry.ray(x, y, width, height), pose, intrinsics, margin
                    ) != null
                }
            }
        }
    }

    fun setRow(y: Int, weights: FloatArray) {
        require(weights.size == width)
        for (x in weights.indices) covered[y * width + x] = weights[x] > 0f
    }

    fun summary(): CoverageSummary {
        var missing = 0
        var coveredArea = 0.0
        var totalArea = 0.0
        for (y in 0 until height) {
            val area = cos((0.5 - (y + 0.5) / height) * PI)
            for (x in 0 until width) {
                totalArea += area
                if (covered[y * width + x]) coveredArea += area else missing++
            }
        }
        return CoverageSummary(
            coveredArea / totalArea, missing, width, height,
            (0 until width).all { covered[it] },
            (0 until width).all { covered[(height - 1) * width + it] }
        )
    }

    fun coveredCells(): Set<Int> {
        val missingCells = mutableSetOf<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!covered[y * width + x]) {
                    val ray = SphericalGeometry.ray(x, y, width, height)
                    missingCells += SphereGrid.cellIndex(ray.azimuth.toFloat(), ray.elevation.toFloat())
                }
            }
        }
        return (0 until SphereGrid.TOTAL_CELLS).filterNot { it in missingCells }.toSet()
    }

    fun nearestMissing(forward: Direction): Direction? {
        var target: Direction? = null
        var bestDot = -Double.MAX_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (covered[y * width + x]) continue
                val ray = SphericalGeometry.ray(x, y, width, height)
                val dot = forward.dot(ray)
                if (dot > bestDot) {
                    target = ray
                    bestDot = dot
                }
            }
        }
        return target
    }
}
