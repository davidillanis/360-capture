package com.example.stitching

import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.junit.Assert.*
import org.junit.Before

class CylindricalStitcherTest {

    @Test
    fun testStitchWithMinimalImages() {
        val stitcher = CylindricalStitcher(canvasHeight = 100)
        // We can't easily run OpenCV in plain JUnit tests without native libs.
        // But we can look at the code for obvious bugs.
    }
}
