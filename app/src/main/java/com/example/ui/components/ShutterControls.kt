package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.theme.ImmersiveLilacGlow
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.Typography

@Composable
fun ShutterControls(
    recentThumbnailUrl: String,
    isAutoCapture: Boolean,
    onThumbnailClick: () -> Unit,
    onShutterClick: () -> Unit,
    onAutoCaptureToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val shutterScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "shutterScale"
    )

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
                    .scale(shutterScale)
                    .clip(CircleShape)
                    .background(SurfaceContainer)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                                onShutterClick()
                            }
                        )
                    }
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
                        imageVector = Icons.Default.Panorama,
                        contentDescription = "Capture Panorama",
                        tint = ImmersiveDeepPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Auto-Capture Switch (Right)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(48.dp)
            ) {
                Switch(
                    checked = isAutoCapture,
                    onCheckedChange = onAutoCaptureToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ImmersiveDeepPurple,
                        checkedTrackColor = ImmersiveLilac,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = SurfaceContainerHighest
                    ),
                    modifier = Modifier
                        .scale(0.85f)
                        .testTag("auto_capture_switch")
                )
                Text(
                    text = "AUTO",
                    color = OnSurfaceVariant,
                    style = Typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}
