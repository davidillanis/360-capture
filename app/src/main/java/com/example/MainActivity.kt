package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearError()
                    }
                }

                // Hardware back press navigation
                BackHandler(enabled = uiState.screenState != ScreenState.CAPTURE) {
                    when (uiState.screenState) {
                        ScreenState.VIEWER -> viewModel.openCapture()
                        ScreenState.GALLERY -> viewModel.openCapture()
                        ScreenState.PROCESSING -> viewModel.openCapture()
                        ScreenState.CAPTURE -> {}
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = BackgroundDark
                    ) {
                    Crossfade(
                        targetState = uiState.screenState,
                        label = "screen_transition"
                    ) { state ->
                        when (state) {
                            ScreenState.CAPTURE -> {
                                CaptureScreen(
                                    currentSlice = uiState.currentSlice,
                                    totalSlices = uiState.totalSlices,
                                    coveredCells = uiState.coveredCells,
                                    aimCell = uiState.aimCell,
                                    isSphereComplete = uiState.isSphereComplete,
                                    isCapturing = uiState.isCapturing,
                                    captureStatusText = uiState.captureStatusText,
                                    evValue = uiState.evValue,
                                    isHdr = uiState.isHdr,
                                    isoMode = uiState.isoMode,
                                    selectedEngine = uiState.stitchingEngine,
                                    selectedProjection = uiState.projectionMode,
                                    recentThumbnailUrl = uiState.recentThumbnailUrl,
                                    deviceRoll = uiState.orientation.roll,
                                    devicePitch = uiState.orientation.pitch,
                                    hasCameraPermission = viewModel.hasCameraPermission(),
                                    onCameraReady = viewModel::attachCamera,
                                    onCameraDispose = viewModel::detachCamera,
                                    onEvChange = viewModel::setEv,
                                    onHdrToggle = viewModel::toggleHdr,
                                    onIsoCycle = viewModel::cycleIso,
                                    onEngineSelected = viewModel::setEngine,
                                    onProjectionSelected = viewModel::setProjection,
                                    onShutterClick = viewModel::onShutterClick,
                                    onGalleryClick = viewModel::openGallery
                                )
                            }
                            ScreenState.PROCESSING -> {
                                ProcessingScreen(
                                    stitchingProgress = uiState.stitchingProgress,
                                    engine = uiState.stitchingEngine,
                                    totalImages = uiState.totalSlices,
                                    currentStepText = uiState.stitchingStepText,
                                    sliceThumbnails = uiState.sliceThumbnails
                                )
                            }
                            ScreenState.VIEWER -> {
                                ViewerScreen(
                                    panoramaPath = uiState.activePanoramaPath,
                                    deviceAzimuth = uiState.orientation.azimuth,
                                    devicePitch = uiState.orientation.pitch,
                                    onBackClick = viewModel::openCapture,
                                    onSaveToGallery = viewModel::saveToGallery,
                                    onRetakeClick = viewModel::retake
                                )
                            }
                            ScreenState.GALLERY -> {
                                GalleryScreen(
                                    savedPanoramas = uiState.savedPanoramas,
                                    onSelectPanorama = viewModel::selectPanoramaFromGallery,
                                    onDeletePanorama = viewModel::deletePanorama,
                                    onBackToCapture = viewModel::openCapture
                                )
                            }
                        }
                    }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}
