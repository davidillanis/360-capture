package com.example.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

enum class WhatsAppTarget(val packageName: String) {
    PERSONAL("com.whatsapp"),
    BUSINESS("com.whatsapp.w4b")
}

object WhatsAppSharing {
    const val DOCUMENT_MIME_TYPE = "application/octet-stream"

    fun availableTargets(context: Context): List<WhatsAppTarget> = WhatsAppTarget.entries.filter {
        documentIntent(it).resolveActivity(context.packageManager) != null
    }

    /** Package-scoped intents open WhatsApp's own contact selection. Sending is done in WhatsApp. */
    fun createIntent(context: Context, document: File, targets: List<WhatsAppTarget>): Intent {
        require(targets.isNotEmpty())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", document)
        val intents = targets.distinct().map { target ->
            documentIntent(target).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, document.name)
                clipData = ClipData.newRawUri(document.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (intents.size == 1) return intents.first()
        return Intent.createChooser(intents.first(), "Compartir con WhatsApp").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
        }
    }

    private fun documentIntent(target: WhatsAppTarget) = Intent(Intent.ACTION_SEND).apply {
        type = DOCUMENT_MIME_TYPE
        setPackage(target.packageName)
    }
}
