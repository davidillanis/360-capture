package com.example

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ScreenState
import com.example.ui.PanoramaViewModel
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ViewerScreen
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PanoramaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                // Keep the JPEG basis and aiming geometry in the same screen orientation for a session.
                val lockCaptureRotation = uiState.plannedPhotos > 0
                DisposableEffect(lockCaptureRotation) {
                    val previous = requestedOrientation
                    if (lockCaptureRotation) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    onDispose { if (lockCaptureRotation) requestedOrientation = previous }
                }

                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearError()
                    }
                }

                // Hardware back press navigation
                BackHandler(enabled = uiState.screenState != ScreenState.HOME) {
                    viewModel.goBack()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = BackgroundDark
                    ) {
                        when (uiState.screenState) {
                            ScreenState.HOME -> {
                                HomeScreen(
                                    savedCount = uiState.savedPanoramas.size,
                                    hasPendingCapture = uiState.hasPendingCapture,
                                    onCreatePhoto = viewModel::retake,
                                    onContinueCapture = viewModel::openCapture,
                                    onOpenGallery = viewModel::openGallery
                                )
                            }
                            ScreenState.CAPTURE -> {
                                CaptureScreen(
                                    currentSlice = uiState.currentSlice,
                                    plannedPhotos = uiState.plannedPhotos,
                                    isCapturing = uiState.isCapturing,
                                    isAutoCapturing = uiState.isAutoCapturing,
                                    captureTarget = uiState.captureTarget,
                                    previewIntrinsics = uiState.previewIntrinsics,
                                    cameraPose = uiState.orientation.pose,
                                    holdProgress = uiState.holdProgress,
                                    captureStatusText = uiState.captureStatusText,
                                    onCaptureActive = viewModel::setCaptureActive,
                                    hasCameraPermission = viewModel.hasCameraPermission(),
                                    onCameraReady = viewModel::attachCamera,
                                    onCameraDispose = viewModel::detachCamera,
                                    onShutterClick = viewModel::onShutterClick,
                                    onManualCapture = viewModel::onManualCapture,
                                    onFinishCapture = viewModel::finishCapture,
                                    onResetCapture = viewModel::retake,
                                    onBackClick = viewModel::openHome
                                )
                            }
                            ScreenState.PROCESSING -> {
                                ProcessingScreen(
                                    stitchingProgress = uiState.stitchingProgress,
                                    isSaving = uiState.isSaving,
                                    onBackToCapture = viewModel::openCapture
                                )
                            }
                            ScreenState.VIEWER -> {
                                ViewerScreen(
                                    panoramaPath = uiState.activePanoramaPath,
                                    deviceAzimuth = uiState.orientation.azimuth,
                                    devicePitch = uiState.orientation.pitch,
                                    isSaved = uiState.savedPanoramas.any { it.panoramaUrl == uiState.activePanoramaPath },
                                    isSaving = uiState.isSaving,
                                    canContinueCapture = uiState.canContinueCapture,
                                    coverage = uiState.activeCoverage,
                                    onBackClick = viewModel::openGallery,
                                    onRetrySave = viewModel::saveToGallery,
                                    onNewCapture = viewModel::retake,
                                    onContinueCapture = viewModel::openCapture
                                )
                            }
                            ScreenState.GALLERY -> {
                                GalleryScreen(
                                    savedPanoramas = uiState.savedPanoramas,
                                    onSelectPanorama = viewModel::selectPanoramaFromGallery,
                                    onDeletePanorama = viewModel::deletePanorama,
                                    onBackClick = viewModel::openHome,
                                    onCreatePhoto = viewModel::retake
                                )
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}
