package com.example.stitching

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

data class ExposureFrame(val pixels: ByteArray, val intrinsics: CameraIntrinsics) {
    init { require(pixels.size == intrinsics.width * intrinsics.height * 3) }

    fun sample(x: Double, y: Double): DoubleArray {
        val k = intrinsics
        val left = x.toInt().coerceIn(0, k.width - 2)
        val top = y.toInt().coerceIn(0, k.height - 2)
        val dx = (x - left).coerceIn(0.0, 1.0)
        val dy = (y - top).coerceIn(0.0, 1.0)
        fun value(px: Int, py: Int, c: Int) = (pixels[(py * k.width + px) * 3 + c].toInt() and 255).toDouble()
        return DoubleArray(3) { c ->
            (value(left, top, c) * (1 - dx) + value(left + 1, top, c) * dx) * (1 - dy) +
                (value(left, top + 1, c) * (1 - dx) + value(left + 1, top + 1, c) * dx) * dy
        }
    }
}

data class ExposureEdge(val a: Int, val b: Int, val logRatio: DoubleArray, val weight: Double)

object ExposureCompensator {
    /** Compare the same world rays, discarding clipped samples and inconsistent overlaps. */
    fun measure(a: Int, b: Int, first: ExposureFrame, second: ExposureFrame, poseA: CameraPose, poseB: CameraPose): ExposureEdge? {
        val ratios = List(3) { mutableListOf<Double>() }
        val k = first.intrinsics
        for (y in 4 until k.height - 4 step 4) for (x in 4 until k.width - 4 step 4) {
            val ray = SphericalGeometry.cameraRay(x.toDouble(), y.toDouble(), poseA, k)
            val point = SphericalGeometry.project(ray, poseB, second.intrinsics, margin = 3.0) ?: continue
            val ca = first.sample(x.toDouble(), y.toDouble())
            val cb = second.sample(point.x, point.y)
            if (ca.any { it < 18 || it > 235 } || cb.any { it < 18 || it > 235 }) continue
            for (channel in 0..2) ratios[channel] += ln(ca[channel] / cb[channel])
        }
        if (ratios[0].size < 40) return null
        val medians = DoubleArray(3) { median(ratios[it]) }
        val spread = (0..2).maxOf { c -> median(ratios[c].map { abs(it - medians[c]) }) }
        if (spread > 0.16 || medians.any { abs(it) > ln(2.5) }) return null
        return ExposureEdge(a, b, medians, (ratios[0].size / 100.0).coerceIn(0.5, 3.0) / (1 + spread * 10))
    }

    /** Solve a common brightness/colour balance over the whole graph rather than accumulating drift. */
    fun gains(count: Int, edges: List<ExposureEdge>): List<DoubleArray> {
        val values = List(count) { DoubleArray(3) }
        val neighbors = List(count) { i -> edges.filter { it.a == i || it.b == i } }
        repeat(120) {
            for (i in 0 until count) for (channel in 0..2) {
                var sum = 0.0
                var weight = 0.03
                for (edge in neighbors[i]) {
                    val desired = if (i == edge.b) values[edge.a][channel] + edge.logRatio[channel] else
                        values[edge.b][channel] - edge.logRatio[channel]
                    sum += desired * edge.weight
                    weight += edge.weight
                }
                values[i][channel] = sum / weight
            }
        }
        // Neutral mean within each connected component; isolated images retain unit gain.
        val visited = BooleanArray(count)
        for (root in 0 until count) {
            if (visited[root]) continue
            val component = mutableListOf(root)
            visited[root] = true
            var index = 0
            while (index < component.size) {
                val node = component[index++]
                for (edge in neighbors[node]) {
                    val next = if (node == edge.a) edge.b else edge.a
                    if (!visited[next]) { visited[next] = true; component += next }
                }
            }
            for (c in 0..2) {
                val mean = component.sumOf { values[it][c] } / component.size
                component.forEach { values[it][c] -= mean }
            }
        }
        return values.map { channels -> DoubleArray(3) { exp(channels[it].coerceIn(-ln(1.6), ln(1.6))) } }
    }

    private fun median(values: List<Double>) = values.sorted()[values.size / 2]
}
