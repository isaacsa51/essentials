package com.sameerasw.island.media.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/** Background and effects are hosted separately from the interactive foreground. */
interface MediaPlayerRenderer {
    val usesLayeredSurface: Boolean get() = false
    val effectOutset: Dp get() = MediaParticleOutset

    @Composable
    fun Underlay(state: MediaPlayerState, modifier: Modifier) {}

    @Composable
    fun Background(state: MediaPlayerState, modifier: Modifier, fallback: @Composable (Modifier) -> Unit) {
        fallback(modifier)
    }

    @Composable
    fun Effects(state: MediaPlayerState, modifier: Modifier) {}

    @Composable
    fun Content(scope: MediaPlayerContentScope) {
        DefaultMediaPlayerForeground(scope)
    }
}

/** Stable renderer for Brief and for comparing against the custom player. */
object DefaultMediaPlayerRenderer : MediaPlayerRenderer

@Composable
fun MediaPlayerBackdrop(
    state: MediaPlayerState,
    renderer: MediaPlayerRenderer,
    modifier: Modifier = Modifier,
    background: @Composable (Modifier) -> Unit,
) {
    Box(modifier, propagateMinConstraints = true) {
        renderer.Underlay(state, Modifier.fillMaxSize())
        renderer.Background(state, Modifier.fillMaxSize(), background)
        val effectModifier = if (renderer.usesLayeredSurface && state.surface.geometry == null) Modifier.layout { measurable, constraints ->
            val inset = renderer.effectOutset.roundToPx()
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val child = measurable.measure(Constraints.fixed(width + inset * 2, height + inset * 2))
            layout(width, height) { child.place(-inset, -inset) }
        } else Modifier
        renderer.Effects(state, Modifier.fillMaxSize().then(effectModifier))
    }
}
