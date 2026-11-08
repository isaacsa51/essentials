package com.sameerasw.island.media.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal val MediaCameraClearance = 4.dp

internal data class MediaSurfaceGradient(val center: Offset, val radiusX: Float, val radiusY: Float, val stops: FloatArray)

/** The ellipse's shortest radius must contain the entire circular camera in its opaque stop. */
internal fun mediaSurfaceGradient(rect: Rect, camera: MediaCameraGeometry?, clearance: Float): MediaSurfaceGradient {
    val blackRadius = (camera?.radius?.coerceAtLeast(0f) ?: 0f) + clearance
    val radiusX = max(rect.width * 1.525f, blackRadius * 1.05f).coerceAtLeast(1f)
    val radiusY = max(rect.height * 1.525f, blackRadius * 1.05f).coerceAtLeast(1f)
    val first = max(0.05f, blackRadius / min(radiusX, radiusY)).coerceAtMost(0.96f)
    val stops = floatArrayOf(0.05f, 0.25f, 0.45f, 0.65f, 0.85f, 1f)
        .map { first + (it - 0.05f) / 0.95f * (1f - first) }.toFloatArray()
    return MediaSurfaceGradient(camera?.center ?: Offset(rect.center.x, rect.top + rect.height * 0.175f), radiusX, radiusY, stops)
}

/** A post-blur mask keeps the complete top edge/corners and camera region free of inner light. */
internal fun mediaShadowClearUntil(rect: Rect, cornerRadius: Float, camera: MediaCameraGeometry?, clearance: Float): Float =
    max(rect.top + cornerRadius, camera?.takeIf { it.radius > 0f }?.let { it.center.y + it.radius + clearance } ?: rect.top)
