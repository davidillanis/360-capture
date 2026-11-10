package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingScreen(
    stitchingProgress: Float,
    isSaving: Boolean,
    onBackToCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeepingScreenOn }
    }
    val progress = stitchingProgress.coerceIn(0f, 1f)
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(28.dp).testTag("processing_screen_root"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 6.dp)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(32.dp))
        Text("Creando tu foto 360°", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                isSaving -> "Guardando en Mis fotos…"
                progress < 0.45f -> "Alineando las fotos…"
                progress < 0.92f -> "Uniendo todo el lugar en una imagen…"
                else -> "Revisando el resultado…"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Ya puedes dejar de girar. Abriremos la foto cuando esté lista y quedará guardada en la app.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
        )
        if (!isSaving) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBackToCapture) { Text("Volver a la captura") }
        }
    }
}
