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
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.stitching.ImageUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private var camera: Camera? = null
    private var previewView: PreviewView? = null
    private var bindingGeneration = 0
    private var whiteBalanceLocked = false
    var captureIntrinsics: CameraIntrinsics? = null
        private set
    var previewIntrinsics: CameraIntrinsics? = null
        private set

    /** Binds preview + capture for the back camera. Must be called on the main thread. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: (Boolean) -> Unit = {}
    ) {
        val generation = ++bindingGeneration
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (generation != bindingGeneration) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                val preview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation).build()

                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(98)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .setResolutionSelector(resolution)
                    .setTargetRotation(rotation)

                val capture = captureBuilder.build()
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                this.camera = camera
                cameraInfo = camera.cameraInfo
                this.previewView = previewView
                preview.setSurfaceProvider(previewView.surfaceProvider)
                imageCapture = capture
                whiteBalanceLocked = false
                captureIntrinsics = estimatedIntrinsics(capture.resolutionInfo, camera.cameraInfo)
                previewIntrinsics = estimatedIntrinsics(preview.resolutionInfo, camera.cameraInfo)
                bound = true
                onReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
                bound = false
                onReady(false)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /** Meter the point being photographed, then retain a common white balance for this binding. */
    suspend fun prepareCapture(requireFocus: Boolean = true): Boolean {
        val current = camera ?: return false
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val focus = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        if (current.cameraInfo.isFocusMeteringSupported(focus)) {
            val result = withTimeoutOrNull(2500) { current.cameraControl.startFocusAndMetering(focus).awaitResult() }
            if (result?.isFocusSuccessful != true && requireFocus) return false
        }
        if (!whiteBalanceLocked && Camera2CameraInfo.from(current.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true) {
            whiteBalanceLocked = withTimeoutOrNull(1000) {
                Camera2CameraControl.from(current.cameraControl).addCaptureRequestOptions(
                    CaptureRequestOptions.Builder().setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true).build()
                ).awaitResult()
                true
            } == true
        }
        return true
    }

    fun resetWhiteBalance() {
        whiteBalanceLocked = false
        camera?.let {
            Camera2CameraControl.from(it.cameraControl).clearCaptureRequestOptions()
        }
    }

    fun setExposure(ev: Float) {
        val current = camera ?: return
        val exposure = current.cameraInfo.exposureState
        if (!exposure.isExposureCompensationSupported) return
        val index = kotlin.math.round(ev / exposure.exposureCompensationStep.toFloat()).toInt()
            .coerceIn(exposure.exposureCompensationRange.lower, exposure.exposureCompensationRange.upper)
        current.cameraControl.setExposureCompensationIndex(index)
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
        camera = null
        previewView = null
        captureIntrinsics = null
        previewIntrinsics = null
        whiteBalanceLocked = false
    }

    fun close() {
        unbind()
        executor.shutdown()
    }

    private fun readIntrinsics(file: File, info: CameraInfo, sensorRotation: Int): CameraIntrinsics {
        val exif = ExifInterface(file.absolutePath)
        val camera2 = Camera2CameraInfo.from(info)
        val fallbackFocal = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()?.toDouble() ?: 0.0
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, fallbackFocal)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val swap = ImageUtils.exifRotation(file) % 180 != 0
        return intrinsics(info, if (swap) bounds.outHeight else bounds.outWidth,
            if (swap) bounds.outWidth else bounds.outHeight, sensorRotation, focal)
    }

    private fun estimatedIntrinsics(resolution: androidx.camera.core.ResolutionInfo?, info: CameraInfo): CameraIntrinsics? {
        resolution ?: return null
        val swap = resolution.rotationDegrees % 180 != 0
        return intrinsics(info, if (swap) resolution.resolution.height else resolution.resolution.width,
            if (swap) resolution.resolution.width else resolution.resolution.height, resolution.rotationDegrees)
    }

    private fun intrinsics(info: CameraInfo, width: Int, height: Int, sensorRotation: Int, focal: Double? = null): CameraIntrinsics {
        val camera2 = Camera2CameraInfo.from(info)
        val physical = requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE))
        val pixelArray = requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))
        val active = requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))
        val focalLength = focal ?: requireNotNull(camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))
            .first().toDouble()
        return CameraIntrinsics.fromSensor(
            width = width, height = height,
            sensorWidthMm = physical.width.toDouble() * active.width() / pixelArray.width,
            sensorHeightMm = physical.height.toDouble() * active.height() / pixelArray.height,
            focalLengthMm = focalLength,
            sensorRotationDegrees = sensorRotation
        )
    }

    fun isBound(): Boolean = bound

    companion object {
        private const val TAG = "CameraController"
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        if (continuation.isActive) {
            try {
                continuation.resume(get())
            } catch (error: Exception) {
                continuation.resumeWith(Result.failure(error))
            }
        }
    }, java.util.concurrent.Executor { it.run() })
    continuation.invokeOnCancellation { cancel(false) }
}
