package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.IsoMode
import com.example.model.ProjectionMode
import com.example.model.StitchingEngine
import com.example.ui.screens.CaptureScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun capture_screen_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        CaptureScreen(
          currentSlice = 3,
          totalSlices = 10,
          isCapturing = false,
          captureStatusText = "Captura 3/10. Sigue girando...",
          evValue = 0f,
          isHdr = true,
          isoMode = IsoMode.AUTO,
          isAutoCapture = true,
          selectedEngine = StitchingEngine.OPENCV,
          selectedProjection = ProjectionMode.EQUIRECTANGULAR,
          recentThumbnailUrl = null,
          deviceRoll = 0f,
          devicePitch = 0f,
          hasCameraPermission = true,
          onCameraReady = { _, _ -> },
          onCameraDispose = {},
          onEvChange = {},
          onHdrToggle = {},
          onIsoCycle = {},
          onAutoCaptureToggle = {},
          onEngineSelected = {},
          onProjectionSelected = {},
          onShutterClick = {},
          onGalleryClick = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
