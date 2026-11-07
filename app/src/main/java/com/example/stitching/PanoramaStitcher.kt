package com.example.stitching

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File

data class SliceSource(val file: File, val pose: CameraPose, val intrinsics: CameraIntrinsics)
data class PanoramaResult(val bitmap: Bitmap, val coverage: CoverageSummary)

/** Builds an actual spherical image directly on a 2:1 canvas, without cropping or stretching. */
object PanoramaStitcher {
    private const val STITCH_MAX_DIM = 1440
    private const val EQUIRECT_HEIGHT = 1024
    private const val TAG = "PanoramaStitcher"
    private var initialized = false

    @Synchronized
    fun ensureOpenCV(): Boolean {
        if (!initialized) {
            initialized = try {
                OpenCVLoader.initLocal()
            } catch (error: LinkageError) {
                Log.e(TAG, "OpenCV init failed", error)
                false
            }
        }
        return initialized
    }

    suspend fun stitch(
        slices: List<SliceSource>,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): PanoramaResult? {
        if (slices.isEmpty() || !ensureOpenCV()) return null
        val context = currentCoroutineContext()
        try {
            EquirectangularStitcher(EQUIRECT_HEIGHT).use { stitcher ->
                for ((index, slice) in slices.withIndex()) {
                    context.ensureActive()
                    onProgress(index.toFloat() / slices.size * 0.9f, "Proyectando foto ${index + 1}/${slices.size} en la esfera...")
                    val decoded = ImageUtils.decodeUpright(slice.file, STITCH_MAX_DIM)
                    var scaled: Bitmap? = null
                    val mat = Mat()
                    try {
                        scaled = ImageUtils.scaleDown(decoded, STITCH_MAX_DIM)
                        Utils.bitmapToMat(scaled, mat)
                        stitcher.add(mat, slice.pose, slice.intrinsics.scaledTo(mat.cols(), mat.rows())) {
                            context.ensureActive()
                        }
                    } finally {
                        mat.release()
                        if (scaled !== decoded) scaled?.recycle()
                        decoded.recycle()
                    }
                }
                onProgress(0.92f, "Comprobando cobertura, cenit y nadir...")
                val result = stitcher.finish { context.ensureActive() }
                try {
                    val bitmap = Bitmap.createBitmap(result.image.cols(), result.image.rows(), Bitmap.Config.ARGB_8888)
                    try {
                        Utils.matToBitmap(result.image, bitmap)
                        return PanoramaResult(bitmap, result.coverage)
                    } catch (error: Throwable) {
                        bitmap.recycle()
                        throw error
                    }
                } finally {
                    result.image.release()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Spherical stitching failed", error)
            return null
        }
    }
}
