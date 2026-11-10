package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveDeepPurple
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.Typography

@Composable
fun ShutterControls(
    recentThumbnailUrl: String,
    coveredCount: Int,
    totalCells: Int,
    isSphereComplete: Boolean,
    onThumbnailClick: () -> Unit,
    onShutterClick: () -> Unit,
    isCapturing: Boolean = false,
    isAutoCapturing: Boolean = false,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = GlassBackground,
                shape = RoundedCornerShape(28.dp)
            )
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("shutter_control_bar"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Thumbnail (Left)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceContainer)
                    .border(
                        width = 1.5.dp,
                        color = ImmersiveLilac.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onThumbnailClick)
                    .testTag("gallery_thumbnail_button"),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recentThumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Gallery Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Shutter Button (Center)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainer)
                    .clickable(onClick = onShutterClick)
                    .testTag("shutter_capture_button"),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring with lavender accent
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .border(
                            width = 2.dp,
                            color = ImmersiveLilac.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                )

                // Inner Immersive Lilac trigger with glow and deep purple icon
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            spotColor = ImmersiveLilac,
                            ambientColor = ImmersiveLilac
                        )
                        .background(ImmersiveLilac, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAutoCapturing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isAutoCapturing) "Pausar captura" else "Iniciar o reanudar captura automática",
                        tint = ImmersiveDeepPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Sphere Coverage Indicator (Right)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(
                            elevation = if (isSphereComplete) 12.dp else 0.dp,
                            shape = CircleShape,
                            spotColor = ImmersiveLilac,
                            ambientColor = ImmersiveLilac
                        )
                        .background(
                            color = if (isSphereComplete) ImmersiveLilac else SurfaceContainerHighest,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = if (isSphereComplete) "Esfera completa" else "Cobertura",
                        tint = if (isSphereComplete) ImmersiveDeepPurple else OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "$coveredCount/$totalCells",
                    color = OnSurfaceVariant,
                    style = Typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}
