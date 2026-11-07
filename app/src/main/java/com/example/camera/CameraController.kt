package com.example.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.IsoMode
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.stitching.ImageUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class CapturedPhoto(val pose: CameraPose, val intrinsics: CameraIntrinsics)

/** Owns CameraX and attaches the optical geometry of each upright JPEG. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var bound = false
    private var cameraInfo: CameraInfo? = null
    private var previewView: PreviewView? = null
    private var bindingGeneration = 0

    /** Binds preview + capture for the back camera. Must be called on the main thread. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        isoMode: IsoMode = IsoMode.AUTO,
        evCompensation: Int = 0,
        onReady: (Boolean) -> Unit = {}
    ) {
        val generation = ++bindingGeneration
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (generation != bindingGeneration) return@addListener
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
                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                cameraInfo = camera.cameraInfo
                this.previewView = previewView
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
    suspend fun takePicture(outputFile: File, orientationAtExposure: (Int) -> CameraPose?): CapturedPhoto? {
        val capture = imageCapture ?: return null
        val info = cameraInfo ?: return null
        val rotation = previewView?.display?.rotation ?: Surface.ROTATION_0
        capture.targetRotation = rotation
        val displayDegrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorRotation = info.getSensorRotationDegrees(rotation)
        return suspendCancellableCoroutine { continuation ->
            val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            var pose: CameraPose? = null
            capture.takePicture(
                options,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onCaptureStarted() {
                        pose = orientationAtExposure(displayDegrees)
                    }

                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val result = try {
                            pose?.let { CapturedPhoto(it, readIntrinsics(outputFile, info, sensorRotation)) }
                        } catch (error: Exception) {
                            Log.e(TAG, "Could not read camera geometry", error)
                            null
                        }
                        if (result == null || !continuation.isActive) outputFile.delete()
                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture failed: ${exception.message}", exception)
                        outputFile.delete()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }
    }

    fun unbind() {
        bindingGeneration++
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        imageCapture = null
        bound = false
        cameraInfo = null
        previewView = null
    }

    fun close() {
        unbind()
        executor.shutdown()
    }

    private fun readIntrinsics(file: File, info: CameraInfo, sensorRotation: Int): CameraIntrinsics {
        val camera2 = Camera2CameraInfo.from(info)
        val physical = requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE))
        val pixelArray = requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))
        val active = requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))
        val exif = ExifInterface(file.absolutePath)
        val fallbackFocal = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()?.toDouble() ?: 0.0
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, fallbackFocal)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val swap = ImageUtils.exifRotation(file) % 180 != 0
        return CameraIntrinsics.fromSensor(
            width = if (swap) bounds.outHeight else bounds.outWidth,
            height = if (swap) bounds.outWidth else bounds.outHeight,
            sensorWidthMm = physical.width.toDouble() * active.width() / pixelArray.width,
            sensorHeightMm = physical.height.toDouble() * active.height() / pixelArray.height,
            focalLengthMm = focal,
            sensorRotationDegrees = sensorRotation
        )
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
