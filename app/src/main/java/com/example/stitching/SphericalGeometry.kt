package com.example.stitching

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** World coordinates: east (x), north (y), up (z). Angles are in degrees. */
data class Direction(val x: Double, val y: Double, val z: Double) {
    fun dot(other: Direction): Double = x * other.x + y * other.y + z * other.z
    operator fun times(scale: Double) = Direction(x * scale, y * scale, z * scale)
    operator fun plus(other: Direction) = Direction(x + other.x, y + other.y, z + other.z)
    val azimuth: Double get() = (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0
    val elevation: Double get() = Math.toDegrees(asin(z.coerceIn(-1.0, 1.0)))
}

/** Basis of the upright, back-camera JPEG, expressed in world coordinates. */
data class CameraPose(val right: Direction, val up: Direction, val forward: Direction) {
    companion object {
        fun fromDeviceRotation(matrix: FloatArray, displayRotationDegrees: Int): CameraPose {
            require(matrix.size == 9)
            require(displayRotationDegrees in listOf(0, 90, 180, 270))
            val x = Direction(matrix[0].toDouble(), matrix[3].toDouble(), matrix[6].toDouble())
            val y = Direction(matrix[1].toDouble(), matrix[4].toDouble(), matrix[7].toDouble())
            val z = Direction(matrix[2].toDouble(), matrix[5].toDouble(), matrix[8].toDouble())
            val rotation = Math.toRadians(displayRotationDegrees.toDouble())
            return CameraPose(
                right = x * cos(rotation) + y * sin(rotation),
                up = y * cos(rotation) + x * -sin(rotation),
                forward = z * -1.0
            )
        }

        fun lookingAt(azimuth: Double, elevation: Double, roll: Double = 0.0): CameraPose {
            val yaw = Math.toRadians(azimuth)
            val pitch = Math.toRadians(elevation)
            val r = Math.toRadians(roll)
            val right = Direction(cos(yaw), -sin(yaw), 0.0)
            val up = Direction(-sin(yaw) * sin(pitch), -cos(yaw) * sin(pitch), cos(pitch))
            return CameraPose(
                right * cos(r) + up * sin(r),
                up * cos(r) + right * -sin(r),
                Direction(sin(yaw) * cos(pitch), cos(yaw) * cos(pitch), sin(pitch))
            )
        }
    }
}

data class CameraIntrinsics(
    val width: Int,
    val height: Int,
    val fx: Double,
    val fy: Double,
    val cx: Double = (width - 1) / 2.0,
    val cy: Double = (height - 1) / 2.0
) {
    init {
        require(width > 1 && height > 1)
        require(fx.isFinite() && fy.isFinite() && fx > 0 && fy > 0)
        require(cx.isFinite() && cy.isFinite())
    }

    fun scaledTo(width: Int, height: Int): CameraIntrinsics {
        val sx = width.toDouble() / this.width
        val sy = height.toDouble() / this.height
        return CameraIntrinsics(width, height, fx * sx, fy * sy, (cx + 0.5) * sx - 0.5, (cy + 0.5) * sy - 0.5)
    }

    companion object {
        /** Account for the JPEG's upright rotation and the sensor's aspect-ratio crop. */
        fun fromSensor(
            width: Int,
            height: Int,
            sensorWidthMm: Double,
            sensorHeightMm: Double,
            focalLengthMm: Double,
            sensorRotationDegrees: Int
        ): CameraIntrinsics {
            require(sensorWidthMm > 0 && sensorHeightMm > 0 && focalLengthMm > 0)
            val swap = sensorRotationDegrees % 180 != 0
            val sw = if (swap) sensorHeightMm else sensorWidthMm
            val sh = if (swap) sensorWidthMm else sensorHeightMm
            val aspect = width.toDouble() / height
            val croppedWidth = minOf(sw, sh * aspect)
            val croppedHeight = minOf(sh, sw / aspect)
            return CameraIntrinsics(width, height, focalLengthMm * width / croppedWidth, focalLengthMm * height / croppedHeight)
        }
    }
}

data class ImagePoint(val x: Double, val y: Double)

object SphericalGeometry {
    /** Pixel centres. Longitude wraps naturally through vectors, including 0/360 and +/-180. */
    fun ray(x: Int, y: Int, width: Int, height: Int): Direction {
        val longitude = ((x + 0.5) / width * 2.0 - 1.0) * PI
        val latitude = (0.5 - (y + 0.5) / height) * PI
        return Direction(sin(longitude) * cos(latitude), cos(longitude) * cos(latitude), sin(latitude))
    }

    fun project(ray: Direction, pose: CameraPose, intrinsics: CameraIntrinsics, margin: Double = 0.0): ImagePoint? {
        val depth = ray.dot(pose.forward)
        if (depth <= 1e-8) return null // Never project the hemisphere behind the camera.
        val u = intrinsics.cx + intrinsics.fx * ray.dot(pose.right) / depth
        val v = intrinsics.cy - intrinsics.fy * ray.dot(pose.up) / depth
        if (u < margin || v < margin || u > intrinsics.width - 1 - margin || v > intrinsics.height - 1 - margin) return null
        return ImagePoint(u, v)
    }

    fun cameraRay(x: Double, y: Double, pose: CameraPose, intrinsics: CameraIntrinsics): Direction {
        val ray = pose.forward + pose.right * ((x - intrinsics.cx) / intrinsics.fx) +
            pose.up * ((intrinsics.cy - y) / intrinsics.fy)
        return ray * (1.0 / sqrt(ray.dot(ray)))
    }
}
