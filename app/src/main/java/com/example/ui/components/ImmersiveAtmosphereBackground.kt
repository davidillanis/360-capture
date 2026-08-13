package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ImmersiveDeepPurple
import com.example.ui.theme.ImmersiveLilac

@Composable
fun ImmersiveAtmosphereBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Ambient Top-Left Lilac Aura
        Box(
            modifier = Modifier
                .offset(x = (-80).dp, y = (-80).dp)
                .size(300.dp)
                .alpha(0.22f)
                .blur(100.dp)
                .background(ImmersiveLilac, CircleShape)
        )

        // Ambient Bottom-Right Deep Purple Aura
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .size(260.dp)
                .alpha(0.35f)
                .blur(90.dp)
                .background(ImmersiveDeepPurple, CircleShape)
        )

        content()
    }
}
