package com.example.sharing

import android.net.Uri
import androidx.core.content.FileProvider

class PanoramaFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        // Both the intent and the provider advertise a document so recipients skip the photo editor.
        // The display name still ends in .jpg and the contents remain a standard JPEG.
        return if (uri.pathSegments.firstOrNull() == "panorama_documents") {
            WhatsAppSharing.DOCUMENT_MIME_TYPE
        } else {
            super.getType(uri)
        }
    }
}
