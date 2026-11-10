package com.example.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sharing.PanoramaDocumentExporter
import com.example.sharing.WhatsAppSharing
import com.example.stitching.CoverageSummary
import com.example.viewer.EquirectangularViewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ViewerScreen(
    panoramaPath: String?,
    deviceAzimuth: Float,
    devicePitch: Float,
    isSaved: Boolean,
    isSaving: Boolean,
    canContinueCapture: Boolean,
    onBackClick: () -> Unit,
    onRetrySave: () -> Unit,
    onNewCapture: () -> Unit,
    onContinueCapture: () -> Unit,
    coverage: CoverageSummary? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sensorEnabled by rememberSaveable(panoramaPath) { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    val panoramaFile = remember(panoramaPath) { panoramaPath?.let { File(it) } }
    val shareOnWhatsApp: () -> Unit = {
        if (!isSharing && panoramaFile != null) {
            isSharing = true
            scope.launch {
                try {
                    val targets = WhatsAppSharing.availableTargets(context)
                    if (targets.isEmpty()) {
                        snackbarHostState.showSnackbar("Instala WhatsApp o WhatsApp Business para compartir la foto.")
                    } else {
                        val document = withContext(Dispatchers.IO) {
                            PanoramaDocumentExporter(context.cacheDir).export(panoramaFile, coverage)
                        }
                        context.startActivity(WhatsAppSharing.createIntent(context, document, targets))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: ActivityNotFoundException) {
                    snackbarHostState.showSnackbar("No se pudo abrir WhatsApp. Comprueba que esté instalado.")
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("No se pudo preparar la foto. Comprueba que haya espacio libre y vuelve a intentarlo.")
                } finally {
                    isSharing = false
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding().testTag("viewer_screen_root")) {
        val landscape = maxWidth > maxHeight
        val actionsMaxHeight = maxHeight * 0.48f
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("viewer_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a Mis fotos")
                }
                Text("Tu foto 360°", style = MaterialTheme.typography.titleMedium)
            }
            val viewer: @Composable (Modifier) -> Unit = { viewerModifier ->
                Box(viewerModifier) {
                    EquirectangularViewer(panoramaFile, deviceAzimuth, devicePitch, sensorEnabled)
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (sensorEnabled) "Gira el teléfono para mirar alrededor" else "Arrastra la imagen para mirar alrededor",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                        )
                    }
                }
            }
            val actions: @Composable (Modifier) -> Unit = { actionsModifier ->
                Column(
                    actionsModifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSaved) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Guardada en Mis fotos", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("La foto aún no está guardada", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onRetrySave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isSaving) "Guardando…" else "Reintentar guardado")
                        }
                    }
                    if (coverage != null && !coverage.isComplete) {
                        Text("Foto incompleta: las zonas negras no se fotografiaron.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    if (canContinueCapture) {
                        OutlinedButton(onClick = onContinueCapture, modifier = Modifier.fillMaxWidth()) { Text("Completar foto") }
                    }
                    FilterChip(
                        selected = sensorEnabled, onClick = { sensorEnabled = !sensorEnabled },
                        label = { Text("Mover con el teléfono") }, modifier = Modifier.testTag("sensor_toggle_button")
                    )
                    Button(
                        onClick = shareOnWhatsApp, enabled = panoramaFile != null && !isSharing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("share_whatsapp_button")
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSharing) "Preparando foto…" else "Compartir por WhatsApp")
                    }
                    Text("Se envía como archivo. Para explorarlo, hace falta un visor 360°.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    TextButton(onClick = onNewCapture, enabled = !isSaving) { Text("Crear otra foto") }
                }
            }
            if (landscape) {
                Row(Modifier.weight(1f)) {
                    viewer(Modifier.weight(1f).fillMaxSize())
                    actions(Modifier.width(300.dp))
                }
            } else {
                viewer(Modifier.weight(1f).fillMaxWidth())
                actions(Modifier.fillMaxWidth().heightIn(max = actionsMaxHeight))
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
    }
}
