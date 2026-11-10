package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.IsoMode
import com.example.model.ProjectionMode
import com.example.model.StitchingEngine
import com.example.ui.components.ExposureDialog
import com.example.ui.components.CaptureTargetGuide
import com.example.capture.CaptureTarget
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.ui.components.ImmersiveAtmosphereBackground
import com.example.ui.components.SettingsDialog
import com.example.ui.components.ShutterControls
import com.example.ui.components.SphereCoverageGrid
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.Secondary
import com.example.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    currentSlice: Int,
    totalSlices: Int,
    coveredCells: Set<Int>,
    aimCell: Int?,
    isSphereComplete: Boolean,
    isCapturing: Boolean,
    captureStatusText: String,
    evValue: Float,
    isHdr: Boolean,
    isoMode: IsoMode,
    selectedEngine: StitchingEngine,
    selectedProjection: ProjectionMode,
    recentThumbnailUrl: String?,
    deviceRoll: Float,
    devicePitch: Float,
    hasCameraPermission: Boolean,
    onCameraReady: (PreviewView, LifecycleOwner) -> Unit,
    onCameraDispose: () -> Unit,
    onEvChange: (Float) -> Unit,
    onHdrToggle: () -> Unit,
    onIsoCycle: () -> Unit,
    onEngineSelected: (StitchingEngine) -> Unit,
    onProjectionSelected: (ProjectionMode) -> Unit,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    coverageFraction: Double = 0.0,
    deviceAzimuth: Float = 0f,
    onFinishCapture: () -> Unit = {},
    onResetCapture: () -> Unit = {},
    isAutoCapturing: Boolean = false,
    captureTarget: CaptureTarget? = null,
    previewIntrinsics: CameraIntrinsics? = null,
    cameraPose: CameraPose? = null,
    holdProgress: Float = 0f,
    onCaptureActive: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExposureDialog by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(hasCameraPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            onCameraReady(previewView, lifecycleOwner)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onCameraDispose() }
    }

    val activeCallback by rememberUpdatedState(onCaptureActive)
    DisposableEffect(lifecycleOwner, showSettingsDialog, showExposureDialog, permissionGranted) {
        fun updateActive() = activeCallback(permissionGranted && !showSettingsDialog && !showExposureDialog &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> updateActive() }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateActive()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeCallback(false)
        }
    }

    DisposableEffect(previewView) {
        previewView.keepScreenOn = true
        onDispose { previewView.keepScreenOn = false }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("capture_screen_root")
    ) {
        // Camera Live Preview
        if (permissionGranted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundDark),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Se necesita permiso de cámara",
                        color = Secondary,
                        style = Typography.bodyMedium
                    )
                }
            }
        }

        // Immersive atmospheric aura overlay
        ImmersiveAtmosphereBackground()

        // Subtle gradient overlays for high UI legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC1C1B1F),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xE61C1B1F)
                        )
                    )
                )
        )

        // Top Navigation Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(GlassBackground)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("capture_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Secondary
                )
            }

            Text(
                text = "360Capture",
                style = Typography.titleMedium,
                color = ImmersiveLilac,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp
            )

            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("capture_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Secondary
                )
            }
        }

        // Sphere coverage grid at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            SphereCoverageGrid(
                coveredCells = coveredCells,
                aimCell = aimCell
            )
        }

        // Capture status text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = captureStatusText,
                color = ImmersiveLilac,
                style = Typography.bodyMedium,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        CaptureTargetGuide(cameraPose, captureTarget, previewIntrinsics, holdProgress)

        // Bottom Controls Area (Thumb-Zone)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%.1f%% de cobertura estimada · %d fotos".format(coverageFraction * 100, currentSlice),
                color = ImmersiveLilac,
                style = Typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(
                    onClick = onResetCapture,
                    enabled = currentSlice > 0 && !isCapturing
                ) { Text("Reiniciar") }
                TextButton(
                    onClick = onFinishCapture,
                    enabled = currentSlice > 0 && !isCapturing,
                    modifier = Modifier.testTag("finish_capture_button")
                ) { Text(if (isSphereComplete) "Ver panorama" else "Ver panorama parcial") }
            }

            TextButton(onClick = { showExposureDialog = true }, enabled = !isCapturing) {
                Text("Exposición %+.1f EV · enfoque automático".format(evValue))
            }
            Text(
                text = "Mantén la cámara en el mismo punto al girar",
                color = Secondary,
                style = Typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = if (isAutoCapturing) "Pausar" else if (currentSlice > 0) "Reanudar" else "Iniciar",
                color = ImmersiveLilac,
                style = Typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ShutterControls(
                recentThumbnailUrl = recentThumbnailUrl.orEmpty(),
                coveredCount = coveredCells.size,
                totalCells = totalSlices,
                isSphereComplete = isSphereComplete,
                onThumbnailClick = onGalleryClick,
                onShutterClick = onShutterClick,
                isCapturing = isCapturing,
                isAutoCapturing = isAutoCapturing
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                selectedEngine = selectedEngine,
                selectedProjection = selectedProjection,
                onEngineSelected = {
                    onEngineSelected(it)
                    showSettingsDialog = false
                },
                onProjectionSelected = {
                    onProjectionSelected(it)
                    showSettingsDialog = false
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Exposure Adjust Dialog
        if (showExposureDialog) {
            ExposureDialog(
                currentEv = evValue,
                onEvChange = onEvChange,
                onDismiss = { showExposureDialog = false }
            )
        }
    }
}
