package com.example.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import com.example.stitching.SliceSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureSessionStoreTest {
    @Test
    fun `original camera geometry can be reloaded exactly from the JPEG sidecar`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File.createTempFile("capture-test", ".jpg", context.cacheDir)
        try {
            val source = SliceSource(file, CameraPose.lookingAt(359.0, 78.0, -23.0), CameraIntrinsics(3000, 4000, 2400.5, 2401.0))
            CaptureSessionStore.write(source, CaptureTarget(359.0, 78.0, "test"))
            assertEquals(source, CaptureSessionStore.read(file))
        } finally {
            file.delete()
            CaptureSessionStore.metadataFile(file).delete()
        }
    }
}
