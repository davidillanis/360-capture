package com.example

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.capture.CaptureTarget
import com.example.stitching.CameraPose
import com.example.ui.screens.CaptureScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ManualCaptureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `manual shutter can be tapped close to the target but not from elsewhere`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        var count = 0
        compose.setContent {
            MyApplicationTheme {
                CaptureScreen(
                    currentSlice = 1,
                    plannedPhotos = 20,
                    isCapturing = false,
                    isAutoCapturing = true,
                    captureTarget = CaptureTarget(0.0, 0.0, "Horizonte"),
                    previewIntrinsics = null,
                    cameraPose = CameraPose.lookingAt(5.0, 0.0),
                    holdProgress = 0f,
                    hasCameraPermission = true,
                    onCameraReady = { _, _ -> },
                    onCameraDispose = {},
                    onCaptureActive = {},
                    onShutterClick = {},
                    onManualCapture = { count++ },
                    onFinishCapture = {},
                    onResetCapture = {},
                    onBackClick = {}
                )
            }
        }
        compose.onNodeWithTag("manual_capture_button").assertIsEnabled().performClick()
        assertEquals(1, count)
    }

    @Test
    fun `manual shutter stays disabled when far from the target`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        compose.setContent {
            MyApplicationTheme {
                CaptureScreen(
                    currentSlice = 1,
                    plannedPhotos = 20,
                    isCapturing = false,
                    isAutoCapturing = true,
                    captureTarget = CaptureTarget(0.0, 0.0, "Horizonte"),
                    previewIntrinsics = null,
                    cameraPose = CameraPose.lookingAt(40.0, 0.0),
                    holdProgress = 0f,
                    hasCameraPermission = true,
                    onCameraReady = { _, _ -> },
                    onCameraDispose = {},
                    onCaptureActive = {},
                    onShutterClick = {},
                    onFinishCapture = {},
                    onResetCapture = {},
                    onBackClick = {}
                )
            }
        }
        compose.onNodeWithTag("manual_capture_button").assertIsNotEnabled()
    }
}
