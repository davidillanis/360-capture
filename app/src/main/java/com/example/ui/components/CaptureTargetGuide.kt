package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.capture.CaptureTarget
import com.example.stitching.CameraIntrinsics
import com.example.stitching.CameraPose
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** The point is projected into the same FIT_CENTER image rectangle as the camera preview. */
@Composable
fun CaptureTargetGuide(pose: CameraPose?, target: CaptureTarget?, intrinsics: CameraIntrinsics?, progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = 30.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.5f), radius, center, style = Stroke(7.dp.toPx()))
        drawCircle(Color.White, radius, center, style = Stroke(3.dp.toPx()))
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
        val limitX = (size.width / 2 - 28.dp.toPx()).coerceAtLeast(1f)
        val limitY = (size.height / 2 - 28.dp.toPx()).coerceAtLeast(1f)
        val shrink = maxOf(1f, abs(dx) / limitX, abs(dy) / limitY)
        val point = center + Offset(dx / shrink, dy / shrink)
        val color = if (progress > 0f) Color(0xFF65E4AD) else Color(0xFFFFCB66)
        val delta = point - center
        val distance = sqrt(delta.x * delta.x + delta.y * delta.y)
        if (distance > radius * 2) {
            val direction = delta / distance
            val perpendicular = Offset(-direction.y, direction.x)
            val tip = point - direction * 22.dp.toPx()
            val tail = tip - direction * 20.dp.toPx()
            drawLine(color, center + direction * (radius + 10.dp.toPx()), tail, strokeWidth = 4.dp.toPx())
            drawPath(Path().apply {
                moveTo(tip.x, tip.y)
                val left = tail + perpendicular * 12.dp.toPx()
                val right = tail - perpendicular * 12.dp.toPx()
                lineTo(left.x, left.y)
                lineTo(right.x, right.y)
                close()
            }, color)
        }
        drawCircle(Color.Black.copy(alpha = 0.7f), 17.dp.toPx(), point)
        drawCircle(color, 13.dp.toPx(), point)
        drawArc(color, -90f, progress * 360f, false,
            topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
            style = Stroke(4.dp.toPx()))
    }
}
