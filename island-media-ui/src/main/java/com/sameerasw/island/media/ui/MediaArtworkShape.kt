package com.sameerasw.island.media.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Cut only the artwork: the badge's clear ring reveals the background and particles below. */
internal class MediaArtworkShape(private val corner: Dp, private val badge: Boolean, private val onEnd: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val radius = corner.toPx().coerceIn(0f, minOf(size.width, size.height) / 2f)
        val rounded = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
        if (!badge) return@with Outline.Generic(rounded)
        // Matches the 32dp icon at bottom +/-4dp; 4dp transparent clearance around it.
        val centre = Offset(if (onEnd) 12.dp.toPx() else size.width - 12.dp.toPx(), size.height - 12.dp.toPx())
        val cutout = Path().apply { addOval(Rect(centre, 20.dp.toPx())) }
        Outline.Generic(Path.combine(PathOperation.Difference, rounded, cutout))
    }
}
