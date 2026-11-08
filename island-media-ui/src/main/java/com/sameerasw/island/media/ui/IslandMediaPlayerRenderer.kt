package com.sameerasw.island.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * The layered Island Media player.
 *
 * @param particles Draws the particle field while the island pulses.
 * @param translucent Lets what's behind the island show through the surface; false puts an opaque card under it.
 *
 * The blurred artwork background is the host's `fallback`: it draws under the surface when the host provides one.
 */
data class LayeredMediaPlayerRenderer(
    val particles: Boolean = true,
    val translucent: Boolean = true,
) : MediaPlayerRenderer {
    override val usesLayeredSurface: Boolean get() = true

    @Composable
    override fun Underlay(state: MediaPlayerState, modifier: Modifier) {
        MediaTopShade(state, modifier)
    }

    @Composable
    override fun Background(
        state: MediaPlayerState,
        modifier: Modifier,
        fallback: @Composable (Modifier) -> Unit,
    ) {
        Box(modifier) {
            if (!translucent) CardLayer(state) { Box(it.background(Color.Black)) }
            // On the detached canvas the modifier spans the display, so the artwork is kept to the card
            CardLayer(state) { fallback(it) }
            MediaSurface(state, Modifier.fillMaxSize())
        }
    }

    @Composable
    override fun Effects(state: MediaPlayerState, modifier: Modifier) {
        if (!particles) return
        GeminiParticlePill(
            modifier = modifier,
            color = state.accent,
            cornerRadius = state.surface.corner,
            activation = state.surface.pulse,
            enabled = state.surface.blurEnabled,
            geometry = state.surface.geometry,
            protectedCamera = state.surface.camera,
        )
    }

    @Composable
    override fun Content(scope: MediaPlayerContentScope) {
        if (scope.state.style == MediaPlayerStyle.ArtControls) DefaultMediaPlayerForeground(scope)
        else IslandMediaForeground(scope)
    }
}

/** Customization entry point used by the expanded Island Media item, with every layer on. */
val IslandMediaPlayerRenderer: MediaPlayerRenderer = LayeredMediaPlayerRenderer()

/**
 * Lays [content] out at the live card outline (the whole canvas when there is none), clipped to the card's corners.
 * Geometry is read during layout and drawing, so the layer follows the card while it animates.
 */
@Composable
private fun CardLayer(state: MediaPlayerState, content: @Composable (Modifier) -> Unit) {
    val shape = remember(state.surface) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val radius = (state.surface.geometry?.invoke()?.cornerRadius ?: with(density) { state.surface.corner.toPx() })
                    .coerceIn(0f, minOf(size.width, size.height) / 2f)
                return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
            }
        }
    }
    Box(
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val rect = state.surface.geometry?.invoke()?.bounds
                ?: Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val child = measurable.measure(
                Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.placeWithLayer(rect.left.roundToInt(), rect.top.roundToInt())
            }
        },
    ) {
        content(Modifier.fillMaxSize().clip(shape))
    }
}
