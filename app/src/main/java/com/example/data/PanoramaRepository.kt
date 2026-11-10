package com.example.data

import android.content.Context
import android.util.AtomicFile
import com.example.model.SavedPanorama
import com.example.stitching.ImageUtils
import com.example.stitching.CoverageSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists saved panoramas under filesDir/panoramas with an index.json catalog.
 */
class PanoramaRepository(context: Context) {

    private val root = File(context.filesDir, "panoramas")

    init {
        root.mkdirs()
    }

    @Synchronized
    fun loadAll(): List<SavedPanorama> {
        val index = File(root, "index.json")
        return try {
            val array = JSONArray(AtomicFile(index).openRead().bufferedReader().use { it.readText() })
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        SavedPanorama(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            timestamp = o.getLong("timestamp"),
                            previewUrl = o.getString("preview"),
                            panoramaUrl = o.getString("pano"),
                            sliceCount = o.getInt("slices"),
                            engine = o.getString("engine"),
                            location = o.optString("location"),
                            iso = o.optString("iso"),
                            ev = o.optString("ev"),
                            hdr = o.optBoolean("hdr"),
                            coverage = o.optJSONObject("coverage")?.let { coverage ->
                                CoverageSummary(
                                    fraction = coverage.getDouble("fraction"),
                                    missingPixels = coverage.getInt("missingPixels"),
                                    width = coverage.getInt("width"),
                                    height = coverage.getInt("height"),
                                    zenithCovered = coverage.getBoolean("zenithCovered"),
                                    nadirCovered = coverage.getBoolean("nadirCovered")
                                )
                            }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Copies the session panorama + thumbnail into permanent storage and registers it. */
    @Synchronized
    fun save(panorama: SavedPanorama, panoSource: File, thumbSource: File): SavedPanorama {
        val dir = File(root, panorama.id)
        dir.mkdirs()
        // A completed capture can replace its partial result. Keep the old files valid until
        // both new files and the catalog have been committed, including when storage is full.
        val version = UUID.randomUUID().toString()
        val panoDest = File(dir, "pano_$version.jpg")
        val thumbDest = File(dir, "thumb_$version.jpg")

        val saved = panorama.copy(
            previewUrl = thumbDest.absolutePath,
            panoramaUrl = panoDest.absolutePath
        )

        val entries = loadAll()
        try {
            panoSource.copyTo(panoDest)
            thumbSource.copyTo(thumbDest)
            writeIndex(listOf(saved) + entries.filterNot { it.id == saved.id })
        } catch (error: Exception) {
            panoDest.delete()
            thumbDest.delete()
            throw error
        }
        entries.firstOrNull { it.id == saved.id }?.let { previous ->
            listOf(previous.panoramaUrl, previous.previewUrl).map(::File)
                .filter { it.parentFile == dir }.forEach { it.delete() }
        }
        return saved
    }

    @Synchronized
    fun delete(id: String) {
        val remaining = loadAll().filter { it.id != id }
        writeIndex(remaining)
        File(root, id).deleteRecursively()
    }

    private fun writeIndex(panoramas: List<SavedPanorama>) {
        val array = JSONArray()
        for (p in panoramas) {
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("timestamp", p.timestamp)
                    put("preview", p.previewUrl)
                    put("pano", p.panoramaUrl)
                    put("slices", p.sliceCount)
                    put("engine", p.engine)
                    put("location", p.location)
                    put("iso", p.iso)
                    put("ev", p.ev)
                    put("hdr", p.hdr)
                    p.coverage?.let { coverage ->
                        put("coverage", JSONObject().apply {
                            put("fraction", coverage.fraction)
                            put("missingPixels", coverage.missingPixels)
                            put("width", coverage.width)
                            put("height", coverage.height)
                            put("zenithCovered", coverage.zenithCovered)
                            put("nadirCovered", coverage.nadirCovered)
                        })
                    }
                }
            )
        }
        val index = AtomicFile(File(root, "index.json"))
        val output = index.startWrite()
        try {
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            index.finishWrite(output)
        } catch (error: Exception) {
            index.failWrite(output)
            throw error
        }
    }

    companion object {
        fun thumbnailFile(context: Context): File {
            val dir = File(context.filesDir, "thumbnails")
            dir.mkdirs()
            return File(dir, "saved_${System.currentTimeMillis()}.jpg")
        }
    }
}

/** Convenience: rebuilds a small thumbnail bitmap from a panorama bitmap. */
fun thumbnailOf(source: android.graphics.Bitmap, maxEdge: Int = 480): android.graphics.Bitmap {
    val longest = maxOf(source.width, source.height)
    if (longest <= maxEdge) return source
    val scale = maxEdge.toFloat() / longest
    return android.graphics.Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt(),
        (source.height * scale).toInt(),
        true
    )
}

/** Saves an already-scaled bitmap as the app gallery thumbnail. */
fun saveThumbnail(source: android.graphics.Bitmap, file: File): Boolean {
    val scaled = thumbnailOf(source)
    val ok = ImageUtils.saveJpeg(scaled, file, 82)
    if (scaled !== source) scaled.recycle()
    return ok
}
