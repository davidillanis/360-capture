package com.example.stitching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Decodes a JPEG respecting its EXIF orientation and downscales it so the
     * longest edge is at most [maxDimension].
     */
    fun decodeUpright(file: File, maxDimension: Int = 2048): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxDimension) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) { "No se pudo leer ${file.name}" }

        val rotation = exifRotation(file)
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }

    fun exifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun savePng(bitmap: Bitmap, file: File): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Horizontal FOV estimate in degrees, from EXIF 35mm focal length when available. */
    fun fovHorizontalDegrees(file: File, fallback: Float = 67f): Float {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            if (focal35 > 0) {
                (2.0 * Math.toDegrees(Math.atan(36.0 / (2.0 * focal35)))).toFloat()
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    /** Scales a bitmap so its longest edge equals [maxDimension]. */
    fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        return scaled
    }
}
