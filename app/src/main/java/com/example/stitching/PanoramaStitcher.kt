package com.example.stitching

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File

data class SliceSource(
    val file: File,
    val azimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f
)

/**
 * Orchestrates panorama creation from a list of captured slices.
 * Prefers feature-based stitching and falls back to the sensor-based
 * cylindrical projection when features cannot be matched.
 */
object PanoramaStitcher {

    private const val STITCH_MAX_DIM = 720
    private const val EQUIRECT_HEIGHT = 1024

    private var initialized = false

    @Synchronized
    fun ensureOpenCV(): Boolean {
        if (initialized) return true
        initialized = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            Log.e(TAG, "OpenCV init failed", t)
            false
        }
        if (!initialized) Log.e(TAG, "ensureOpenCV returned false: OpenCV native library not loaded")
        return initialized
    }

    fun stitch(
        slices: List<SliceSource>,
        preferCylindrical: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Bitmap? {
        if (slices.isEmpty()) return null
        if (!ensureOpenCV()) return null

        onProgress(0.03f, "Cargando ${slices.size} imágenes...")

        val mats = ArrayList<Mat>(slices.size)
        for ((index, slice) in slices.withIndex()) {
            val decoded = ImageUtils.decodeUpright(slice.file, 2000)
            val scaled = ImageUtils.scaleDown(decoded, STITCH_MAX_DIM)
            val mat = Mat()
            Utils.bitmapToMat(scaled, mat)
            mats.add(mat)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            onProgress(0.03f + 0.12f * (index + 1) / slices.size, "Procesando imagen ${index + 1}/${slices.size}")
        }

        var pano: Mat? = null
        if (!preferCylindrical) {
            onProgress(0.18f, "Detectando puntos clave con OpenCV (ORB)...")
            try {
                pano = FeatureStitcher().stitch(mats)
            } catch (t: Throwable) {
                Log.w(TAG, "FeatureStitcher failed", t)
                pano = null
            }
        }

        val usable = !preferCylindrical &&
            pano != null &&
            pano.cols() > 0 &&
            pano.cols().toDouble() / pano.rows() > 1.3

        if (!usable) {
            if (!preferCylindrical) {
                Log.w(TAG, "FeatureStitcher unusable (null or aspect <= 1.3), falling back to CylindricalStitcher")
            }
            pano?.release()
            onProgress(0.35f, "Usando proyección cilíndrica por sensores...")
            pano = try {
                CylindricalStitcher().stitch(
                    mats.indices.map { i ->
                        StitchImage(mats[i], slices[i].azimuthDeg, slices[i].pitchDeg, slices[i].rollDeg)
                    }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "CylindricalStitcher failed", t)
                return null
            }
        }

        onProgress(0.75f, "Fusionando imágenes y generando proyección 360°...")
        val equirect = normalizeEquirect(pano)

        onProgress(0.92f, "Finalizando panorama...")
        val result = Bitmap.createBitmap(equirect.cols(), equirect.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(equirect, result)

        mats.forEach { it.release() }
        pano.release()
        equirect.release()

        onProgress(1f, "Panorama listo")
        return result
    }

    private const val TAG = "PanoramaStitcher"

    /** Resizes/crops the stitched panorama so its aspect ratio is exactly 2:1. */
    private fun normalizeEquirect(pano: Mat, targetHeight: Int = EQUIRECT_HEIGHT): Mat {
        val targetWidth = targetHeight * 2
        val srcRatio = pano.cols().toDouble() / pano.rows()
        val out = Mat(targetHeight, targetWidth, pano.type(), Scalar(0.0))
        val rect = Mat()
        if (srcRatio >= 2.0) {
            val newHeight = targetHeight
            val newWidth = (targetHeight * srcRatio).toInt()
            Imgproc.resize(pano, rect, Size(newWidth.toDouble(), newHeight.toDouble()))
            val x = (rect.cols() - targetWidth) / 2
            rect.submat(0, targetHeight, x, x + targetWidth).copyTo(out)
        } else {
            val newWidth = targetWidth
            val newHeight = (targetWidth / srcRatio).toInt()
            Imgproc.resize(pano, rect, Size(newWidth.toDouble(), newHeight.toDouble()))
            val y = (targetHeight - newHeight) / 2
            if (newHeight > 0 && y >= 0 && y + newHeight <= targetHeight) {
                rect.copyTo(out.submat(y, y + newHeight, 0, targetWidth))
            } else {
                Imgproc.resize(pano, out, Size(targetWidth.toDouble(), targetHeight.toDouble()))
            }
        }
        return out
    }
}
