package com.sameerasw.island.media.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class CapsuleParticleGeometryTest {
    @Test fun insetUsesCardBoundsInsteadOfEffectViewport() {
        val shape = CapsuleParticleGeometry.create(Size(496f, 224f), 32f, 32f)!!
        assertEquals(Rect(32f, 32f, 464f, 192f), shape.rect)
        assertTrue(shape.signedDistance(shape.rect.center) < 0f)
        assertEquals(0f, shape.signedDistance(Offset(248f, 32f)), 0.001f)
        assertEquals(12f, shape.signedDistance(Offset(248f, 20f)), 0.001f)
        assertTrue(shape.signedDistance(shape.rect.topLeft) > 0f)
    }

    @Test fun perimeterSamplesLieOnBoundaryAndNormalsPointOutward() {
        for (radius in listOf(0f, 24f, 80f)) {
            val shape = CapsuleParticleGeometry.create(Size(496f, 224f), 32f, radius)!!
            repeat(257) { i ->
                val (point, normal) = shape.sample(shape.perimeter * i / 257f)
                assertEquals(0f, shape.signedDistance(point), 0.002f)
                assertEquals(1f, normal.getDistance(), 0.001f)
                assertEquals(3f, shape.signedDistance(point + normal * 3f), 0.002f)
            }
            assertEquals(shape.sample(0f).first.x, shape.sample(shape.perimeter).first.x, 0.001f)
            assertEquals(shape.sample(0f).first.y, shape.sample(-shape.perimeter).first.y, 0.001f)
        }
    }

    @Test fun circleAndExplicitLocalRectAreSupported() {
        val circle = CapsuleParticleGeometry.create(Size(200f, 200f), 0f, 999f, Rect(50f, 50f, 150f, 150f))!!
        assertEquals(50f, circle.radius)
        repeat(32) { assertEquals(0f, circle.signedDistance(circle.sample(it * circle.perimeter / 32f).first), 0.001f) }
    }

    @Test fun invalidGeometryAndActivationDoNotReachShader() {
        assertNull(CapsuleParticleGeometry.create(Size.Zero, 0f, 24f))
        assertNull(CapsuleParticleGeometry.create(Size(40f, 40f), 32f, 24f))
        assertNull(CapsuleParticleGeometry.create(Size(Float.NaN, 40f), 0f, 24f))
        assertEquals(0f, particleActivation(Float.NaN))
        assertEquals(0f, particleActivation(Float.POSITIVE_INFINITY))
        assertEquals(0f, particleActivation(-2f))
        assertEquals(1f, particleActivation(2f))
    }
}
