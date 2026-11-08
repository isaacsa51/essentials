package com.sameerasw.island.media.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Responsive foreground; the app still owns expanded width, roundness, scale and font scale. */
@Composable
internal fun IslandMediaForeground(scope: MediaPlayerContentScope) {
    val state = scope.state
    val env = scope.environment
    val components = env.components
    BoxWithConstraints(Modifier.fillMaxWidth().padding(scope.layout.expandedOutset).padding(12.dp)) {
        // Keep the 136dp design art at roomy widths; reserve usable controls at smaller widths.
        val narrow = maxWidth < 320.dp
        val artSize = if (narrow) minOf(112.dp, maxWidth * 0.34f) else minOf(136.dp, maxWidth * 0.38f)
        val infoHeight = maxOf(artSize, 112.dp)
        val controlGap = if (narrow) 4.dp else 8.dp
        val skipSize = if (narrow) 20.dp else 24.dp
        val artOnEnd = scope.layout.growDirection > 0
        val artwork: @Composable () -> Unit = {
            Box(Modifier.size(artSize)) {
                AnimatedContent(
                    targetState = state.artwork,
                    transitionSpec = { fadeIn(env.motion.contentIn) togetherWith fadeOut(env.motion.contentOut) },
                    label = "mediaCover",
                ) { image ->
                    Box(
                        Modifier.fillMaxSize()
                            .clip(MediaArtworkShape((scope.layout.expandedCorner - 12.dp).coerceAtLeast(6.dp),
                                state.appIcon != null, artOnEnd))
                            .background(state.accent.copy(alpha = 0.1f))
                            .clickable {
                                env.host.buttonHaptic()
                                if (state.style == MediaPlayerStyle.ArtControls) scope.actions.playPause()
                                else scope.actions.like?.invoke() ?: env.host.openApp()
                            },
                    ) {
                        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                state.appIcon?.let { icon ->
                    Image(
                        icon, null,
                        Modifier.align(if (artOnEnd) Alignment.BottomStart else Alignment.BottomEnd)
                            .offset(if (artOnEnd) (-4).dp else 4.dp, 4.dp)
                            .size(32.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        val information: @Composable () -> Unit = {
            Column(
                Modifier.height(infoHeight).padding(start = if (narrow) 12.dp else 16.dp,
                    end = if (narrow) 4.dp else 8.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        components.Text(state.title, true, Modifier.fillMaxWidth())
                        components.Text(state.artist, false, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.size(controlGap))
                    Box(
                        Modifier.size(if (narrow) 44.dp else 52.dp, if (narrow) 36.dp else 40.dp).clip(CircleShape).background(state.accent)
                            .clickable {
                                env.host.buttonHaptic()
                                scope.actions.playPause()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        components.Icon(if (state.playing) MediaPlayerIcon.Pause else MediaPlayerIcon.Play, state.onAccent, 24.dp)
                    }
                }
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    scope.actions.previous?.let { action ->
                        MediaSkip(MediaPlayerIcon.Previous, state.canSkipBack, scope, skipSize, action)
                        Spacer(Modifier.size(controlGap))
                    }
                    Box(Modifier.weight(1f)) {
                        components.SeekBar(scope.progress, state.accent, scope.canSeek, state.playing, Modifier,
                            env.host.keepAlive, scope.seekTo)
                    }
                    Spacer(Modifier.size(controlGap))
                    MediaSkip(MediaPlayerIcon.Next, state.canSkipForward, scope, skipSize, scope.actions.next)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (artOnEnd) {
                Box(Modifier.weight(1f)) { information() }
                artwork()
            } else {
                artwork()
                Box(Modifier.weight(1f)) { information() }
            }
        }
    }
}

@Composable
private fun MediaSkip(icon: MediaPlayerIcon, enabled: Boolean, scope: MediaPlayerContentScope, size: Dp, action: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(enabled = enabled) {
            scope.environment.host.buttonHaptic()
            action()
        }, contentAlignment = Alignment.Center,
    ) {
        scope.environment.components.Icon(icon, scope.state.accent.copy(alpha = if (enabled) 1f else 0.38f), 20.dp)
    }
}
