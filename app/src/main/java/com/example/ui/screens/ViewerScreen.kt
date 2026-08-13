package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ImmersiveAtmosphereBackground
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveDeepPurple
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.ImmersiveLilacGlow
import com.example.ui.theme.InverseSurface
import com.example.ui.theme.Secondary
import com.example.ui.theme.Typography
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(
    panoramaUrl: String,
    deviceAzimuth: Float,
    devicePitch: Float,
    onBackClick: () -> Unit,
    onSaveToGallery: () -> Unit,
    onRetakeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Interactive 360 Pan & Zoom State
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var hasUserInteracted by remember { mutableStateOf(false) }

    // Swipe Hint Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "dragPulse")
    val hintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    hasUserInteracted = true
                    panOffsetX += pan.x
                    panOffsetY = (panOffsetY + pan.y).coerceIn(-200f, 200f)
                    zoomScale = (zoomScale * zoom).coerceIn(0.9f, 3.0f)
                }
            }
            .testTag("viewer_screen_root")
    ) {
        // Full Panoramic Image Canvas with Interactive Pan / Scale
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panOffsetX
                    translationY = panOffsetY
                    scaleX = zoomScale * 1.5f
                    scaleY = zoomScale * 1.5f
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(panoramaUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "360 Panorama Viewer",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
            // Back Button
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

            // Title
            Text(
                text = "360Capture",
                style = Typography.titleMedium,
                color = ImmersiveLilac,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp
            )

            // Share Button
            IconButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "360 Panoramic Photo")
                        putExtra(Intent.EXTRA_TEXT, "Check out my high-resolution 360° panoramic photo captured with 360Capture!")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share 360 Panorama"))
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("viewer_share_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
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

        // "DRAG TO PAN" Animated Hint Overlay
        AnimatedVisibility(
            visible = !hasUserInteracted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(hintAlpha)
            ) {
                Icon(
                    imageVector = Icons.Default.Swipe,
                    contentDescription = null,
                    tint = ImmersiveLilac.copy(alpha = 0.8f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "DRAG TO PAN",
                    style = Typography.labelSmall,
                    color = ImmersiveLilac.copy(alpha = 0.85f),
                    letterSpacing = 2.sp
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
                    // Save to Gallery (Primary Action - Immersive Lilac Button with Deep Purple Text)
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
                                    snackbarHostState.showSnackbar("Saved to 360Capture Gallery!")
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
                                text = "Save to Gallery",
                                color = ImmersiveDeepPurple,
                                style = Typography.labelLarge,
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }

                    // Retake Button (Secondary Action)
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
                                text = "Retake",
                                color = InverseSurface,
                                style = Typography.labelLarge,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Toast / Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
        )
    }
}
