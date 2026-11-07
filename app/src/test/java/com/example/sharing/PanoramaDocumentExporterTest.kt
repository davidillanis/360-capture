package com.example.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.stitching.CoverageSummary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PanoramaDocumentExporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `export adds valid photo sphere metadata without recompressing or changing the source`() {
        val source = jpeg()
        val original = source.readBytes()
        val coverage = CoverageSummary(1.0, 0, 320, 160, true, true)
        val document = PanoramaDocumentExporter(context.cacheDir).export(source, coverage)
        try {
            assertTrue(document.name.startsWith("Panorama_360_"))
            assertTrue(document.name.endsWith(".jpg"))
            assertArrayEquals(original, source.readBytes())
            assertArrayEquals(jpegScan(original), jpegScan(document.readBytes()))
            val description = description(document)
            assertEquals("equirectangular", description.getAttributeNS(GPANO, "ProjectionType"))
            assertEquals("True", description.getAttributeNS(GPANO, "UsePanoramaViewer"))
            assertEquals("320", description.getAttributeNS(GPANO, "FullPanoWidthPixels"))
            assertEquals("160", description.getAttributeNS(GPANO, "FullPanoHeightPixels"))
            assertEquals("320", description.getAttributeNS(GPANO, "CroppedAreaImageWidthPixels"))
            assertEquals("160", description.getAttributeNS(GPANO, "CroppedAreaImageHeightPixels"))
            assertEquals("0", description.getAttributeNS(GPANO, "CroppedAreaLeftPixels"))
            assertEquals("0", description.getAttributeNS(GPANO, "CroppedAreaTopPixels"))
            assertEquals("complete", description.getAttributeNS(CAPTURE, "CoverageStatus"))
        } finally {
            source.delete()
            document.delete()
        }
    }

    @Test
    fun `partial and unknown coverage are not advertised as complete`() {
        val source = jpeg()
        val exporter = PanoramaDocumentExporter(context.cacheDir)
        val partial = exporter.export(source, CoverageSummary(0.75, 12800, 320, 160, false, false))
        val unknown = exporter.export(source)
        val mismatched = exporter.export(source, CoverageSummary(1.0, 0, 640, 320, true, true))
        try {
            assertTrue(partial.name.contains("_parcial_"))
            val metadata = description(partial)
            assertEquals("partial", metadata.getAttributeNS(CAPTURE, "CoverageStatus"))
            assertEquals("0.75", metadata.getAttributeNS(CAPTURE, "CoverageFraction"))
            assertEquals("12800", metadata.getAttributeNS(CAPTURE, "MissingPixels"))
            assertEquals("unknown", description(unknown).getAttributeNS(CAPTURE, "CoverageStatus"))
            assertEquals("unknown", description(mismatched).getAttributeNS(CAPTURE, "CoverageStatus"))
        } finally {
            listOf(source, partial, unknown, mismatched).forEach { it.delete() }
        }
    }

    @Test
    fun `invalid or missing images are rejected instead of sharing a broken document`() {
        val exporter = PanoramaDocumentExporter(context.cacheDir)
        val wrongRatio = jpeg(width = 160, height = 160)
        val invalid = File.createTempFile("invalid-pano", ".jpg", context.cacheDir).apply { writeText("invalid") }
        val missing = File(context.cacheDir, "missing-panorama.jpg")
        try {
            for (source in listOf(wrongRatio, invalid, missing)) {
                assertThrows(IOException::class.java) { exporter.export(source) }
            }
        } finally {
            wrongRatio.delete()
            invalid.delete()
        }
    }

    private fun jpeg(width: Int = 320, height: Int = 160): File {
        val file = File.createTempFile("source-pano", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            bitmap.setPixel(width / 2, height / 2, Color.RED)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun description(file: File): Element {
        val xmp = requireNotNull(ExifInterface(file).getAttribute(ExifInterface.TAG_XMP))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmp.toByteArray(Charsets.UTF_8)))
        return document.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "Description").item(0) as Element
    }

    /** Compare the original compressed scan, not a decoded bitmap or just file dimensions. */
    private fun jpegScan(bytes: ByteArray): ByteArray {
        assertEquals(0xff, bytes[0].toInt() and 0xff)
        assertEquals(0xd8, bytes[1].toInt() and 0xff)
        var offset = 2
        while (offset + 3 < bytes.size) {
            assertEquals(0xff, bytes[offset].toInt() and 0xff)
            if ((bytes[offset + 1].toInt() and 0xff) == 0xda) return bytes.copyOfRange(offset, bytes.size)
            val length = ((bytes[offset + 2].toInt() and 0xff) shl 8) or (bytes[offset + 3].toInt() and 0xff)
            offset += length + 2
        }
        error("JPEG scan not found")
    }

    companion object {
        private const val GPANO = "http://ns.google.com/photos/1.0/panorama/"
        private const val CAPTURE = "urn:360capture:metadata:1.0"
    }
}
