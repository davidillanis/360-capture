package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.SavedPanorama
import com.example.ui.components.ImmersiveAtmosphereBackground
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.OnSurface
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.Typography
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(
    savedPanoramas: List<SavedPanorama>,
    onSelectPanorama: (SavedPanorama) -> Unit,
    onDeletePanorama: (String) -> Unit,
    onBackToCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("gallery_screen_root")
    ) {
        ImmersiveAtmosphereBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                    onClick = onBackToCapture,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Secondary
                    )
                }

                Text(
                    text = "360 Gallery",
                    style = Typography.titleMedium,
                    color = ImmersiveLilac,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp
                )

                IconButton(
                    onClick = onBackToCapture,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = ImmersiveLilac
                    )
                }
            }

            if (savedPanoramas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Panorama,
                            contentDescription = null,
                            tint = ImmersiveLilac.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Panoramas Yet",
                            style = Typography.titleLarge,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Capture your first 360° panoramic photograph",
                            style = Typography.bodyMedium,
                            color = Secondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(savedPanoramas, key = { it.id }) { pano ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(SurfaceContainer)
                                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                                .clickable { onSelectPanorama(pano) }
                                .testTag("panorama_card_${pano.id}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Panorama Preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(File(pano.previewUrl.takeIf { it.isNotBlank() } ?: pano.panoramaUrl))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = pano.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Gradient overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0xCC1C1B1F)
                                                    )
                                                )
                                            )
                                    )

                                    // 360 Badge in Immersive Lilac
                                    Box(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .align(Alignment.TopStart)
                                            .background(GlassBackground, RoundedCornerShape(9999.dp))
                                            .border(1.dp, GlassBorder, RoundedCornerShape(9999.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "360° RAW",
                                            style = Typography.labelSmall,
                                            color = ImmersiveLilac
                                        )
                                    }

                                    // Delete action
                                    IconButton(
                                        onClick = { onDeletePanorama(pano.id) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                // Metadata bar
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = pano.title,
                                        style = Typography.titleMedium,
                                        color = OnSurface,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${dateFormatter.format(Date(pano.timestamp))} • ${pano.location}",
                                        style = Typography.bodyMedium,
                                        color = Secondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${pano.sliceCount} Slices",
                                            style = Typography.labelSmall,
                                            color = ImmersiveLilac
                                        )
                                        Text("•", color = Secondary)
                                        Text(
                                            text = pano.engine,
                                            style = Typography.labelSmall,
                                            color = Secondary
                                        )
                                        Text("•", color = Secondary)
                                        Text(
                                            text = pano.iso,
                                            style = Typography.labelSmall,
                                            color = Secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
