package com.example.capture

import com.example.stitching.CameraPose
import kotlin.math.abs
import kotlin.math.atan2

enum class CaptureCue(val title: String, val hint: String) {
    WAITING("Preparando la guía…", "Mantén el teléfono a la altura de tus ojos."),
    RIGHT("Gira a la derecha", "Lleva el punto amarillo al círculo, sin cambiar de sitio."),
    LEFT("Gira a la izquierda", "Lleva el punto amarillo al círculo, sin cambiar de sitio."),
    UP("Apunta más arriba", "Inclina el teléfono hacia arriba, sin levantar la cámara de su sitio."),
    DOWN("Apunta más abajo", "Inclina el teléfono hacia abajo, sin bajar la cámara de su sitio."),
    LEVEL("Endereza el teléfono", "Quita la inclinación lateral para que se tome la foto."),
    HOLD("Quédate quieto", "La foto se toma sola al completar el círculo.")
}

/** Directions are relative to the camera preview, including rotated screens and the poles. */
fun captureCue(pose: CameraPose?, target: CaptureTarget?): CaptureCue {
    if (pose == null || target == null) return CaptureCue.WAITING
    if (angularDistance(pose.forward, target.direction) <= 3.0) {
        val roll = Math.toDegrees(atan2(pose.right.z, pose.up.z))
        return if (abs(target.elevation) <= 80 && abs(roll) > 8.0) CaptureCue.LEVEL else CaptureCue.HOLD
    }
    val horizontal = target.direction.dot(pose.right)
    val vertical = target.direction.dot(pose.up)
    // An exactly opposite target has no unique shortest turn; consistently choose right.
    if (abs(horizontal) + abs(vertical) < 1e-6) return CaptureCue.RIGHT
    return if (abs(horizontal) >= abs(vertical)) {
        if (horizontal >= 0) CaptureCue.RIGHT else CaptureCue.LEFT
    } else {
        if (vertical >= 0) CaptureCue.UP else CaptureCue.DOWN
    }
}

fun captureStage(target: CaptureTarget?): String = when {
    target == null -> "Recorrido completo"
    target.band == "Completar hueco" -> "Últimos espacios"
    target.elevation > 0 -> "2 de 3 · Hacia arriba"
    target.elevation < 0 -> "3 de 3 · Hacia abajo"
    else -> "1 de 3 · A tu alrededor"
}
