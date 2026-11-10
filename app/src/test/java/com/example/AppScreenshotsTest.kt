package com.example

import android.Manifest
import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.capture.CaptureTarget
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AppScreenshotsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun home_screen_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface {
        HomeScreen(
          savedCount = 0,
          hasPendingCapture = false,
          onCreatePhoto = {},
          onContinueCapture = {},
          onOpenGallery = {}
        )
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/home.png")
  }

  @Test
  fun guided_capture_screenshot() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface {
          CaptureScreen(
            currentSlice = 3,
            plannedPhotos = 40,
            isCapturing = false,
            isAutoCapturing = true,
            captureTarget = CaptureTarget(25.0, 0.0, "Horizonte"),
            previewIntrinsics = CameraIntrinsics(1080, 1440, 900.0, 900.0),
            cameraPose = CameraPose.lookingAt(0.0, 0.0),
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
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/capture.png")
  }
}
