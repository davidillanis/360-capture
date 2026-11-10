package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PanoramaPhotosphere
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    savedCount: Int,
    hasPendingCapture: Boolean,
    onCreatePhoto: () -> Unit,
    onContinueCapture: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("home_screen_root"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PanoramaPhotosphere, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("360Capture", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Todo el lugar en una foto",
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Te guiamos para fotografiar a tu alrededor, arriba y abajo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeStep("1", "Sigue la flecha", "Gira sin cambiar de sitio.")
                HomeStep("2", "Quédate quieto", "La cámara toma cada foto sola.")
                HomeStep("3", "Mira y comparte", "Tu foto se guarda automáticamente.")
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = if (hasPendingCapture) onContinueCapture else onCreatePhoto,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("create_photo_button")
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(if (hasPendingCapture) "Continuar foto 360°" else "Crear foto 360°")
        }
        if (hasPendingCapture) {
            Text(
                "Tienes una captura en pausa.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenGallery,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_gallery_button")
        ) {
            Icon(Icons.Default.Collections, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(if (savedCount == 0) "Mis fotos" else "Mis fotos ($savedCount)")
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Sin internet · Sin cuentas", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeStep(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
