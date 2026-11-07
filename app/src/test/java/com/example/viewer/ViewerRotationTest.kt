package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class ViewerRotationTest {
    @Test
    fun `viewer maps north east and up without a 4x4 multiplication`() {
        val matrix = FloatArray(9)
        ViewerRotation.matrix(matrix, 0f, 0f)
        assertEquals(-1f, -matrix[8], 1e-6f)
        ViewerRotation.matrix(matrix, (PI / 2).toFloat(), 0f)
        assertEquals(1f, -matrix[6], 1e-6f)
        ViewerRotation.matrix(matrix, 0f, (PI / 2).toFloat())
        assertEquals(1f, -matrix[7], 1e-6f)
    }

    @Test
    fun `sensor interpolation crosses 359 to 1 by two degrees`() {
        val delta = ViewerRotation.shortestDelta(Math.toRadians(359.0).toFloat(), Math.toRadians(1.0).toFloat())
        assertEquals(Math.toRadians(2.0).toFloat(), delta, 1e-6f)
    }
}
