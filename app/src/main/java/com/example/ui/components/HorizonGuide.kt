package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.ImmersiveLilacGlow
import com.example.ui.theme.ImmersiveLilacLight
import kotlin.math.roundToInt

@Composable
fun HorizonGuide(
    rollOffset: Float,
    pitchOffset: Float,
    instructionText: String = "Keep rotating slowly",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Horizon line across full width with subtle Lilac glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ImmersiveLilac.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.9f),
                            ImmersiveLilac.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
                .shadow(elevation = 8.dp, spotColor = ImmersiveLilac, ambientColor = ImmersiveLilac)
        )

        // Center level indicator frame (120dp x 20dp) with rounded corners
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(22.dp)
                .border(
                    width = 1.dp,
                    color = ImmersiveLilac.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Level Target Center Markers
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(8.dp)
                    .background(ImmersiveLilac.copy(alpha = 0.6f))
            )

            // Dynamic Level Dot moving according to tilt with Immersive Lilac glow
            val dotX = (rollOffset * 2.5f).coerceIn(-48f, 48f)
            val dotY = (pitchOffset * 1.5f).coerceIn(-6f, 6f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(dotX.roundToInt(), dotY.roundToInt()) }
                    .size(8.dp)
                    .background(ImmersiveLilac, CircleShape)
                    .shadow(elevation = 10.dp, shape = CircleShape, spotColor = ImmersiveLilac, ambientColor = ImmersiveLilac)
            )
        }

        // Subtitle Pill: "Keep rotating slowly"
        Box(
            modifier = Modifier
                .offset(y = 46.dp)
                .alpha(pulseAlpha)
                .background(
                    color = GlassBackground,
                    shape = RoundedCornerShape(9999.dp)
                )
                .border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = RoundedCornerShape(9999.dp)
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("horizon_instruction_pill")
        ) {
            Text(
                text = instructionText,
                color = ImmersiveLilacLight,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
