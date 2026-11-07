package com.example.sharing

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatsAppSharingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun attachProviderToCurrentSandbox() {
        // Robolectric changes cacheDir between tests; attaching clears FileProvider's static path cache.
        val authority = "${context.packageName}.fileprovider"
        val info = requireNotNull(context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA))
        val provider = PanoramaFileProvider().apply { attachInfo(this@WhatsAppSharingTest.context, info) }
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }

    @Test
    fun `sharing grants read access to the JPEG document rather than the photo pipeline`() {
        val directory = File(context.cacheDir, "panorama-shares").apply { mkdirs() }
        val document = File(directory, "Panorama_360_test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val intent = WhatsAppSharing.createIntent(context, document, listOf(WhatsAppTarget.PERSONAL))
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("com.whatsapp", intent.`package`)
            assertEquals("application/octet-stream", intent.type)
            val uri = requireNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals("content", uri.scheme)
            assertEquals("panorama_documents", uri.pathSegments.first())
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals("application/octet-stream", context.contentResolver.getType(uri))
            context.contentResolver.openInputStream(uri)!!.use {
                assertArrayEquals(document.readBytes(), it.readBytes())
            }
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(document.name, it.getString(0))
            }
        } finally {
            document.delete()
        }
    }

    @Test
    fun `both WhatsApp variants produce a chooser restricted to those packages`() {
        val directory = File(context.cacheDir, "panorama-shares").apply { mkdirs() }
        val document = File(directory, "chooser.jpg").apply { writeText("test") }
        try {
            val chooser = WhatsAppSharing.createIntent(context, document, WhatsAppTarget.entries)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val primary = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            @Suppress("DEPRECATION")
            val extra = chooser.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)!!.single() as Intent
            assertEquals("com.whatsapp", primary.`package`)
            assertEquals("com.whatsapp.w4b", extra.`package`)
            assertEquals(primary.type, extra.type)
            assertEquals(primary.clipData!!.getItemAt(0).uri, extra.clipData!!.getItemAt(0).uri)
            assertTrue(extra.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally {
            document.delete()
        }
    }

    @Test
    fun `WhatsApp Business works when personal WhatsApp is not installed`() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/octet-stream").setPackage("com.whatsapp.w4b")
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                name = "com.whatsapp.w4b.ShareActivity"
                packageName = "com.whatsapp.w4b"
                exported = true
                enabled = true
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.whatsapp.w4b"
                    enabled = true
                }
            }
            isDefault = true
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
        assertEquals(listOf(WhatsAppTarget.BUSINESS), WhatsAppSharing.availableTargets(context))
    }

    @Test
    fun `missing WhatsApp is detected before preparing a document`() {
        assertTrue(WhatsAppSharing.availableTargets(context).isEmpty())
    }

    @Test
    fun `ordinary gallery JPEG URIs still expose their image type`() {
        val file = File.createTempFile("gallery-pano", ".jpg", context.cacheDir)
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            assertEquals("image/jpeg", context.contentResolver.getType(uri))
        } finally {
            file.delete()
        }
    }
}
