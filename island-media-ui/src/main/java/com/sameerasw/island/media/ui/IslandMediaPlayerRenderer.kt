package com.sameerasw.island.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Customization entry point used only by the expanded Island Media item. */
object IslandMediaPlayerRenderer : MediaPlayerRenderer {
    override val usesLayeredSurface = true

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
        MediaSurface(state, modifier)
    }

    @Composable
    override fun Effects(state: MediaPlayerState, modifier: Modifier) {
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
