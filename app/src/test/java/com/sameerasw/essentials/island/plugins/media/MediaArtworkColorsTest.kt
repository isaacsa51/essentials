package com.sameerasw.essentials.island.plugins.media

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaArtworkColorsTest {
    @Test
    fun artworkSeedProducesDarkMaterialRoles() {
        for (seed in intArrayOf(0xFF005AC1.toInt(), 0xFF008000.toInt(), 0xFF9B005D.toInt())) {
            val colors = mediaColorsFromSeed(seed)
            assertEquals(80.0, Hct.fromInt(colors.primary.toArgb()).tone, 0.6)
            assertEquals(20.0, Hct.fromInt(colors.onPrimary.toArgb()).tone, 0.6)
            val contrast = (colors.primary.luminance() + 0.05f) / (colors.onPrimary.luminance() + 0.05f)
            assertTrue("Dark primary/onPrimary must remain readable", contrast > 7f)
        }
    }

    @Test
    fun differentCoverSeedsDoNotReuseTheSamePalette() {
        val blue = mediaColorsFromSeed(0xFF005AC1.toInt())
        val green = mediaColorsFromSeed(0xFF008000.toInt())
        assertNotEquals(blue.primary, green.primary)
        assertNotEquals(blue.onPrimary, green.onPrimary)
    }
}
