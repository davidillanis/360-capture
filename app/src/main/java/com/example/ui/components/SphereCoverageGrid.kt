package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.SphereGrid
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.SurfaceContainerHighest

/**
 * Representa la cobertura de la esfera 360° como una rejilla de celdas
 * (filas = elevación, columnas = azimut). Cada foto tomada rellena la
 * celda hacia la que apuntaba el dispositivo.
 */
@Composable
fun SphereCoverageGrid(
    coveredCells: Set<Int>,
    aimCell: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = GlassBackground,
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("sphere_coverage_grid"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in 0 until SphereGrid.PITCH_ROWS) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in 0 until SphereGrid.AZIMUTH_COLS) {
                    val cell = row * SphereGrid.AZIMUTH_COLS + col
                    val covered = cell in coveredCells
                    val isAim = cell == aimCell
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .shadow(
                                elevation = if (covered || isAim) 8.dp else 0.dp,
                                shape = CircleShape,
                                spotColor = ImmersiveLilac,
                                ambientColor = ImmersiveLilac
                            )
                            .background(
                                color = when {
                                    isAim -> Color.White
                                    covered -> ImmersiveLilac
                                    else -> SurfaceContainerHighest
                                },
                                shape = CircleShape
                            )
                            .border(
                                width = if (isAim) 1.5.dp else 0.dp,
                                color = ImmersiveLilac,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
