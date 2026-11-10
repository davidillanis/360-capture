package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.SavedPanorama
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(
    savedPanoramas: List<SavedPanorama>,
    onSelectPanorama: (SavedPanorama) -> Unit,
    onDeletePanorama: (String) -> Unit,
    onBackClick: () -> Unit,
    onCreatePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.forLanguageTag("es")) }
    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("gallery_screen_root")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al inicio") }
            Text("Mis fotos", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (savedPanoramas.isNotEmpty()) {
                TextButton(onClick = onCreatePhoto) { Text("Crear foto") }
            }
        }
        if (savedPanoramas.isEmpty()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Collections, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Tu primera foto empieza aquí", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Las fotos que crees se guardarán automáticamente en este lugar.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onCreatePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Crear foto 360°") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Toca una foto para explorarla y compartirla.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(savedPanoramas, key = { it.id }) { panorama ->
                    Card(onClick = { onSelectPanorama(panorama) }, modifier = Modifier.fillMaxWidth().testTag("panorama_card_${panorama.id}")) {
                        AsyncImage(
                            model = File(panorama.previewUrl.ifBlank { panorama.panoramaUrl }),
                            contentDescription = panorama.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(panorama.title, style = MaterialTheme.typography.titleMedium)
                                Text(dateFormatter.format(Date(panorama.timestamp)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (panorama.coverage?.isComplete == false) "Foto incompleta" else "Ver en 360°",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeletePanorama(panorama.id) }) {
                                Icon(Icons.Default.Delete, "Eliminar ${panorama.title}")
                            }
                        }
                    }
                }
            }
        }
    }
}
