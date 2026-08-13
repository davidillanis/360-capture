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
import com.example.model.StitchingEngine
import com.example.sensor.OrientationData
import com.example.sensor.OrientationSensorManager
import com.example.stitching.PanoramaStitcher
import com.example.stitching.SliceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PanoramaUiState(
    val screenState: ScreenState = ScreenState.CAPTURE,
    val currentSlice: Int = 0,
    val totalSlices: Int = 10,
    val isCapturing: Boolean = false,
    val isAutoCapture: Boolean = true,
    val captureStatusText: String = "Gira lentamente para capturar 360°",
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

    private var startAzimuth = 0f
    private var sessionPanoFile: File? = null
    private var sessionThumbFile: File? = null

    private val angleStep: Float
        get() = 360f / _uiState.value.totalSlices

    init {
        sensorManager.startListening()
        _uiState.update { it.copy(savedPanoramas = repository.loadAll()) }
        viewModelScope.launch {
            sensorManager.orientationFlow.collect { orient ->
                _uiState.update { it.copy(orientation = orient) }
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

    fun toggleAutoCapture(enabled: Boolean) {
        _uiState.update { it.copy(isAutoCapture = enabled) }
    }

    fun setEngine(engine: StitchingEngine) {
        _uiState.update { it.copy(stitchingEngine = engine) }
    }

    fun setProjection(projection: ProjectionMode) {
        _uiState.update { it.copy(projectionMode = projection) }
    }

    // ---- Capture ----

    fun onShutterClick() {
        val state = _uiState.value
        if (state.isAutoCapture && state.isCapturing) {
            stopCapture()
            return
        }
        if (state.isAutoCapture) {
            startAutoCapture()
        } else {
            captureManual()
        }
    }

    private fun startAutoCapture() {
        if (!cameraController.isBound()) {
            _uiState.update { it.copy(errorMessage = "Espera a que la cámara esté lista") }
            return
        }
        slices.clear()
        startAzimuth = _uiState.value.orientation.unwrappedAzimuth
        _uiState.update {
            it.copy(
                isCapturing = true,
                currentSlice = 0,
                errorMessage = null,
                captureStatusText = "Foto 1 tomada. Gira lentamente 360°..."
            )
        }
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            var failures = 0
            if (!captureSliceNow()) failures++
            while (isActive && slices.size < _uiState.value.totalSlices) {
                delay(80)
                val rel = (_uiState.value.orientation.unwrappedAzimuth - startAzimuth).coerceAtLeast(0f)
                val target = minOf(_uiState.value.totalSlices, (rel / angleStep).toInt() + 1)
                while (isActive && slices.size < target) {
                    if (captureSliceNow()) {
                        failures = 0
                    } else {
                        failures++
                        if (failures > 5) {
                            stopCapture()
                            return@launch
                        }
                        delay(300)
                    }
                }
            }
            if (slices.size >= _uiState.value.totalSlices) {
                finishCapture()
            }
        }
    }

    private fun captureManual() {
        if (slices.size >= _uiState.value.totalSlices) return
        if (!cameraController.isBound()) {
            _uiState.update { it.copy(errorMessage = "Espera a que la cámara esté lista") }
            return
        }
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, errorMessage = null) }
            val ok = captureSliceNow()
            if (ok && slices.size >= _uiState.value.totalSlices) {
                finishCapture()
            } else {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        captureStatusText = if (ok) "Captura ${slices.size}/${it.totalSlices}. Gira y toca el botón." else "No se pudo capturar. Inténtalo de nuevo."
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
        val relAzimuth = (o.unwrappedAzimuth - startAzimuth).coerceAtLeast(0f)
        slices.add(SliceSource(file = file, azimuthDeg = relAzimuth, pitchDeg = o.pitch, rollDeg = o.roll))
        _uiState.update {
            it.copy(
                currentSlice = slices.size,
                recentThumbnailUrl = file.toURI().toString(),
                sliceThumbnails = it.sliceThumbnails + file.toURI().toString(),
                captureStatusText = "Captura ${slices.size}/${it.totalSlices}. Sigue girando..."
            )
        }
        return true
    }

    private fun stopCapture() {
        captureJob?.cancel()
        _uiState.update { it.copy(isCapturing = false) }
        if (slices.size >= 3) {
            finishCapture()
        } else {
            slices.clear()
            _uiState.update {
                it.copy(
                    currentSlice = 0,
                    captureStatusText = "Toma al menos 3 fotos para crear un panorama."
                )
            }
        }
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
            val bitmap = PanoramaStitcher.stitch(slices.toList()) { progress, text ->
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
                isCapturing = false,
                stitchingProgress = 0f,
                activePanoramaPath = null,
                sliceThumbnails = emptyList(),
                errorMessage = null,
                captureStatusText = "Gira lentamente para capturar 360°"
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
