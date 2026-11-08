package com.sameerasw.island.media.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MediaSurfaceMathTest {
    @Test fun opaqueEllipseContainsTheWholeLensAndClearanceAtEveryAngle() {
        for (rect in listOf(Rect(8f, 8f, 440f, 168f), Rect(200f, 40f, 260f, 80f), Rect(0f, 0f, 5f, 3f))) {
            for (radius in listOf(14f, 32f)) {
                val camera = MediaCameraGeometry(Offset(224f, 28f), radius)
                val gradient = mediaSurfaceGradient(rect, camera, 4f)
                assertEquals(camera.center, gradient.center)
                repeat(360) { degrees ->
                    val angle = degrees * PI / 180
                    val x = cos(angle) * (radius + 4f) / gradient.radiusX
                    val y = sin(angle) * (radius + 4f) / gradient.radiusY
                    assertTrue("lens exits opaque stop at $degrees degrees", hypot(x, y) <= gradient.stops.first() + 0.000001)
                }
                assertTrue(gradient.stops.toList().zipWithNext().all { (a, b) -> a < b })
                assertEquals(1f, gradient.stops.last(), 0.000001f)
            }
        }
    }

    @Test fun cameraAnchorDoesNotMoveWithTheCard() {
        val camera = MediaCameraGeometry(Offset(62f, 35f), 14f)
        val expanded = mediaSurfaceGradient(Rect(16f, 8f, 448f, 168f), camera, 4f)
        val moving = mediaSurfaceGradient(Rect(40f, -12f, 200f, 48f), camera, 4f)
        assertEquals(camera.center, expanded.center)
        assertEquals(camera.center, moving.center)
        assertNotEquals(expanded.radiusY, moving.radiusY)
    }

    @Test fun shadowSuppressionCoversTopCornersAndTheCameraBottom() {
        val camera = MediaCameraGeometry(Offset(224f, 28f), 14f)
        assertEquals(46f, mediaShadowClearUntil(Rect(8f, 8f, 440f, 168f), 32f, camera, 4f), 0f)
        assertEquals(132f, mediaShadowClearUntil(Rect(8f, 100f, 440f, 260f), 32f, camera, 4f), 0f)
        assertEquals(40f, mediaShadowClearUntil(Rect(8f, 8f, 440f, 168f), 32f, null, 4f), 0f)
    }
}
