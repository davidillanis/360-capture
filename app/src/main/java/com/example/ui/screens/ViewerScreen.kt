package com.example.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharing.PanoramaDocumentExporter
import com.example.sharing.WhatsAppSharing
import com.example.stitching.CoverageSummary
import com.example.ui.components.ImmersiveAtmosphereBackground
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveDeepPurple
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.InverseSurface
import com.example.ui.theme.Secondary
import com.example.ui.theme.Typography
import com.example.viewer.EquirectangularViewer
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ViewerScreen(
    panoramaPath: String?,
    deviceAzimuth: Float,
    devicePitch: Float,
    onBackClick: () -> Unit,
    onSaveToGallery: () -> Unit,
    onRetakeClick: () -> Unit,
    coverageText: String? = null,
    coverage: CoverageSummary? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sensorEnabled by remember { mutableStateOf(true) }
    var isSharing by remember { mutableStateOf(false) }

    val panoramaFile = panoramaPath?.let { File(it) }
    val shareOnWhatsApp: () -> Unit = {
        if (!isSharing && panoramaFile != null) {
            isSharing = true
            scope.launch {
                try {
                    val targets = WhatsAppSharing.availableTargets(context)
                    if (targets.isEmpty()) {
                        snackbarHostState.showSnackbar("Instala WhatsApp o WhatsApp Business para compartir el panorama")
                    } else {
                        val document = withContext(Dispatchers.IO) {
                            PanoramaDocumentExporter(context.cacheDir).export(panoramaFile, coverage)
                        }
                        context.startActivity(WhatsAppSharing.createIntent(context, document, targets))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: ActivityNotFoundException) {
                    snackbarHostState.showSnackbar("No se pudo abrir WhatsApp. Comprueba que esté instalado y habilitado.")
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("No se pudo preparar el documento. Comprueba que el panorama exista y haya espacio libre.")
                } finally {
                    isSharing = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("viewer_screen_root")
    ) {
        // Interactive 360 panorama
        if (panoramaFile != null) {
            EquirectangularViewer(
                panoramaFile = panoramaFile,
                azimuthDeg = deviceAzimuth,
                pitchDeg = devicePitch,
                sensorEnabled = sensorEnabled
            )
        }

        // Ambient glow atmosphere
        ImmersiveAtmosphereBackground()

        // Ambient Top/Bottom Gradient Shadows for Contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC1C1B1F),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xE61C1B1F)
                        )
                    )
                )
        )

        // Top Navigation Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .background(GlassBackground)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("viewer_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Secondary
                )
            }

            Text(
                text = "360Capture",
                style = Typography.titleMedium,
                color = ImmersiveLilac,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp
            )

            IconButton(
                onClick = shareOnWhatsApp,
                enabled = panoramaFile != null && !isSharing,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("viewer_share_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Compartir por WhatsApp como documento JPEG",
                    tint = Secondary
                )
            }
        }

        // "360 Interactive View" Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(GlassBackground)
                .border(1.dp, GlassBorder, RoundedCornerShape(9999.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(8.dp, RoundedCornerShape(9999.dp))
                .testTag("interactive_view_badge"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChangeCircle,
                    contentDescription = "360 Mode",
                    tint = ImmersiveLilac,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Interactive View",
                    style = Typography.labelLarge,
                    color = InverseSurface,
                    fontSize = 14.sp
                )
            }
        }

        // "DRAG TO PAN" Hint Overlay
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(0.7f)
        ) {
            Icon(
                imageVector = Icons.Default.Swipe,
                contentDescription = null,
                tint = ImmersiveLilac.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ARRASTRA PARA MOVERTE",
                style = Typography.labelSmall,
                color = ImmersiveLilac.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
        }

        // Sensor toggle pill (top right below bar)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 72.dp, end = 16.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(GlassBackground)
                .border(1.dp, if (sensorEnabled) ImmersiveLilac else GlassBorder, RoundedCornerShape(9999.dp))
                .clickable { sensorEnabled = !sensorEnabled }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("sensor_toggle_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideogameAsset,
                    contentDescription = null,
                    tint = if (sensorEnabled) ImmersiveLilac else Secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (sensorEnabled) "Sensor ON" else "Sensor OFF",
                    style = Typography.labelSmall,
                    color = if (sensorEnabled) ImmersiveLilac else InverseSurface,
                    fontSize = 12.sp
                )
            }
        }

        // Bottom Actions Overlay Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassBackground)
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("viewer_bottom_card")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    coverageText?.let {
                        Text(text = it, color = ImmersiveLilac, style = Typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = shareOnWhatsApp,
                        enabled = panoramaFile != null && !isSharing,
                        modifier = Modifier.fillMaxWidth().testTag("share_whatsapp_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSharing) "Preparando JPEG…" else "Enviar por WhatsApp")
                    }
                    Text(
                        text = "Documento JPEG original · para verlo en 360°, ábrelo con un visor compatible.",
                        color = Secondary,
                        style = Typography.bodySmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(9999.dp),
                                spotColor = ImmersiveLilac,
                                ambientColor = ImmersiveLilac
                            )
                            .clip(RoundedCornerShape(9999.dp))
                            .background(ImmersiveLilac)
                            .clickable {
                                onSaveToGallery()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Guardado en la galería 360")
                                }
                            }
                            .testTag("save_to_gallery_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = ImmersiveDeepPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Guardar en Galería",
                                color = ImmersiveDeepPurple,
                                style = Typography.labelLarge,
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(9999.dp))
                            .background(Color.Transparent)
                            .clickable(onClick = onRetakeClick)
                            .testTag("retake_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                tint = InverseSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Retomar",
                                color = InverseSurface,
                                style = Typography.labelLarge,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
        )
    }
}
