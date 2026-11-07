package com.example.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Compose wrapper around the OpenGL equirectangular viewer.
 */
@Composable
fun EquirectangularViewer(
    panoramaFile: File?,
    azimuthDeg: Float,
    pitchDeg: Float,
    sensorEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = remember { EquirectGLSurfaceView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.setPanorama(null)
            view.onPause()
        }
    }

    LaunchedEffect(panoramaFile) {
        var bitmap: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { bitmap = panoramaFile?.let { loadBitmap(it) } }
            view.setPanorama(bitmap)
            bitmap = null // The GL thread now owns the bitmap.
        } finally {
            bitmap?.recycle()
        }
    }

    LaunchedEffect(azimuthDeg, pitchDeg, sensorEnabled) {
        view.setSensor(azimuthDeg, pitchDeg, sensorEnabled)
    }

    AndroidView(
        factory = { view },
        modifier = modifier.fillMaxSize()
    )
}

private fun loadBitmap(file: File): Bitmap? {
    return try {
        val bmp = com.example.stitching.ImageUtils.decodeUpright(file, 4096)
        bmp
    } catch (t: Throwable) {
        null
    }
}
