package com.sameerasw.essentials.island.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandedSurfaceStyleTest {
    @Test
    fun layeredSurfaceIsOptInAndSurvivesInlineBubbleArrangement() {
        val defaultItem = IslandItem("ordinary", 1, CompactPlacement.Dynamic, listOf(CompactCell("one") {}), chip = false)
        assertEquals(ExpandedSurfaceStyle.Default, defaultItem.expandedSurfaceStyle)

        val media = IslandItem("media", 1, CompactPlacement.Dynamic, listOf(CompactCell("art") {}),
            chip = false, expandedSurfaceStyle = ExpandedSurfaceStyle.Layered)
        assertEquals(ExpandedSurfaceStyle.Layered, media.withInlineBubble(CompactCell("bubble") {}).expandedSurfaceStyle)
    }
}
