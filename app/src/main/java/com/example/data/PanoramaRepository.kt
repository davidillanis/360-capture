package com.example.data

import android.content.Context
import com.example.model.SavedPanorama
import com.example.stitching.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists saved panoramas under filesDir/panoramas with an index.json catalog.
 */
class PanoramaRepository(context: Context) {

    private val root = File(context.filesDir, "panoramas")

    init {
        root.mkdirs()
    }

    fun loadAll(): List<SavedPanorama> {
        val index = File(root, "index.json")
        if (!index.exists()) return emptyList()
        return try {
            val array = JSONArray(index.readText())
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
                            hdr = o.optBoolean("hdr")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Copies the session panorama + thumbnail into permanent storage and registers it. */
    fun save(panorama: SavedPanorama, panoSource: File, thumbSource: File): SavedPanorama {
        val dir = File(root, panorama.id)
        dir.mkdirs()
        val panoDest = File(dir, "pano.jpg")
        val thumbDest = File(dir, "thumb.jpg")
        panoSource.copyTo(panoDest, overwrite = true)
        thumbSource.copyTo(thumbDest, overwrite = true)

        val saved = panorama.copy(
            previewUrl = thumbDest.absolutePath,
            panoramaUrl = panoDest.absolutePath
        )

        val entries = loadAll().filter { it.id != saved.id }
        val updated = listOf(saved) + entries
        writeIndex(updated)
        return saved
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
        val remaining = loadAll().filter { it.id != id }
        writeIndex(remaining)
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
                }
            )
        }
        val index = File(root, "index.json")
        index.writeText(array.toString())
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
