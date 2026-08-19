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
          totalSlices = 24,
          coveredCells = setOf(0, 1, 2),
          aimCell = 4,
          isSphereComplete = false,
          isCapturing = false,
          captureStatusText = "Apunta a: Arriba frontal (3/24)",
          evValue = 0f,
          isHdr = true,
          isoMode = IsoMode.AUTO,
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
