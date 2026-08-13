package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.SurfaceContainerHighest

@Composable
fun ProgressDots(
    totalSlices: Int = 10,
    capturedSlices: Int = 3,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = GlassBackground,
                shape = RoundedCornerShape(9999.dp)
            )
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(9999.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("progress_dots_container"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSlices) {
                val isFilled = i < capturedSlices
                if (isFilled) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                spotColor = ImmersiveLilac,
                                ambientColor = ImmersiveLilac
                            )
                            .background(ImmersiveLilac, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SurfaceContainerHighest, CircleShape)
                    )
                }
            }
        }
    }
}
