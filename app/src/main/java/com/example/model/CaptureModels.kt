package com.example.model

enum class ScreenState {
    CAPTURE,
    PROCESSING,
    VIEWER,
    GALLERY
}

enum class SliceStatus {
    PENDING,
    ACTIVE,
    PROCESSED
}

enum class IsoMode(val label: String) {
    AUTO("ISO AUTO"),
    ISO_100("ISO 100"),
    ISO_200("ISO 200"),
    ISO_400("ISO 400"),
    ISO_800("ISO 800"),
    ISO_1600("ISO 1600"),
    ISO_3200("ISO 3200")
}

enum class StitchingEngine(val label: String, val subtitle: String) {
    OPENCV("OpenCV Engine", "High-precision feature matching & multiband blending"),
    NEURAL_AI("Neural AI Synthesis", "Deep generative seam fill & HDR relighting"),
    FAST_CYLINDRICAL("Fast Cylindrical", "Low latency real-time projection")
}

enum class ProjectionMode(val label: String) {
    EQUIRECTANGULAR("360° Equirectangular"),
    CYLINDRICAL("Cylindrical Panorama"),
    TINY_PLANET("Little Planet (Stereographic)")
}

data class PanoramaSlice(
    val index: Int,
    val imageUrl: String,
    val status: SliceStatus = SliceStatus.PENDING,
    val angle: Float = index * 36f
)

data class SavedPanorama(
    val id: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val previewUrl: String,
    val panoramaUrl: String,
    val sliceCount: Int = 10,
    val engine: String = "OpenCV 4.8 Pro",
    val location: String = "Dolomites Alps, 2,750m",
    val iso: String = "ISO 100",
    val ev: String = "EV 0.0",
    val hdr: Boolean = true
)
