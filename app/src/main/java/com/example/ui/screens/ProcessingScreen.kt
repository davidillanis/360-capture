package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.StitchingEngine
import com.example.ui.components.ImmersiveAtmosphereBackground
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveDeepPurple
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.ImmersiveLilacGlow
import com.example.ui.theme.OnSurface
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.Typography
import kotlin.math.roundToInt

@Composable
fun ProcessingScreen(
    stitchingProgress: Float = 0.45f,
    engine: StitchingEngine = StitchingEngine.OPENCV,
    totalImages: Int = 12,
    currentStepText: String = "Processing 12 images with OpenCV engine",
    modifier: Modifier = Modifier
) {
    // Ambient Glow Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Progress Arc Dash Rotation
    val arcRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcRotation"
    )

    // Scanline horizontal beam animation
    val scanlineX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanlineX"
    )

    val thumb1Url = "https://lh3.googleusercontent.com/aida-public/AB6AXuAPnEFjo_7ay_H66Lh07zH0c-JAwJUlmAJuZIDNbSryStp73l1kFhp_HN--jIZ5g-VykgGtrHUM4x61QgpvF2RPTrAT7TPDVJBM3Cg5tNtHg9VCZsLe62i5PSOWMiwcVW-l7vyJjqj-4HCkS-r74YqIjaEtrsL02I7ZanDvUCCxW7xWVM3YDAyUIo-qfxQXhkkaw_5FAUxaSZxls3sxemPt6KX7Ktl9YQcOugmNkhrds80Hn0Ms9kf"
    val thumb2Url = "https://lh3.googleusercontent.com/aida-public/AB6AXuB0l18qKToqkvDurpmgfyb7Uoq5_LaFjQabdS6ZKN1TDeYVENlDXwJedWf21R5gJXgIUew3wGYDUGX7qDFDSnFwdiUddYD10qvYAGN_4-m5Ae1grpBGXVMfob4nKxJ7Nb0jhRzZarGO6ypxB-Q6Stm6xffHmuihixGuflyD-U0yoUEbwE_EIKtgNxTocnOqHaN47a83gzxaPNUfgBlZSrb0CUKjc0wER4itUARFv2QafraVpaMT2rMZ"
    val thumb3Url = "https://lh3.googleusercontent.com/aida-public/AB6AXuCLwBTXD9cI_k1LTgOJzD9_CO2QAnYzG_l075vUs5jVH-gvc2DdRdIqFP-GAOOPF1xwR0f8BBZjOhu-Ak2p2cdYTbyTpvsDWBtm_zKfaKoU6KoJ9NINVZSEf0Ps2shW_K3mGxKhSxfUskM-1L2top-IMQBbIBeo5Ap6JevPiIvcwaihqxMcNNvYJ2AkAeV9ZtcApN5ds4-NdAXjrzVgfSUD6BVHwxStaTP_Qe3BPIyOsbMpQ9UJgKll"

    val grayscaleMatrix = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("processing_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        // Immersive atmosphere background orbs
        ImmersiveAtmosphereBackground()

        // Central Lilac Aura
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .blur(90.dp)
                .background(ImmersiveLilac, CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Large Circular Progress Indicator (220dp)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .testTag("circular_progress_indicator"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 5.dp.toPx()
                    val trackStroke = 3.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        (size.width - diameter) / 2,
                        (size.height - diameter) / 2
                    )
                    val arcSize = Size(diameter, diameter)

                    // Track Ring
                    drawArc(
                        color = Color(0xFF332D41),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = trackStroke)
                    )

                    // Active Progress Ring with Immersive Lilac
                    drawArc(
                        color = ImmersiveLilac,
                        startAngle = arcRotation - 90f,
                        sweepAngle = (stitchingProgress * 280f).coerceAtLeast(40f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Center Icon with Lilac Glow
                Icon(
                    imageVector = Icons.Default.Panorama,
                    contentDescription = "Stitching",
                    tint = ImmersiveLilac,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Headline & Subtitle
            Text(
                text = "Stitching Panorama...",
                style = Typography.headlineLarge,
                color = OnSurface,
                fontSize = 28.sp,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentStepText,
                style = Typography.bodyLarge,
                color = Secondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(44.dp))

            // Glassmorphism Thumbnail Preview Strip with Immersive styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceContainer)
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("thumbnail_preview_strip")
            ) {
                // Moving Scanline Light Effect in Lilac
                Box(
                    modifier = Modifier
                        .offset { IntOffset(scanlineX.roundToInt(), 0) }
                        .width(36.dp)
                        .height(96.dp)
                        .blur(6.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ImmersiveLilac.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Thumbnails Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Processed Thumbnail 1
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .alpha(0.6f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222028))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(thumb1Url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Processed image 1",
                                colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Processed Thumbnail 2
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .alpha(0.8f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222028))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(thumb2Url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Processed image 2",
                                colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Currently Processing Thumbnail (Highlighted in Lilac)
                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 86.dp)
                                .scale(1.05f)
                                .shadow(14.dp, RoundedCornerShape(12.dp), spotColor = ImmersiveLilac)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, ImmersiveLilac, RoundedCornerShape(12.dp))
                                .background(Color(0xFF222028))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(thumb3Url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Currently processing image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Immersive purple/lilac processing tint
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ImmersiveLilac.copy(alpha = 0.18f))
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Pending Placeholder 1
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .alpha(0.35f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .background(Color(0xFF1D1B20)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Pending image",
                                tint = Secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Pending Placeholder 2
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .alpha(0.15f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .background(Color(0xFF1D1B20)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Pending image",
                                tint = Secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Micro Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(SurfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(stitchingProgress)
                                .shadow(8.dp, spotColor = ImmersiveLilac, ambientColor = ImmersiveLilac)
                                .background(ImmersiveLilac, RoundedCornerShape(9999.dp))
                        )
                    }
                }
            }
        }
    }
}
