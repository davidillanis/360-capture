package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.SavedPanorama
import com.example.stitching.CoverageSummary
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PanoramaRepositoryTest {
    @Test
    fun `completing a partial photo updates one gallery entry and a failed retry preserves it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PanoramaRepository(context)
        val source = File.createTempFile("capture", ".jpg", context.cacheDir).apply { writeText("partial photo") }
        val metadata = SavedPanorama("auto-save-test", "Foto", previewUrl = "", panoramaUrl = "")
        try {
            val partial = repository.save(metadata, source, source)
            source.writeText("complete photo")
            val complete = repository.save(metadata.copy(sliceCount = 40), source, source)
            assertEquals(1, repository.loadAll().count { it.id == metadata.id })
            assertEquals(40, repository.loadAll().single { it.id == metadata.id }.sliceCount)
            assertEquals("complete photo", File(complete.panoramaUrl).readText())
            assertFalse(File(partial.panoramaUrl).exists())

            source.writeText("incomplete write")
            try {
                repository.save(metadata, source, File(context.cacheDir, "missing-thumbnail.jpg"))
                fail("A missing thumbnail must fail the save")
            } catch (_: java.io.IOException) {
                assertEquals(complete, repository.loadAll().single { it.id == metadata.id })
                assertEquals("complete photo", File(complete.panoramaUrl).readText())
            }
        } finally {
            repository.delete(metadata.id)
            source.delete()
        }
    }

    @Test
    fun `coverage survives saving and old panoramas without coverage still load`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PanoramaRepository(context)
        val source = File.createTempFile("pano", ".jpg", context.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val coverage = CoverageSummary(0.75, 200, 40, 20, true, false)
        val panorama = SavedPanorama("coverage-test", "Test", previewUrl = "", panoramaUrl = "", coverage = coverage)
        try {
            repository.save(panorama, source, source)
            assertEquals(coverage, repository.loadAll().single { it.id == panorama.id }.coverage)

            val index = File(context.filesDir, "panoramas/index.json")
            val entries = JSONArray(index.readText())
            for (i in 0 until entries.length()) entries.getJSONObject(i).remove("coverage")
            index.writeText(entries.toString())
            assertNull(repository.loadAll().single { it.id == panorama.id }.coverage)
        } finally {
            repository.delete(panorama.id)
            source.delete()
        }
    }
}
