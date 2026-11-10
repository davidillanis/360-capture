package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.capture.CaptureCue
import com.example.capture.CaptureTarget
import com.example.capture.canCaptureManually
import com.example.capture.captureCue
import com.example.capture.captureStage
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.ui.components.CaptureTargetGuide
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    currentSlice: Int,
    plannedPhotos: Int,
    isCapturing: Boolean,
    isAutoCapturing: Boolean,
    captureTarget: CaptureTarget?,
    previewIntrinsics: CameraIntrinsics?,
    cameraPose: CameraPose?,
    holdProgress: Float,
    captureStatusText: String = "",
    hasCameraPermission: Boolean,
    onCameraReady: (PreviewView, LifecycleOwner) -> Unit,
    onCameraDispose: () -> Unit,
    onCaptureActive: (Boolean) -> Unit,
    onShutterClick: () -> Unit,
    onManualCapture: () -> Unit = {},
    onFinishCapture: () -> Unit,
    onResetCapture: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(hasCameraPermission) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    LaunchedEffect(permissionGranted, lifecycleOwner) {
        if (permissionGranted) onCameraReady(previewView, lifecycleOwner)
    }
    DisposableEffect(Unit) {
        onDispose { onCameraDispose() }
    }
    val activeCallback by rememberUpdatedState(onCaptureActive)
    DisposableEffect(lifecycleOwner, showHelp, permissionGranted) {
        fun updateActive() {
            permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            activeCallback(permissionGranted && !showHelp && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
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

    val started = plannedPhotos > 0
    val complete = started && captureTarget == null
    val cue = captureCue(cameraPose, captureTarget)
    var showPhotoTaken by remember { mutableStateOf(false) }
    var previousSlice by remember { mutableStateOf(currentSlice) }
    LaunchedEffect(currentSlice) {
        showPhotoTaken = currentSlice > previousSlice
        previousSlice = currentSlice
        if (showPhotoTaken) {
            delay(900)
            showPhotoTaken = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding().testTag("capture_screen_root")) {
        val landscape = maxWidth > maxHeight
        val controlsMaxHeight = maxHeight * 0.52f
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("capture_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al inicio")
                }
                Text("Foto 360°", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showHelp = true }) { Text("Ayuda") }
            }
            if (started) {
                val progress = (currentSlice.toFloat() / plannedPhotos.coerceAtLeast(1)).coerceIn(0f, if (complete) 1f else 0.99f)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(captureStage(captureTarget), Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.labelLarge)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }

            val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                Box(previewModifier.background(Color.Black), contentAlignment = Alignment.Center) {
                    if (permissionGranted) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        CaptureTargetGuide(cameraPose, captureTarget, previewIntrinsics, holdProgress)
                        if (showPhotoTaken) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                                shape = RoundedCornerShape(24.dp), color = Color(0xFF164B38)
                            ) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF83F5BF))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Foto lista", color = Color.White)
                                }
                            }
                        }
                    } else {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
                Column(
                    controlsModifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val title = when {
                        !permissionGranted -> "Activa la cámara"
                        !started -> "Apunta al frente"
                        complete -> "Fotos listas"
                        !isAutoCapturing -> "Captura en pausa"
                        isCapturing -> "Quédate quieto"
                        else -> if (cue == CaptureCue.HOLD && captureStatusText.isNotBlank()) captureStatusText else cue.title
                    }
                    val hint = when {
                        !permissionGranted -> "Necesitamos la cámara para crear tu foto 360°."
                        !started -> "Sujeta el teléfono a la altura de tus ojos. Después, sigue la flecha sin cambiar de sitio."
                        complete -> "Ya puedes crear y guardar tu foto 360°."
                        !isAutoCapturing -> "Tus tomas siguen aquí. Continúa desde donde te quedaste."
                        isCapturing -> "Tomando la foto…"
                        else -> if (captureStatusText == "Mueve el teléfono más despacio")
                            "El círculo avanzará cuando mantengas el teléfono estable." else cue.hint
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        val icon = when {
                            !permissionGranted || !started -> Icons.Default.CameraAlt
                            complete -> Icons.Default.CheckCircle
                            !isAutoCapturing -> Icons.Default.Pause
                            isCapturing -> Icons.Default.RadioButtonChecked
                            else -> cue.icon()
                        }
                        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("capture_instruction"))
                            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = {
                            when {
                                !permissionGranted -> {
                                    permissionRequested = true
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                complete -> onFinishCapture()
                                else -> onShutterClick()
                            }
                        },
                        enabled = !isCapturing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("capture_action_button")
                    ) {
                        Text(when {
                            !permissionGranted -> "Permitir cámara"
                            complete -> "Crear foto 360°"
                            isAutoCapturing -> "Pausar"
                            started -> "Continuar"
                            else -> "Empezar"
                        })
                    }
                    if (permissionGranted && started && !complete && isAutoCapturing) {
                        val canTakePhoto = canCaptureManually(cameraPose, captureTarget)
                        TextButton(
                            onClick = onManualCapture,
                            enabled = canTakePhoto && !isCapturing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual_capture_button")
                        ) { Text("Tomar foto ahora") }
                        if (!canTakePhoto) {
                            Text("Acerca el punto al círculo para habilitar la foto manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!permissionGranted && permissionRequested) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }) { Text("Abrir ajustes de permisos") }
                    }
                    if (permissionGranted && started && !isAutoCapturing && !isCapturing) {
                        if (currentSlice > 0 && !complete) {
                            TextButton(onClick = onFinishCapture, modifier = Modifier.testTag("finish_capture_button")) {
                                Text("Crear foto con lo capturado")
                            }
                        }
                        TextButton(onClick = onResetCapture) { Text("Empezar de nuevo") }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.weight(1f)) {
                    preview(Modifier.weight(1f).fillMaxSize())
                    controls(Modifier.width(300.dp))
                }
            } else {
                preview(Modifier.weight(1f).fillMaxWidth())
                controls(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight))
            }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Sigue el punto, paso a paso") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("1. Gira despacio siguiendo la flecha hasta llevar el punto amarillo al círculo central.")
                    Text("2. Quédate quieto. El círculo se llena y la foto se toma sola. Después aparece el siguiente punto.")
                    Text("3. Sigue la guía a tu alrededor, arriba y abajo. Al terminar, guardamos la foto en Mis fotos.")
                    Text("Mantén la cámara en el mismo lugar mientras giras: inclina el teléfono para mirar arriba y abajo, sin subirlo ni bajarlo.",
                        color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendido") } }
        )
    }
}

private fun CaptureCue.icon(): ImageVector = when (this) {
    CaptureCue.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
    CaptureCue.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
    CaptureCue.UP -> Icons.Default.ArrowUpward
    CaptureCue.DOWN -> Icons.Default.ArrowDownward
    CaptureCue.LEVEL -> Icons.Default.ScreenRotation
    CaptureCue.HOLD -> Icons.Default.RadioButtonChecked
    CaptureCue.WAITING -> Icons.Default.CameraAlt
}
