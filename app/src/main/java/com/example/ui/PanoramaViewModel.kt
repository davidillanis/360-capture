package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraController
import com.example.capture.CaptureTarget
import com.example.capture.CaptureSessionStore
import com.example.capture.GuidedCaptureGate
import com.example.capture.GuidedCapturePlanner
import com.example.capture.angularDistance
import com.example.capture.canCaptureManually
import com.example.capture.captureCue
import com.example.capture.poseDistance
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
import com.example.stitching.CoverageSummary
import com.example.stitching.SphericalCoverage
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PanoramaUiState(
    val screenState: ScreenState = ScreenState.HOME,
    val currentSlice: Int = 0,
    val totalSlices: Int = SphereGrid.TOTAL_CELLS,
    val coveredCells: Set<Int> = emptySet(),
    val aimCell: Int? = null,
    val isSphereComplete: Boolean = false,
    val coverageFraction: Double = 0.0,
    val activeCoverage: CoverageSummary? = null,
    val alignmentText: String? = null,
    val isCapturing: Boolean = false,
    val isAutoCapturing: Boolean = false,
    val captureTarget: CaptureTarget? = null,
    val previewIntrinsics: CameraIntrinsics? = null,
    val holdProgress: Float = 0f,
    val plannedPhotos: Int = 0,
    val hasPendingCapture: Boolean = false,
    val canContinueCapture: Boolean = false,
    val isSaving: Boolean = false,
    val captureStatusText: String = "Apunta al frente y pulsa Empezar. Las fotos se toman solas.",
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
    private var coverage = SphericalCoverage()
    private var captureJob: Job? = null
    private var stitchJob: Job? = null
    private val captureGate = GuidedCaptureGate()
    private var targets = emptyList<CaptureTarget>()
    private var targetIndex = 0
    private var captureActive = false
    private var captureGeneration = 0
    private var exposureAnchor: CameraPose? = null
    private var exposureMotionSamples = 0
    private var movedDuringCapture = false
    private var captureDirectory: File? = null

    private var sessionPanoFile: File? = null
    private var sessionThumbFile: File? = null
    private var sessionPanorama: SavedPanorama? = null

    init {
        sensorManager.startListening()
        _uiState.update { it.copy(savedPanoramas = repository.loadAll()) }
        viewModelScope.launch {
            sensorManager.orientationFlow.collect { orient ->
                _uiState.update {
                    it.copy(
                        orientation = orient,
                        aimCell = if (orient.pose == null) null else SphereGrid.cellIndex(orient.azimuth, orient.pitch)
                    )
                }
                val pose = orient.pose
                exposureAnchor?.let { anchor ->
                    val rawPose = orient.rawPose
                    if (rawPose == null) movedDuringCapture = true
                    else {
                        exposureMotionSamples = if (poseDistance(anchor, rawPose) > 2.5) exposureMotionSamples + 1 else 0
                        if (exposureMotionSamples >= 2) movedDuringCapture = true
                    }
                }
                val state = _uiState.value
                val target = state.captureTarget
                if (captureActive && state.isAutoCapturing && !state.isCapturing &&
                    state.screenState == ScreenState.CAPTURE && pose != null && target != null) {
                    val hold = captureGate.update(pose, target, orient.timestampNanos)
                    _uiState.update { it.copy(
                        holdProgress = hold.progress,
                        captureStatusText = if (hold.moving) "Mueve el teléfono más despacio" else captureCue(pose, target).title
                    ) }
                    if (hold.ready) captureTarget(target)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stopListening()
        cameraController.close()
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun attachCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onReady = { ready ->
                _uiState.update { it.copy(
                    previewIntrinsics = cameraController.previewIntrinsics,
                    errorMessage = if (ready) null else "No se pudo preparar la cámara. Vuelve al inicio e inténtalo otra vez."
                ) }
                if (ready) cameraController.setExposure(_uiState.value.evValue)
            }
        )
    }

    fun detachCamera() {
        setCaptureActive(false)
        cameraController.unbind()
    }

    fun setCaptureActive(active: Boolean) {
        captureActive = active
        if (!active) pauseCapture()
    }

    private fun pauseCapture() {
        captureGeneration++
        captureJob?.cancel()
        captureGate.reset()
        exposureAnchor = null
        exposureMotionSamples = 0
        _uiState.update { it.copy(isAutoCapturing = false, isCapturing = false, holdProgress = 0f,
            captureStatusText = if (targets.isEmpty()) PanoramaUiState().captureStatusText else
                "Captura en pausa. Pulsa Continuar para seguir.") }
    }

    // ---- Settings ----

    fun setEv(ev: Float) {
        pauseCapture()
        _uiState.update { it.copy(evValue = ev) }
        cameraController.setExposure(ev)
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
        if (state.screenState != ScreenState.CAPTURE || !captureActive) return
        if (state.isAutoCapturing) {
            pauseCapture()
            return
        }
        if (!cameraController.isBound()) {
            _uiState.update { it.copy(errorMessage = "Espera a que la cámara esté lista") }
            return
        }
        if (!sensorManager.isAvailable || !orientationIsFresh()) {
            _uiState.update { it.copy(errorMessage = "No podemos detectar cómo giras el teléfono. Comprueba que tenga un sensor de rotación y vuelve a intentarlo.") }
            return
        }
        if (targets.isEmpty()) {
            val intrinsics = cameraController.captureIntrinsics
            if (intrinsics == null) {
                _uiState.update { it.copy(errorMessage = "Estamos preparando la cámara. Espera un momento.") }
                return
            }
            try {
                targets = GuidedCapturePlanner.plan(intrinsics, state.orientation.azimuth.toDouble())
            } catch (_: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = "La cámara de este teléfono no es compatible con la captura guiada.") }
                return
            }
            targetIndex = 0
            captureDirectory = File(getApplication<Application>().cacheDir, "captures/${UUID.randomUUID()}")
            _uiState.update { it.copy(captureTarget = targets.first(), plannedPhotos = targets.size, hasPendingCapture = true) }
        }
        if (_uiState.value.captureTarget == null) {
            finishCapture()
            return
        }
        captureGate.reset()
        _uiState.update { it.copy(isAutoCapturing = true, errorMessage = null, holdProgress = 0f) }
    }

    private fun orientationIsFresh(): Boolean {
        val orientation = _uiState.value.orientation
        return orientation.pose != null && SystemClock.elapsedRealtimeNanos() - orientation.timestampNanos in 0..250_000_000L
    }

    fun onManualCapture() {
        val state = _uiState.value
        val target = state.captureTarget ?: return
        if (!captureActive || state.screenState != ScreenState.CAPTURE || !state.isAutoCapturing || state.isCapturing) return
        if (!orientationIsFresh() || !canCaptureManually(state.orientation.pose, target)) {
            _uiState.update { it.copy(errorMessage = "Acerca el punto al círculo y endereza el teléfono para tomar la foto.") }
            return
        }
        if (!cameraController.isBound()) {
            _uiState.update { it.copy(errorMessage = "Espera a que la cámara esté lista.") }
            return
        }
        captureGate.reset()
        captureTarget(target, manual = true)
    }

    private fun captureTarget(target: CaptureTarget, manual: Boolean = false) {
        val generation = captureGeneration
        // Do not judge motion during focus preparation (or the user's tap) as shutter blur.
        exposureAnchor = null
        exposureMotionSamples = 0
        movedDuringCapture = false
        _uiState.update { it.copy(isCapturing = true, captureStatusText = "Tomando la foto… Quédate quieto") }
        captureJob = viewModelScope.launch {
            try {
                if (!cameraController.prepareCapture(requireFocus = !manual)) {
                    pauseCapture()
                    _uiState.update {
                        it.copy(errorMessage = "No se pudo enfocar. Busca una zona con detalle o más luz y pulsa Continuar.")
                    }
                } else if (!captureSliceNow(target, manual)) {
                    _uiState.update { it.copy(errorMessage = "La toma no se guardó: hubo movimiento o la cámara no pudo capturar. Mantén el teléfono quieto e inténtalo otra vez.") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                pauseCapture()
                _uiState.update { it.copy(errorMessage = "No se pudo capturar la fotografía") }
            } finally {
                if (generation == captureGeneration) {
                    exposureAnchor = null
                    exposureMotionSamples = 0
                    captureGate.reset()
                    _uiState.update { it.copy(isCapturing = false, holdProgress = 0f) }
                }
            }
            if (generation == captureGeneration && _uiState.value.captureTarget == null && slices.isNotEmpty()) {
                // The last exposure has finished; avoid cancelling this job when pausing for processing.
                captureJob = null
                finishCapture()
            }
        }
    }

    private suspend fun captureSliceNow(target: CaptureTarget, manual: Boolean): Boolean {
        if (!cameraController.isBound() || !orientationIsFresh()) return false
        val startingPose = _uiState.value.orientation.rawPose ?: return false
        val maxError = if (manual) 7.0 else 3.5
        if (angularDistance(startingPose.forward, target.direction) > maxError) return false
        exposureAnchor = startingPose
        exposureMotionSamples = 0
        movedDuringCapture = false
        val index = slices.size
        val file = File(requireNotNull(captureDirectory), "slice_${index}_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        var accepted = false
        try {
            val photo = cameraController.takePicture(file) { rotation -> sensorManager.snapshot(rotation)?.rawPose }
                ?: return false
            if (movedDuringCapture || !orientationIsFresh() ||
                angularDistance(photo.pose.forward, target.direction) > maxError) return false
            val source = SliceSource(file, photo.pose, photo.intrinsics)
            withContext(Dispatchers.IO) { CaptureSessionStore.write(source, target) }
            val tracker = coverage.copy()
            val (summary, cells, missing) = withContext(Dispatchers.Default) {
                tracker.add(photo.pose, photo.intrinsics)
                Triple(tracker.summary(), tracker.coveredCells(), tracker.nearestMissing(photo.pose.forward))
            }
            coverage = tracker
            slices.add(source)
            accepted = true
            targetIndex++
            val next = targets.getOrNull(targetIndex) ?: missing?.let {
                CaptureTarget(it.azimuth, it.elevation, "Completar hueco")
            }
            _uiState.update {
                it.copy(
                    currentSlice = slices.size,
                    coveredCells = cells,
                    coverageFraction = summary.fraction,
                    isSphereComplete = summary.isComplete,
                    captureTarget = next,
                    plannedPhotos = maxOf(targets.size, slices.size + if (next == null) 0 else 1),
                    isAutoCapturing = it.isAutoCapturing && next != null,
                    recentThumbnailUrl = file.toURI().toString(),
                    sliceThumbnails = it.sliceThumbnails + file.toURI().toString(),
                    captureStatusText = if (next == null) {
                        "Recorrido completo. Creando tu foto…"
                    } else {
                        "${next.band} · lleva el siguiente punto al círculo"
                    }
                )
            }
            return true
        } finally {
            if (!accepted) {
                file.delete()
                CaptureSessionStore.metadataFile(file).delete()
            }
        }
    }

    fun finishCapture() {
        if (_uiState.value.isCapturing || slices.isEmpty() || stitchJob?.isActive == true) return
        pauseCapture()
        val sources = slices.toList()
        val captureState = _uiState.value
        val previousPanorama = sessionPanorama
        _uiState.update {
            it.copy(
                isCapturing = false,
                screenState = ScreenState.PROCESSING,
                stitchingProgress = 0.01f,
                isSaving = false,
                stitchingStepText = "Preparando tus fotos…"
            )
        }
        stitchJob?.cancel()
        stitchJob = viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val result = PanoramaStitcher.stitch(sources) { progress, text ->
                _uiState.update {
                    it.copy(stitchingProgress = progress, stitchingStepText = text)
                }
            }
            if (result == null) {
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.CAPTURE,
                        isCapturing = false,
                        errorMessage = "No se pudo crear la foto. Tus tomas siguen aquí; puedes intentarlo de nuevo."
                    )
                }
                return@launch
            }

            val bitmap = result.bitmap
            try {
                ensureActive()
                val id = UUID.randomUUID().toString()
                val dir = File(app.cacheDir, "session/$id")
                dir.mkdirs()
                val panoFile = File(dir, "pano.jpg")
                val thumbFile = File(dir, "thumb.jpg")

                val (saved, thumbSaved) = withContext(Dispatchers.IO) {
                    CaptureSessionStore.writeAlignment(dir, result.alignment, sources)
                    com.example.stitching.ImageUtils.saveJpeg(bitmap, panoFile, 92) to
                        com.example.data.saveThumbnail(bitmap, thumbFile)
                }
                if (!saved || !thumbSaved) {
                    _uiState.update {
                        it.copy(
                            screenState = ScreenState.CAPTURE,
                            isCapturing = false,
                            errorMessage = "No se pudo crear el archivo de la foto. Comprueba el espacio libre e inténtalo de nuevo."
                        )
                    }
                    return@launch
                }
                val refinedCoverage = SphericalCoverage()
                sources.indices.forEach { index ->
                    ensureActive()
                    refinedCoverage.add(result.alignment.poses[index], sources[index].intrinsics)
                }
                val refinedSummary = refinedCoverage.summary()
                val refinedCells = refinedCoverage.coveredCells()
                _uiState.update { it.copy(stitchingProgress = 0.97f, stitchingStepText = "Guardando en Mis fotos…", isSaving = true) }
                val panorama = SavedPanorama(
                    id = previousPanorama?.id ?: UUID.randomUUID().toString(),
                    title = previousPanorama?.title ?: "Foto 360° #${captureState.savedPanoramas.size + 1}",
                    timestamp = previousPanorama?.timestamp ?: System.currentTimeMillis(),
                    previewUrl = thumbFile.absolutePath,
                    panoramaUrl = panoFile.absolutePath,
                    sliceCount = sources.size,
                    engine = captureState.stitchingEngine.label,
                    location = "",
                    iso = captureState.isoMode.label,
                    ev = "EV ${captureState.evValue}",
                    hdr = captureState.isHdr,
                    coverage = result.coverage
                )
                val savedPanorama = try {
                    withContext(Dispatchers.IO) { repository.save(panorama, panoFile, thumbFile) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    sessionPanoFile = panoFile
                    sessionThumbFile = thumbFile
                    sessionPanorama = panorama
                    coverage = refinedCoverage
                    val missing = coverage.nearestMissing(_uiState.value.orientation.pose?.forward ?: sources.last().pose.forward)
                    _uiState.update {
                        val target = when {
                            result.coverage.isComplete -> null
                            targetIndex < targets.size -> it.captureTarget
                            else -> missing?.let { ray -> CaptureTarget(ray.azimuth, ray.elevation, "Completar hueco") }
                        }
                        it.copy(
                            screenState = ScreenState.VIEWER,
                            activePanoramaPath = savedPanorama?.panoramaUrl ?: panoFile.absolutePath,
                            activeCoverage = result.coverage,
                            alignmentText = result.alignment.label,
                            coveredCells = refinedCells,
                            coverageFraction = refinedSummary.fraction,
                            isSphereComplete = refinedSummary.isComplete,
                            captureTarget = target,
                            hasPendingCapture = target != null,
                            canContinueCapture = target != null,
                            isSaving = false,
                            savedPanoramas = savedPanorama?.let { saved ->
                                listOf(saved) + it.savedPanoramas.filterNot { entry -> entry.id == saved.id }
                            } ?: it.savedPanoramas,
                            errorMessage = if (savedPanorama == null) "No se pudo guardar en Mis fotos. Puedes reintentar desde el resultado." else null,
                            plannedPhotos = maxOf(it.plannedPhotos, sources.size + if (target == null) 0 else 1),
                            recentThumbnailUrl = thumbFile.toURI().toString(),
                            stitchingProgress = 1f,
                            stitchingStepText = result.coverage.label
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(screenState = ScreenState.CAPTURE, isSaving = false,
                    errorMessage = "No se pudo terminar la foto. Tus tomas siguen aquí; inténtalo de nuevo.") }
            } finally {
                bitmap.recycle()
            }
        }
    }

    // ---- Gallery / navigation ----

    fun saveToGallery() {
        val panoFile = sessionPanoFile ?: return
        val thumbFile = sessionThumbFile ?: return
        val panorama = sessionPanorama ?: return
        val state = _uiState.value
        if (state.activePanoramaPath != panoFile.absolutePath || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { repository.save(panorama, panoFile, thumbFile) }
                _uiState.update { current -> current.copy(
                    activePanoramaPath = if (current.activePanoramaPath == panoFile.absolutePath) saved.panoramaUrl else current.activePanoramaPath,
                    savedPanoramas = listOf(saved) + current.savedPanoramas.filterNot { it.id == saved.id },
                    isSaving = false
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "No se pudo guardar. Comprueba que haya espacio libre y vuelve a intentarlo.") }
            }
        }
    }

    fun retake() {
        stopCaptureJob()
        slices.clear()
        targets = emptyList()
        targetIndex = 0
        captureDirectory = null
        cameraController.resetWhiteBalance()
        coverage = SphericalCoverage()
        sessionPanoFile = null
        sessionThumbFile = null
        sessionPanorama = null
        _uiState.update {
            it.copy(
                screenState = ScreenState.CAPTURE,
                currentSlice = 0,
                coveredCells = emptySet(),
                isSphereComplete = false,
                coverageFraction = 0.0,
                activeCoverage = null,
                alignmentText = null,
                isCapturing = false,
                isAutoCapturing = false,
                captureTarget = null,
                holdProgress = 0f,
                plannedPhotos = 0,
                hasPendingCapture = false,
                canContinueCapture = false,
                isSaving = false,
                stitchingProgress = 0f,
                activePanoramaPath = null,
                recentThumbnailUrl = null,
                sliceThumbnails = emptyList(),
                errorMessage = null,
                captureStatusText = PanoramaUiState().captureStatusText
            )
        }
    }

    fun openGallery() {
        stopCaptureJob()
        _uiState.update { it.copy(screenState = ScreenState.GALLERY) }
    }

    fun openHome() {
        stopCaptureJob()
        _uiState.update { it.copy(screenState = ScreenState.HOME) }
    }

    fun goBack() {
        when (_uiState.value.screenState) {
            ScreenState.HOME -> Unit
            ScreenState.CAPTURE, ScreenState.GALLERY -> openHome()
            ScreenState.VIEWER -> openGallery()
            ScreenState.PROCESSING -> if (!_uiState.value.isSaving) openCapture()
        }
    }

    fun openCapture() {
        stopCaptureJob()
        _uiState.update { it.copy(screenState = ScreenState.CAPTURE) }
    }

    fun selectPanoramaFromGallery(panorama: SavedPanorama) {
        stopCaptureJob()
        _uiState.update {
            it.copy(
                activePanoramaPath = panorama.panoramaUrl,
                activeCoverage = panorama.coverage,
                alignmentText = null,
                canContinueCapture = false,
                screenState = ScreenState.VIEWER
            )
        }
    }

    fun deletePanorama(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.delete(id) }
                _uiState.update { it.copy(savedPanoramas = it.savedPanoramas.filter { p -> p.id != id }) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "No se pudo eliminar la foto. Inténtalo de nuevo.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun stopCaptureJob() {
        pauseCapture()
        stitchJob?.cancel()
    }
}
