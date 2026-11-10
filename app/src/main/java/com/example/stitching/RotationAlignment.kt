package com.example.stitching

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Proper 3-D rotation (no translation, scale or planar warping). */
class Rotation3 private constructor(private val m: DoubleArray) {
    fun apply(v: Direction) = Direction(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z
    )

    fun apply(pose: CameraPose) = CameraPose(apply(pose.right), apply(pose.up), apply(pose.forward))

    operator fun times(other: Rotation3): Rotation3 = Rotation3(DoubleArray(9) { index ->
        val row = index / 3
        val col = index % 3
        (0..2).sumOf { m[row * 3 + it] * other.m[it * 3 + col] }
    })

    fun inverse() = Rotation3(DoubleArray(9) { m[(it % 3) * 3 + it / 3] })

    fun log(): Direction {
        val angle = acos(((m[0] + m[4] + m[8] - 1) / 2).coerceIn(-1.0, 1.0))
        val factor = if (angle < 1e-7) 0.5 else angle / (2 * sin(angle))
        return Direction(m[7] - m[5], m[2] - m[6], m[3] - m[1]) * factor
    }

    val degrees: Double get() = Math.toDegrees(log().length())

    companion object {
        fun identity() = Rotation3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        fun exp(vector: Direction): Rotation3 {
            val angle = vector.length()
            if (angle < 1e-12) return identity()
            val n = vector * (1 / angle)
            val c = cos(angle)
            val s = sin(angle)
            val t = 1 - c
            return Rotation3(doubleArrayOf(
                c + n.x * n.x * t, n.x * n.y * t - n.z * s, n.x * n.z * t + n.y * s,
                n.y * n.x * t + n.z * s, c + n.y * n.y * t, n.y * n.z * t - n.x * s,
                n.z * n.x * t - n.y * s, n.z * n.y * t + n.x * s, c + n.z * n.z * t
            ))
        }
    }
}

internal fun Direction.cross(other: Direction) = Direction(
    y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x
)
internal fun Direction.length() = sqrt(dot(this))
internal operator fun Direction.minus(other: Direction) = this + other * -1.0

data class RayMatch(val from: Direction, val to: Direction)
data class RotationFit(val rotation: Rotation3, val inliers: List<Int>, val medianErrorDegrees: Double)
data class RotationEdge(val a: Int, val b: Int, val bToA: Rotation3, val weight: Double)

object RotationAlignment {
    /** Small-angle Wahba fit. Sensor priors limit the search to < 15°, avoiding ambiguous wide-baseline matches. */
    fun fit(matches: List<RayMatch>): Rotation3? {
        if (matches.size < 3) return null
        var rotation = Rotation3.identity()
        repeat(7) {
            val normal = DoubleArray(9)
            var rhs = Direction(0.0, 0.0, 0.0)
            for (match in matches) {
                val ray = rotation.apply(match.from)
                rhs += ray.cross(match.to)
                val v = doubleArrayOf(ray.x, ray.y, ray.z)
                for (r in 0..2) for (c in 0..2) normal[r * 3 + c] += (if (r == c) 1.0 else 0.0) - v[r] * v[c]
            }
            val delta = solve(normal, doubleArrayOf(rhs.x, rhs.y, rhs.z)) ?: return null
            rotation = Rotation3.exp(Direction(delta[0], delta[1], delta[2])) * rotation
        }
        return rotation
    }

