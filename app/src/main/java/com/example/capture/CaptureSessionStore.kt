package com.example.capture

import android.util.AtomicFile
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.stitching.Direction
import com.example.stitching.SliceSource
import com.example.stitching.AlignmentResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Sidecars keep the original geometry alongside app-owned JPEGs for reproducible diagnosis. */
object CaptureSessionStore {
    fun writeAlignment(directory: File, result: AlignmentResult, sources: List<SliceSource>) {
        val json = JSONObject().put("version", 1).put("matchedPairs", result.matchedPairs)
            .put("candidatePairs", result.candidatePairs).put("supportedPhotos", JSONArray(result.supportedPhotos.sorted()))
            .put("maximumCorrectionDegrees", result.maximumCorrectionDegrees)
            .put("sources", JSONArray(sources.map { it.file.absolutePath }))
            .put("photos", JSONArray(result.poses.indices.map { i ->
                JSONObject().put("index", i).put("gain", JSONArray(result.gains[i].toList()))
                    .put("pose", JSONArray(listOf(result.poses[i].right, result.poses[i].up, result.poses[i].forward).map {
                        JSONArray(listOf(it.x, it.y, it.z))
                    }))
            }))
        File(directory, "alignment.json").writeText(json.toString(2))
    }

    fun metadataFile(jpeg: File) = File(jpeg.parentFile, "${jpeg.nameWithoutExtension}.json")

    fun write(source: SliceSource, target: CaptureTarget) {
        val k = source.intrinsics
        val json = JSONObject().put("version", 1).put("image", source.file.name)
            .put("capturedAt", System.currentTimeMillis())
            .put("pose", JSONArray(listOf(source.pose.right, source.pose.up, source.pose.forward).map {
                JSONArray(listOf(it.x, it.y, it.z))
            }))
            .put("intrinsics", JSONArray(listOf(k.width, k.height, k.fx, k.fy, k.cx, k.cy)))
            .put("target", JSONArray(listOf(target.azimuth, target.elevation, target.band)))
        val atomic = AtomicFile(metadataFile(source.file))
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    fun read(jpeg: File): SliceSource {
        val json = JSONObject(metadataFile(jpeg).readText())
        require(json.getInt("version") == 1 && json.getString("image") == jpeg.name)
        val basis = json.getJSONArray("pose")
        fun direction(index: Int): Direction = basis.getJSONArray(index).let {
            Direction(it.getDouble(0), it.getDouble(1), it.getDouble(2))
        }
        val k = json.getJSONArray("intrinsics")
        return SliceSource(jpeg, CameraPose(direction(0), direction(1), direction(2)),
            CameraIntrinsics(k.getInt(0), k.getInt(1), k.getDouble(2), k.getDouble(3), k.getDouble(4), k.getDouble(5)))
    }
}
