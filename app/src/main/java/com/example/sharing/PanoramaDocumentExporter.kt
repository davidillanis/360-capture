package com.example.sharing

import android.graphics.BitmapFactory
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.example.stitching.CoverageSummary
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Prepares a JPEG document with Photo Sphere XMP, without decoding/recompressing its pixels. */
class PanoramaDocumentExporter(cacheDir: File) {
    private val directory = File(cacheDir, "panorama-shares")

    @WorkerThread
    fun export(source: File, coverage: CoverageSummary? = null): File {
        if (!source.isFile || source.length() == 0L) throw IOException("No se encuentra el panorama")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (bounds.outMimeType != "image/jpeg" || height <= 0 || width.toLong() != height.toLong() * 2) {
            throw IOException("El archivo no es un panorama JPEG de proporción 2:1")
        }

        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("No se pudo preparar el documento")
        // Keep recently shared files readable while WhatsApp imports them, even after leaving the viewer.
        val expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expiry }?.forEach { it.delete() }

        val matchingCoverage = coverage?.takeIf { it.width == width && it.height == height }
        val status = when {
            matchingCoverage == null -> "unknown"
            matchingCoverage.isComplete -> "complete"
            else -> "partial"
        }
        val partial = if (status == "partial") "_parcial" else ""
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val suffix = UUID.randomUUID().toString().take(8)
        val document = File(directory, "Panorama_360${partial}_${timestamp}_$suffix.jpg")
        try {
            source.copyTo(document)
            ExifInterface(document).apply {
                setAttribute(ExifInterface.TAG_XMP, xmp(width, height, matchingCoverage, status))
                saveAttributes()
            }
            return document
        } catch (error: Exception) {
            document.delete()
            throw error
        }
    }

    private fun xmp(width: Int, height: Int, coverage: CoverageSummary?, status: String): String {
        val coverageAttributes = coverage?.let {
            "Capture360:CoverageFraction=\"${it.fraction}\" Capture360:MissingPixels=\"${it.missingPixels}\""
        }.orEmpty()
        // Even partial captures occupy the full spherical canvas; holes are black, not a rectangular crop.
        // GPano does not describe arbitrary holes. Keep coverage in a separate, application-specific namespace.
        return """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                  xmlns:GPano="http://ns.google.com/photos/1.0/panorama/"
                  xmlns:Capture360="urn:360capture:metadata:1.0"
                  GPano:UsePanoramaViewer="True"
                  GPano:ProjectionType="equirectangular"
                  GPano:CaptureSoftware="360Capture"
                  GPano:StitchingSoftware="360Capture"
                  GPano:FullPanoWidthPixels="$width"
                  GPano:FullPanoHeightPixels="$height"
                  GPano:CroppedAreaImageWidthPixels="$width"
                  GPano:CroppedAreaImageHeightPixels="$height"
                  GPano:CroppedAreaLeftPixels="0"
                  GPano:CroppedAreaTopPixels="0"
                  Capture360:CoverageStatus="$status"
                  $coverageAttributes />
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
    }
}
