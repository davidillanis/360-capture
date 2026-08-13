package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

                // Hardware back press navigation
                BackHandler(enabled = uiState.screenState != ScreenState.CAPTURE) {
                    when (uiState.screenState) {
                        ScreenState.VIEWER -> viewModel.openCapture()
                        ScreenState.GALLERY -> viewModel.openCapture()
                        ScreenState.PROCESSING -> viewModel.openCapture()
                        ScreenState.CAPTURE -> {}
                    }
                }

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
                                    evValue = uiState.evValue,
                                    isHdr = uiState.isHdr,
                                    isoMode = uiState.isoMode,
                                    isAutoCapture = uiState.isAutoCapture,
                                    selectedEngine = uiState.stitchingEngine,
                                    selectedProjection = uiState.projectionMode,
                                    recentThumbnailUrl = uiState.recentThumbnailUrl,
                                    deviceRoll = uiState.orientation.roll,
                                    devicePitch = uiState.orientation.pitch,
                                    onEvChange = viewModel::setEv,
                                    onHdrToggle = viewModel::toggleHdr,
                                    onIsoCycle = viewModel::cycleIso,
                                    onAutoCaptureToggle = viewModel::toggleAutoCapture,
                                    onEngineSelected = viewModel::setEngine,
                                    onProjectionSelected = viewModel::setProjection,
                                    onShutterClick = viewModel::captureSlice,
                                    onGalleryClick = viewModel::openGallery
                                )
                            }
                            ScreenState.PROCESSING -> {
                                ProcessingScreen(
                                    stitchingProgress = uiState.stitchingProgress,
                                    engine = uiState.stitchingEngine,
                                    totalImages = uiState.totalSlices,
                                    currentStepText = uiState.stitchingStepText
                                )
                            }
                            ScreenState.VIEWER -> {
                                ViewerScreen(
                                    panoramaUrl = uiState.activePanoramaUrl,
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
            }
        }
    }
}
