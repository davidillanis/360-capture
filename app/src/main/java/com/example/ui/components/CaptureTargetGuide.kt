package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.capture.CaptureTarget
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import kotlin.math.abs
import kotlin.math.min

/** The point is projected into the same FIT_CENTER image rectangle as the camera preview. */
@Composable
fun CaptureTargetGuide(pose: CameraPose?, target: CaptureTarget?, intrinsics: CameraIntrinsics?, progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = 28.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.85f), radius, center, style = Stroke(2.dp.toPx()))
        if (pose == null || target == null || intrinsics == null) return@Canvas
        val ray = target.direction
        val depth = ray.dot(pose.forward)
        val scale = min(size.width / intrinsics.width, size.height / intrinsics.height)
        val x = ray.dot(pose.right)
        val y = -ray.dot(pose.up)
        val safeDepth = depth.coerceAtLeast(0.08)
        var dx = (intrinsics.fx * x / safeDepth * scale).toFloat()
        val dy = (intrinsics.fy * y / safeDepth * scale).toFloat()
        if (depth < 0 && abs(dx) + abs(dy) < 1f) dx = size.width
        val limitX = size.width * 0.38f
        val limitY = size.height * 0.22f
        val shrink = maxOf(1f, abs(dx) / limitX, abs(dy) / limitY)
        val point = center + Offset(dx / shrink, dy / shrink)
        val color = if (progress > 0f) Color(0xFF65E4AD) else Color(0xFFFFCB66)
        drawLine(color.copy(alpha = 0.5f), center, point, strokeWidth = 2.dp.toPx())
        drawCircle(color, 10.dp.toPx(), point)
        drawCircle(Color.Black.copy(alpha = 0.6f), 12.dp.toPx(), point, style = Stroke(2.dp.toPx()))
        drawArc(color, -90f, progress * 360f, false,
            topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
            style = Stroke(4.dp.toPx()))
    }
}
