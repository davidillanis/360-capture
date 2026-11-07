package com.example.viewer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object ViewerRotation {
    /** Column-major 3x3: north is -Z in GL, east is +X, elevation is +Y. */
    fun matrix(out: FloatArray, yaw: Float, pitch: Float) {
        require(out.size == 9)
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        out[0] = cy; out[1] = 0f; out[2] = sy
        out[3] = -sy * sp; out[4] = cp; out[5] = cy * sp
        out[6] = -sy * cp; out[7] = -sp; out[8] = cy * cp
    }

    fun shortestDelta(from: Float, to: Float): Float = atan2(sin(to - from), cos(to - from))
}
