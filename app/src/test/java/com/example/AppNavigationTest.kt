package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `first launch offers a clear route through gallery to capture and back home`() {
        compose.onNodeWithTag("home_screen_root").assertIsDisplayed()
        compose.onNodeWithTag("open_gallery_button").performScrollTo().performClick()
        compose.onNodeWithText("Tu primera foto empieza aquí").assertIsDisplayed()
        compose.onNodeWithText("Crear foto 360°").performScrollTo().performClick()
        compose.onNodeWithTag("capture_screen_root").assertIsDisplayed()
        compose.onNodeWithText("Permitir cámara").assertIsDisplayed()
        compose.onNodeWithTag("capture_back_button").performClick()
        compose.onNodeWithTag("home_screen_root").assertIsDisplayed()
        compose.onNodeWithTag("open_gallery_button").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Volver al inicio").performClick()
        compose.onNodeWithTag("create_photo_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `system back from capture returns to home instead of closing the app`() {
        compose.onNodeWithTag("create_photo_button").performScrollTo().performClick()
        compose.onNodeWithTag("capture_screen_root").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen_root").assertIsDisplayed()
    }
}
