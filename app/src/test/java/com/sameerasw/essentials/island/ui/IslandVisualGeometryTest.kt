package com.sameerasw.essentials.island.ui

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandVisualGeometryTest {
    private val compact = IntSize(80, 40)
    private val expanded = IntSize(432, 160)

    @Test fun surfaceBlendFollowsGeometryInBothDirections() {
        for (height in 40..160) {
            val live = IntSize(432, height)
            val expected = (height - 40) / 120f
            assertEquals(expected, visualExpansionAmount(live, compact, expanded), 0f)
        }
    }

    @Test fun springOvershootAndHiddenSizeCannotProduceInvalidAlpha() {
        assertEquals(0f, visualExpansionAmount(IntSize.Zero, compact, expanded), 0f)
        assertEquals(1f, visualExpansionAmount(IntSize(470, 190), compact, expanded), 0f)
        assertEquals(0f, visualExpansionAmount(compact, compact, IntSize.Zero), 0f)
    }

    @Test fun sameHeightSurfacesUseWidthForTheHandoff() {
        assertEquals(0.5f, visualExpansionAmount(IntSize(160, 40), compact, IntSize(240, 40)), 0f)
    }
}
