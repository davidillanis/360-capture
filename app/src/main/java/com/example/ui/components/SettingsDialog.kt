package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ProjectionMode
import com.example.model.StitchingEngine
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.Typography

@Composable
fun SettingsDialog(
    selectedEngine: StitchingEngine,
    selectedProjection: ProjectionMode,
    onEngineSelected: (StitchingEngine) -> Unit,
    onProjectionSelected: (ProjectionMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceContainer)
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("settings_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = ImmersiveLilac,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "360Capture Settings",
                            style = Typography.titleMedium,
                            color = OnSurface,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stitching Engine Section
                Text(
                    text = "STITCHING ENGINE",
                    style = Typography.labelSmall,
                    color = ImmersiveLilac,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                listOf(StitchingEngine.OPENCV).forEach { engine ->
                    val isSelected = engine == selectedEngine
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) SurfaceContainerHigh else SurfaceContainerHighest.copy(alpha = 0.4f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ImmersiveLilac else GlassBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onEngineSelected(engine) }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engine.label,
                                    color = if (isSelected) ImmersiveLilac else OnSurface,
                                    style = Typography.bodyMedium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = engine.subtitle,
                                    color = Secondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ImmersiveLilac,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Projection Mode
                Text(
                    text = "PROJECTION GEOMETRY",
                    style = Typography.labelSmall,
                    color = ImmersiveLilac,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                listOf(ProjectionMode.EQUIRECTANGULAR).forEach { projection ->
                    val isSelected = projection == selectedProjection
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) SurfaceContainerHigh else SurfaceContainerHighest.copy(alpha = 0.4f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ImmersiveLilac else GlassBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onProjectionSelected(projection) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = projection.label,
                                color = if (isSelected) ImmersiveLilac else OnSurface,
                                style = Typography.bodyMedium,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ImmersiveLilac,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExposureDialog(
    currentEv: Float,
    onEvChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceContainer)
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("exposure_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exposure Compensation",
                        style = Typography.titleMedium,
                        color = OnSurface,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Secondary)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val formattedEv = if (currentEv >= 0) "+${String.format("%.1f", currentEv)} EV" else "${String.format("%.1f", currentEv)} EV"
                Text(
                    text = formattedEv,
                    style = Typography.displayLarge,
                    color = ImmersiveLilac,
                    fontSize = 28.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = currentEv,
                    onValueChange = onEvChange,
                    valueRange = -2.0f..2.0f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = ImmersiveLilac,
                        activeTrackColor = ImmersiveLilac,
                        inactiveTrackColor = SurfaceContainerHighest
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-2.0", style = Typography.labelSmall, color = Secondary)
                    Text("0.0", style = Typography.labelSmall, color = Secondary)
                    Text("+2.0", style = Typography.labelSmall, color = Secondary)
                }
            }
        }
    }
}