    /** Reject outliers before refining; a failed fit leaves the sensor pose untouched. */
    fun robust(matches: List<RayMatch>, checkCancelled: () -> Unit = {}): RotationFit? {
        if (matches.size < 18) return null
        val random = Random(360)
        val threshold = 2 * sin(Math.toRadians(0.65) / 2)
        fun inliers(rotation: Rotation3) = matches.indices.filter {
            (rotation.apply(matches[it].from) - matches[it].to).length() < threshold
        }
        var best = emptyList<Int>()
        repeat(180) {
            if (it % 20 == 0) checkCancelled()
            val indices = List(3) { random.nextInt(matches.size) }.distinct()
            if (indices.size != 3) return@repeat
            val sample = indices.map { matches[it] }
            if ((sample[0].from - sample[1].from).length() < 0.06) return@repeat
            val rotation = fit(sample) ?: return@repeat
            if (rotation.degrees > 15) return@repeat
            val consensus = inliers(rotation)
            if (consensus.size > best.size) best = consensus
        }
        if (best.size < maxOf(18, (matches.size * 0.3).toInt())) return null
        var refined = fit(best.map { matches[it] }) ?: return null
        best = inliers(refined)
        if (best.size < 18) return null
        refined = fit(best.map { matches[it] }) ?: return null
        val errors = best.map { Math.toDegrees(acos(refined.apply(matches[it].from).dot(matches[it].to).coerceIn(-1.0, 1.0))) }.sorted()
        if (refined.degrees > 15 || errors[errors.size / 2] > 0.35) return null
        // A tiny patch cannot constrain all three rotation axes reliably.
        val mean = best.map { matches[it].from }.reduce { a, b -> a + b } * (1.0 / best.size)
        if (best.sumOf { (matches[it].from - mean).dot(matches[it].from - mean) } / best.size < 0.004) return null
        return RotationFit(refined, best, errors[errors.size / 2])
    }

    /** Optimize all overlapping pairs together, including wrap-around and cross-elevation edges. */
    fun optimize(count: Int, edges: List<RotationEdge>, checkCancelled: () -> Unit = {}): List<Rotation3> {
        val corrections = MutableList(count) { Rotation3.identity() }
        val neighbors = List(count) { i -> edges.filter { it.a == i || it.b == i } }
        val anchored = BooleanArray(count)
        val visited = BooleanArray(count)
        for (root in 0 until count) {
            if (visited[root]) continue
            anchored[root] = true
            val queue = ArrayDeque<Int>()
            queue.add(root)
            visited[root] = true
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                for (edge in neighbors[node]) {
                    val next = if (node == edge.a) edge.b else edge.a
                    if (!visited[next]) { visited[next] = true; queue.add(next) }
                }
            }
        }
        repeat(100) {
            checkCancelled()
            for (i in 0 until count) {
                if (anchored[i] || neighbors[i].isEmpty()) continue
                // Very weak prior regularizes uncertain chains without defeating loop closure.
                var delta = corrections[i].inverse().log() * 0.002
                var weights = 0.002
                for (edge in neighbors[i]) {
                    val desired = if (i == edge.b) corrections[edge.a] * edge.bToA else
                        corrections[edge.b] * edge.bToA.inverse()
                    val residual = (desired * corrections[i].inverse()).log()
                    val weight = edge.weight * minOf(1.0, Math.toRadians(2.0) / residual.length().coerceAtLeast(1e-9))
                    delta += residual * weight
                    weights += weight
                }
                val candidate = Rotation3.exp(delta * (0.7 / weights)) * corrections[i]
                if (candidate.degrees <= 20) corrections[i] = candidate
            }
        }
        return corrections
    }

    private fun solve(matrix: DoubleArray, rhs: DoubleArray): DoubleArray? {
        val a = Array(3) { row -> DoubleArray(4) { col -> if (col == 3) rhs[row] else matrix[row * 3 + col] } }
        for (col in 0..2) {
            val pivot = (col..2).maxBy { abs(a[it][col]) }
            val temp = a[col]; a[col] = a[pivot]; a[pivot] = temp
            if (abs(a[col][col]) < 1e-8) return null
            val divisor = a[col][col]
            for (j in col..3) a[col][j] /= divisor
            for (row in 0..2) if (row != col) {
                val scale = a[row][col]
                for (j in col..3) a[row][j] -= scale * a[col][j]
            }
        }
        return DoubleArray(3) { a[it][3] }
    }
}
