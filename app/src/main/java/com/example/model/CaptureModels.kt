package com.example.model

import com.example.stitching.CoverageSummary

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
    OPENCV("OpenCV · Esfera", "Proyección por orientación y óptica de cada foto, con fusión suave"),
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
    val hdr: Boolean = true,
    val coverage: CoverageSummary? = null
)

/**
 * Modela la cobertura de una esfera 360° como una rejilla de celdas
 * definida por bandas de azimut (columnas) y bandas de elevación (filas).
 * Es una visualización de cobertura, no un número fijo de fotografías.
 * Una celda se completa cuando todos sus rayos muestreados están cubiertos.
 */
object SphereGrid {

    /** Bandas de la visualización; el campo de visión se obtiene de cada cámara. */
    const val AZIMUTH_COLS = 6

    /** Número de bandas verticales (elevación). Fila 0 = arriba, fila última = abajo. */
    const val PITCH_ROWS = 4

    const val TOTAL_CELLS = AZIMUTH_COLS * PITCH_ROWS

    private const val COL_WIDTH = 360f / AZIMUTH_COLS

    fun normalizeAzimuth(azimuthDeg: Float): Float = ((azimuthDeg % 360f) + 360f) % 360f

    /** Índice de celda correspondiente a una orientación del dispositivo. */
    fun cellIndex(azimuthDeg: Float, pitchDeg: Float): Int {
        val col = (normalizeAzimuth(azimuthDeg) / COL_WIDTH).toInt().coerceIn(0, AZIMUTH_COLS - 1)
        val row = ((90f - pitchDeg.coerceIn(-90f, 90f)) / (180f / PITCH_ROWS)).toInt()
            .coerceIn(0, PITCH_ROWS - 1)
        return row * AZIMUTH_COLS + col
    }

    /** Centro de azimut de una columna en grados. */
    fun colCenter(col: Int): Float = (col + 0.5f) * COL_WIDTH

    /** Centro de elevación de una fila en grados (positivo = arriba). */
    fun rowCenter(row: Int): Float = 90f - (row + 0.5f) * (180f / PITCH_ROWS)

    /** Etiqueta legible para guiar al usuario hacia una celda. */
    fun directionLabel(cell: Int): String {
        val row = cell / AZIMUTH_COLS
        val col = cell % AZIMUTH_COLS
        val pitchLabel = when (row) {
            0 -> "arriba"
            1 -> "a media altura superior"
            2 -> "a media altura inferior"
            else -> "abajo"
        }
        val azLabel = when (col) {
            0 -> "al frente"
            1 -> "frente-derecha"
            2 -> "detrás-derecha"
            3 -> "atrás"
            4 -> "detrás-izquierda"
            else -> "frente-izquierda"
        }
        return "$pitchLabel, $azLabel"
    }

    /**
     * Celda sin cubrir más cercana a la orientación actual, usada para guiar
     * al usuario hacia dónde apuntar a continuación.
     */
    fun nearestUncovered(covered: Set<Int>, azimuthDeg: Float, pitchDeg: Float): Int? {
        var best = -1
        var bestDist = Float.MAX_VALUE
        val az = normalizeAzimuth(azimuthDeg)
        for (cell in 0 until TOTAL_CELLS) {
            if (cell in covered) continue
            val row = cell / AZIMUTH_COLS
            val col = cell % AZIMUTH_COLS
            val dAz = minOf(
                kotlin.math.abs(az - colCenter(col)),
                360f - kotlin.math.abs(az - colCenter(col))
            )
            val dPitch = kotlin.math.abs(pitchDeg - rowCenter(row))
            val dist = dAz + dPitch * 2f
            if (dist < bestDist) {
                bestDist = dist
                best = cell
            }
        }
        return if (best >= 0) best else null
    }
}
