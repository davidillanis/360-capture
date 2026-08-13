package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.IsoMode
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ImmersiveLilac
import com.example.ui.theme.OnSurface
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.Typography

@Composable
fun QuickSettingsPills(
    evValue: Float,
    isHdr: Boolean,
    isoMode: IsoMode,
    onEvClick: () -> Unit,
    onHdrToggle: () -> Unit,
    onIsoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // EV Pill
        val evText = if (evValue >= 0) "EV +${String.format("%.1f", evValue)}" else "EV ${String.format("%.1f", evValue)}"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = if (evValue != 0f) ImmersiveLilac else GlassBorder,
                    shape = RoundedCornerShape(9999.dp)
                )
                .clickable(onClick = onEvClick)
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("ev_pill_button")
        ) {
            Text(
                text = if (evValue == 0f) "EV 0.0" else evText,
                color = if (evValue != 0f) ImmersiveLilac else OnSurface,
                style = Typography.labelMedium
            )
        }

        // HDR Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = if (isHdr) ImmersiveLilac else GlassBorder,
                    shape = RoundedCornerShape(9999.dp)
                )
                .clickable(onClick = onHdrToggle)
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("hdr_pill_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HdrOn,
                    contentDescription = "HDR Mode",
                    tint = if (isHdr) ImmersiveLilac else OnSurface,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "HDR",
                    color = if (isHdr) ImmersiveLilac else OnSurface,
                    style = Typography.labelMedium
                )
            }
        }

        // ISO Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = if (isoMode != IsoMode.AUTO) ImmersiveLilac else GlassBorder,
                    shape = RoundedCornerShape(9999.dp)
                )
                .clickable(onClick = onIsoClick)
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("iso_pill_button")
        ) {
            Text(
                text = isoMode.label,
                color = if (isoMode != IsoMode.AUTO) ImmersiveLilac else OnSurface,
                style = Typography.labelMedium
            )
        }
    }
}
