package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraController
import com.example.data.PanoramaRepository
import com.example.model.IsoMode
import com.example.model.ProjectionMode
import com.example.model.SavedPanorama
import com.example.model.ScreenState
import com.example.model.SphereGrid
import com.example.model.StitchingEngine
import com.example.sensor.OrientationData
import com.example.sensor.OrientationSensorManager
import com.example.stitching.PanoramaStitcher
import com.example.stitching.SliceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PanoramaUiState(
    val screenState: ScreenState = ScreenState.CAPTURE,
    val currentSlice: Int = 0,
    val totalSlices: Int = SphereGrid.TOTAL_CELLS,
    val coveredCells: Set<Int> = emptySet(),
    val aimCell: Int? = null,
    val isSphereComplete: Boolean = false,
    val isCapturing: Boolean = false,
    val captureStatusText: String = "Toma una foto apuntando a cada zona para cubrir la esfera 360°",
    val evValue: Float = 0f,
    val isHdr: Boolean = false,
    val isoMode: IsoMode = IsoMode.AUTO,
    val stitchingEngine: StitchingEngine = StitchingEngine.OPENCV,
    val projectionMode: ProjectionMode = ProjectionMode.EQUIRECTANGULAR,
    val stitchingProgress: Float = 0f,
    val stitchingStepText: String = "",
    val activePanoramaPath: String? = null,
    val recentThumbnailUrl: String? = null,
    val sliceThumbnails: List<String> = emptyList(),
    val savedPanoramas: List<SavedPanorama> = emptyList(),
    val orientation: OrientationData = OrientationData(),
    val errorMessage: String? = null
)

class PanoramaViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = OrientationSensorManager(application)
    private val repository = PanoramaRepository(application)
    val cameraController = CameraController(application)

    private val _uiState = MutableStateFlow(PanoramaUiState())
    val uiState: StateFlow<PanoramaUiState> = _uiState.asStateFlow()

    private val slices = mutableListOf<SliceSource>()
    private var captureJob: Job? = null
    private var stitchJob: Job? = null

    private var sessionPanoFile: File? = null
    private var sessionThumbFile: File? = null

    init {
        sensorManager.startListening()
        _uiState.update { it.copy(savedPanoramas = repository.loadAll()) }
        viewModelScope.launch {
            sensorManager.orientationFlow.collect { orient ->
                _uiState.update {
                    it.copy(
                        orientation = orient,
                        aimCell = SphereGrid.cellIndex(orient.azimuth, orient.pitch)
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stopListening()
        cameraController.unbind()
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun attachCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val state = _uiState.value
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            isoMode = state.isoMode,
            evCompensation = (state.evValue * 2).toInt().coerceIn(-8, 8)
        )
    }

    fun detachCamera() {
        cameraController.unbind()
    }

    // ---- Settings ----

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

    fun setEngine(engine: StitchingEngine) {
        _uiState.update { it.copy(stitchingEngine = engine) }
    }

    fun setProjection(projection: ProjectionMode) {
        _uiState.update { it.copy(projectionMode = projection) }
    }

    // ---- Capture (esfera 360°) ----

    fun onShutterClick() {
        val state = _uiState.value
        if (state.isSphereComplete) {
            finishCapture()
            return
        }
        if (!cameraController.isBound()) {
            _uiState.update { it.copy(errorMessage = "Espera a que la cámara esté lista") }
            return
        }
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, errorMessage = null) }
            if (captureSliceNow()) {
                _uiState.update { it.copy(isCapturing = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        captureStatusText = "No se pudo capturar. Inténtalo de nuevo."
                    )
                }
            }
        }
    }

    private suspend fun captureSliceNow(): Boolean {
        if (!cameraController.isBound()) return false
        val index = slices.size
        val file = File(getApplication<Application>().cacheDir, "slices/slice_${index}_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val ok = cameraController.takePicture(file)
        if (!ok) return false
        val o = _uiState.value.orientation
        val cell = SphereGrid.cellIndex(o.azimuth, o.pitch)
        slices.add(SliceSource(file = file, azimuthDeg = o.azimuth, pitchDeg = o.pitch, rollDeg = o.roll))
        val covered = _uiState.value.coveredCells + cell
        val complete = covered.size >= SphereGrid.TOTAL_CELLS
        val target = SphereGrid.nearestUncovered(covered, o.azimuth, o.pitch)
        _uiState.update {
            it.copy(
                currentSlice = slices.size,
                coveredCells = covered,
                isSphereComplete = complete,
                recentThumbnailUrl = file.toURI().toString(),
                sliceThumbnails = it.sliceThumbnails + file.toURI().toString(),
                captureStatusText = if (complete) {
                    "Esfera 360° completa. Pulsa de nuevo para crear el panorama."
                } else if (target != null) {
                    "Apunta a: ${SphereGrid.directionLabel(target)} (${covered.size}/${SphereGrid.TOTAL_CELLS})"
                } else {
                    "Captura ${covered.size}/${SphereGrid.TOTAL_CELLS}"
                }
            )
        }
        return true
    }

    private fun finishCapture() {
        captureJob?.cancel()
        _uiState.update {
            it.copy(
                isCapturing = false,
                screenState = ScreenState.PROCESSING,
                stitchingProgress = 0.01f,
                stitchingStepText = "Preparando ${slices.size} imágenes..."
            )
        }
        stitchJob?.cancel()
        stitchJob = viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val bitmap = PanoramaStitcher.stitch(
                slices = slices.toList(),
                preferCylindrical = true
            ) { progress, text ->
                _uiState.update {
                    it.copy(stitchingProgress = progress, stitchingStepText = text)
                }
            }
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.CAPTURE,
                        isCapturing = false,
                        errorMessage = "No se pudo crear el panorama. Inténtalo de nuevo."
                    )
                }
                return@launch
            }

            val id = UUID.randomUUID().toString()
            val dir = File(app.cacheDir, "session/$id")
            dir.mkdirs()
            val panoFile = File(dir, "pano.jpg")
            val thumbFile = File(dir, "thumb.jpg")

            val saved = com.example.stitching.ImageUtils.saveJpeg(bitmap, panoFile, 92)
            val thumbSaved = com.example.data.saveThumbnail(bitmap, thumbFile)
            if (!saved) {
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.CAPTURE,
                        isCapturing = false,
                        errorMessage = "No se pudo guardar el panorama."
                    )
                }
                return@launch
            }
            sessionPanoFile = panoFile
            sessionThumbFile = thumbFile

            _uiState.update {
                it.copy(
                    screenState = ScreenState.VIEWER,
                    activePanoramaPath = panoFile.absolutePath,
                    recentThumbnailUrl = if (thumbSaved) thumbFile.toURI().toString() else it.recentThumbnailUrl,
                    stitchingProgress = 1f,
                    stitchingStepText = "Panorama listo"
                )
            }
            bitmap.recycle()
        }
    }

    // ---- Gallery / navigation ----

    fun saveToGallery() {
        val panoFile = sessionPanoFile ?: return
        val thumbFile = sessionThumbFile ?: return
        val state = _uiState.value
        val panorama = SavedPanorama(
            id = UUID.randomUUID().toString(),
            title = "Panorama 360° #${state.savedPanoramas.size + 1}",
            timestamp = System.currentTimeMillis(),
            previewUrl = thumbFile.absolutePath,
            panoramaUrl = panoFile.absolutePath,
            sliceCount = slices.size,
            engine = state.stitchingEngine.label,
            location = "",
            iso = state.isoMode.label,
            ev = "EV ${state.evValue}",
            hdr = state.isHdr
        )
        val saved = repository.save(panorama, panoFile, thumbFile)
        _uiState.update { it.copy(savedPanoramas = listOf(saved) + it.savedPanoramas) }
    }

    fun retake() {
        captureJob?.cancel()
        slices.clear()
        sessionPanoFile = null
        sessionThumbFile = null
        _uiState.update {
            it.copy(
                screenState = ScreenState.CAPTURE,
                currentSlice = 0,
                coveredCells = emptySet(),
                isSphereComplete = false,
                isCapturing = false,
                stitchingProgress = 0f,
                activePanoramaPath = null,
                sliceThumbnails = emptyList(),
                errorMessage = null,
                captureStatusText = "Toma una foto apuntando a cada zona para cubrir la esfera 360°"
            )
        }
    }

    fun openGallery() {
        stopCaptureJob()
        _uiState.update { it.copy(screenState = ScreenState.GALLERY) }
    }

    fun openCapture() {
        stopCaptureJob()
        _uiState.update { it.copy(screenState = ScreenState.CAPTURE) }
    }

    fun selectPanoramaFromGallery(panorama: SavedPanorama) {
        _uiState.update {
            it.copy(
                activePanoramaPath = panorama.panoramaUrl,
                screenState = ScreenState.VIEWER
            )
        }
    }

    fun deletePanorama(id: String) {
        repository.delete(id)
        _uiState.update {
            it.copy(savedPanoramas = it.savedPanoramas.filter { p -> p.id != id })
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun stopCaptureJob() {
        captureJob?.cancel()
        stitchJob?.cancel()
        _uiState.update { it.copy(isCapturing = false) }
    }
}
