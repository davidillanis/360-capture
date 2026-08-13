package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.IsoMode
import com.example.model.ProjectionMode
import com.example.model.SavedPanorama
import com.example.model.ScreenState
import com.example.model.StitchingEngine
import com.example.sensor.OrientationData
import com.example.sensor.OrientationSensorManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class PanoramaUiState(
    val screenState: ScreenState = ScreenState.CAPTURE,
    val currentSlice: Int = 3, // Starting at 3 to match the 3/10 progress dot mockup
    val totalSlices: Int = 10,
    val evValue: Float = 0.0f,
    val isHdr: Boolean = true,
    val isoMode: IsoMode = IsoMode.AUTO,
    val isAutoCapture: Boolean = true,
    val stitchingEngine: StitchingEngine = StitchingEngine.OPENCV,
    val projectionMode: ProjectionMode = ProjectionMode.EQUIRECTANGULAR,
    val stitchingProgress: Float = 0.45f,
    val stitchingStepText: String = "Processing 12 images with OpenCV engine",
    val activePanoramaUrl: String = "https://lh3.googleusercontent.com/aida-public/AB6AXuAv0m2IPXVGkwY20N8dcvPvTQSrocww2lZBLjAnPO9yWQ1spsGcWf4xrbDCbS-Poyt91GOHO7VCpVxAiD5RmAKPlsbpUvaJtQ1o2WWKkkOMMiQXNUsKQp_L7McaeZjK6K9UhEa_B4j9oP_ZlGv1r5as-tTH6qyRmXi43pZfoo8xbXmXMh-CC1Nwo-EJzYdmLBbjbBkdx1ezs-MfDlrhR2HsOOtH7EXPChbSuPtGcBHFw2sGTgW3J9T6",
    val recentThumbnailUrl: String = "https://lh3.googleusercontent.com/aida-public/AB6AXuBxj8hbxYTVAII79ATJsJHCwJl82HOb4CfwAsfJCQ4zXgH78IoImJg91aMzwRE4IWY_1jWp_EK8pwd100CTjAgrqOCG96Btdj56ijNCHFXOhFR5pORdngprh7RNZEjtuxsXLreRPAZxCUvwLP4NmR1VjzPyfvuY3a5AFE-4TUiJ2ob4m9nzfkCahcCURBrkGzmKWhG6TXqlggbB9vOICDvCdtHr-MFPY-fLYdRY5Ih_wzOIw0EmA2Ys",
    val savedPanoramas: List<SavedPanorama> = listOf(
        SavedPanorama(
            id = "pano-default-1",
            title = "Dolomites Peak Panorama",
            timestamp = System.currentTimeMillis() - 86400000L,
            previewUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBxj8hbxYTVAII79ATJsJHCwJl82HOb4CfwAsfJCQ4zXgH78IoImJg91aMzwRE4IWY_1jWp_EK8pwd100CTjAgrqOCG96Btdj56ijNCHFXOhFR5pORdngprh7RNZEjtuxsXLreRPAZxCUvwLP4NmR1VjzPyfvuY3a5AFE-4TUiJ2ob4m9nzfkCahcCURBrkGzmKWhG6TXqlggbB9vOICDvCdtHr-MFPY-fLYdRY5Ih_wzOIw0EmA2Ys",
            panoramaUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAv0m2IPXVGkwY20N8dcvPvTQSrocww2lZBLjAnPO9yWQ1spsGcWf4xrbDCbS-Poyt91GOHO7VCpVxAiD5RmAKPlsbpUvaJtQ1o2WWKkkOMMiQXNUsKQp_L7McaeZjK6K9UhEa_B4j9oP_ZlGv1r5as-tTH6qyRmXi43pZfoo8xbXmXMh-CC1Nwo-EJzYdmLBbjbBkdx1ezs-MfDlrhR2HsOOtH7EXPChbSuPtGcBHFw2sGTgW3J9T6",
            sliceCount = 10,
            engine = "OpenCV 4.8 Pro",
            location = "Tre Cime di Lavaredo, 2,999m",
            iso = "ISO 100",
            ev = "EV 0.0",
            hdr = true
        )
    ),
    val orientation: OrientationData = OrientationData()
)

class PanoramaViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = OrientationSensorManager(application)

    private val _uiState = MutableStateFlow(PanoramaUiState())
    val uiState: StateFlow<PanoramaUiState> = _uiState.asStateFlow()

    private var stitchingJob: Job? = null

    init {
        sensorManager.startListening()
        viewModelScope.launch {
            sensorManager.orientationFlow.collect { orient ->
                _uiState.update { it.copy(orientation = orient) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stopListening()
    }

    fun setEv(ev: Float) {
        _uiState.update { it.copy(evValue = ev) }
    }

    fun toggleHdr() {
        _uiState.update { it.copy(isHdr = !it.isHdr) }
    }

    fun cycleIso() {
        _uiState.update { current ->
            val modes = IsoMode.values()
            val nextIndex = (current.isoMode.ordinal + 1) % modes.size
            current.copy(isoMode = modes[nextIndex])
        }
    }

    fun toggleAutoCapture(enabled: Boolean) {
        _uiState.update { it.copy(isAutoCapture = enabled) }
    }

    fun setEngine(engine: StitchingEngine) {
        _uiState.update { it.copy(stitchingEngine = engine) }
    }

    fun setProjection(projection: ProjectionMode) {
        _uiState.update { it.copy(projectionMode = projection) }
    }

    fun captureSlice() {
        val nextSlice = _uiState.value.currentSlice + 1
        if (nextSlice >= _uiState.value.totalSlices) {
            _uiState.update { it.copy(currentSlice = it.totalSlices) }
            startStitchingProcess()
        } else {
            _uiState.update { it.copy(currentSlice = nextSlice) }
        }
    }

    fun startStitchingProcess() {
        stitchingJob?.cancel()
        _uiState.update {
            it.copy(
                screenState = ScreenState.PROCESSING,
                stitchingProgress = 0.05f,
                stitchingStepText = "Extracting keypoints with ${it.stitchingEngine.label}..."
            )
        }

        stitchingJob = viewModelScope.launch {
            val steps = listOf(
                Pair(0.20f, "Extracting feature points & homographies..."),
                Pair(0.45f, "Processing 12 images with ${_uiState.value.stitchingEngine.label}"),
                Pair(0.70f, "Multiband seam blending & exposure compensation..."),
                Pair(0.90f, "Generating ${_uiState.value.projectionMode.label} map..."),
                Pair(1.00f, "Finalizing 360° RAW rendering...")
            )

            for ((progress, text) in steps) {
                delay(900)
                _uiState.update {
                    it.copy(
                        stitchingProgress = progress,
                        stitchingStepText = text
                    )
                }
            }
            delay(500)
            _uiState.update {
                it.copy(screenState = ScreenState.VIEWER)
            }
        }
    }

    fun saveToGallery() {
        val newPano = SavedPanorama(
            id = UUID.randomUUID().toString(),
            title = "Alpine Sunset 360° #${_uiState.value.savedPanoramas.size + 1}",
            timestamp = System.currentTimeMillis(),
            previewUrl = _uiState.value.recentThumbnailUrl,
            panoramaUrl = _uiState.value.activePanoramaUrl,
            sliceCount = _uiState.value.totalSlices,
            engine = _uiState.value.stitchingEngine.label,
            location = "Cortina d'Ampezzo, 2,240m",
            iso = _uiState.value.isoMode.label,
            ev = "EV ${_uiState.value.evValue}",
            hdr = _uiState.value.isHdr
        )
        _uiState.update {
            it.copy(
                savedPanoramas = listOf(newPano) + it.savedPanoramas
            )
        }
    }

    fun retake() {
        _uiState.update {
            it.copy(
                screenState = ScreenState.CAPTURE,
                currentSlice = 0,
                stitchingProgress = 0f
            )
        }
    }

    fun openGallery() {
        _uiState.update { it.copy(screenState = ScreenState.GALLERY) }
    }

    fun openCapture() {
        _uiState.update { it.copy(screenState = ScreenState.CAPTURE) }
    }

    fun selectPanoramaFromGallery(panorama: SavedPanorama) {
        _uiState.update {
            it.copy(
                activePanoramaUrl = panorama.panoramaUrl,
                screenState = ScreenState.VIEWER
            )
        }
    }

    fun deletePanorama(id: String) {
        _uiState.update {
            it.copy(savedPanoramas = it.savedPanoramas.filter { pano -> pano.id != id })
        }
    }
}
