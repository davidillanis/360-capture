package com.example.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.IsoMode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Owns the CameraX preview + image capture pipeline for the capture screen.
 */
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var bound = false

    /** Binds preview + capture for the back camera. Must be called on the main thread. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        isoMode: IsoMode = IsoMode.AUTO,
        evCompensation: Int = 0,
        onReady: (Boolean) -> Unit = {}
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build()

                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)

                try {
                    Camera2Interop.Extender(captureBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evCompensation)
                    if (isoMode != IsoMode.AUTO) {
                        Camera2Interop.Extender(captureBuilder)
                            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoMode.sensitivity)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Camera2Interop tuning not applied", e)
                }

                val capture = captureBuilder.build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                preview.setSurfaceProvider(previewView.surfaceProvider)
                imageCapture = capture
                bound = true
                onReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
                bound = false
                onReady(false)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /** Suspends until a photo has been written to [outputFile]. */
    suspend fun takePicture(outputFile: File): Boolean {
        val capture = imageCapture ?: return false
        return suspendCancellableCoroutine { continuation ->
            val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            capture.takePicture(
                options,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture failed: ${exception.message}", exception)
                        continuation.resume(false)
                    }
                }
            )
        }
    }

    fun unbind() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        imageCapture = null
        bound = false
    }

    fun isBound(): Boolean = bound

    companion object {
        private const val TAG = "CameraController"
    }
}

private val IsoMode.sensitivity: Int
    get() = when (this) {
        IsoMode.AUTO -> 0
        IsoMode.ISO_100 -> 100
        IsoMode.ISO_200 -> 200
        IsoMode.ISO_400 -> 400
        IsoMode.ISO_800 -> 800
        IsoMode.ISO_1600 -> 1600
        IsoMode.ISO_3200 -> 3200
    }
