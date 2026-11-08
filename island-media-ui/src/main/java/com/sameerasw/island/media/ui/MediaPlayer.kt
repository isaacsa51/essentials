package com.sameerasw.island.media.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

private const val SEEK_SETTLE_MS = 1500L
private val LocalMediaPlayerEnvironment = staticCompositionLocalOf<MediaPlayerEnvironment> {
    error("Media player content must be hosted by MediaPlayer")
}

@Composable
fun MediaPlayer(
    state: MediaPlayerState,
    actions: MediaActions,
    environment: MediaPlayerEnvironment,
    layout: MediaPlayerLayout = MediaPlayerLayout(),
    renderer: MediaPlayerRenderer = DefaultMediaPlayerRenderer,
) {
    val latestActions by rememberUpdatedState(actions)
    var progress by remember { mutableFloatStateOf(actions.progress()) }
    var pendingSeek by remember { mutableStateOf<Pair<Float, Long>?>(null) }
    val canSeek = actions.canSeek()
    LaunchedEffect(state.playing, state.title, state.artist) {
        while (true) {
            val actual = latestActions.progress()
            val pending = pendingSeek
            progress = if (pending != null && SystemClock.elapsedRealtime() - pending.second < SEEK_SETTLE_MS && abs(actual - pending.first) > 0.02f) {
                pending.first
            } else {
                pendingSeek = null
                actual
            }
            if (!state.playing) break
            delay(200L)
        }
    }
    val scope = MediaPlayerContentScope(state, actions, layout, environment, progress, canSeek) { target ->
        latestActions.seekTo(target)
        pendingSeek = target to SystemClock.elapsedRealtime()
        progress = target
    }
    CompositionLocalProvider(LocalMediaPlayerEnvironment provides environment) {
        renderer.Content(scope)
    }
}

/** Original layout retained as the baseline; custom renderers may replace it entirely. */
@Composable
fun DefaultMediaPlayerForeground(scope: MediaPlayerContentScope) {
    val state = scope.state
    val title = state.title
    val artist = state.artist
    val image = state.artwork
    val accent = state.accent
    val playing = state.playing
    val liked = state.liked
    val style = state.style
    val canSkipBack = state.canSkipBack
    val canSkipForward = state.canSkipForward
    val spec = scope.layout
    val actions = scope.actions
    val progress = scope.progress
    val canSeek = scope.canSeek
    val environment = scope.environment
    val components = environment.components
    val motion = environment.motion
    Box {
        val art: @Composable (Dp) -> Unit = { artSize ->
            AnimatedContent(
                targetState = image,
                transitionSpec = { fadeIn(motion.contentIn) togetherWith fadeOut(motion.contentOut) },
                label = "playerArt",
            ) { bitmap ->
                Box(
                    Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape((spec.expandedCorner - spec.expandedOutset - 12.dp).coerceAtLeast(6.dp)))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            environment.host.buttonHaptic()
                            val like = actions.like
                            when {
                                style == MediaPlayerStyle.ArtControls -> actions.playPause()
                                like != null -> like()
                                else -> environment.host.openApp()
                            }
                        },
                ) {
                    if (bitmap != null) {
                        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
            }
        }
        val seekBar: @Composable () -> Unit = {
            components.SeekBar(
                value = progress,
                color = accent,
                enabled = canSeek,
                wavy = playing,
                modifier = Modifier.padding(horizontal = 2.dp),
                onInteraction = environment.host.keepAlive,
                onSeek = scope.seekTo,
            )
        }
        val buttonContainer = accent
        val buttonInk = Color.Black
        val artOnEnd = spec.growDirection > 0
        val appIcon = state.appIcon
        val artBlock: @Composable () -> Unit = {
            Box {
                art(ART_SIZE)
                when {
                    style == MediaPlayerStyle.ArtControls -> ArtPlaybackMark(playing, accent, Modifier.align(Alignment.Center))
                    actions.like != null -> LikeMark(liked, accent, Modifier.align(Alignment.Center))
                }
                if (appIcon != null) {
                    Box(
                        Modifier
                            .align(if (artOnEnd) Alignment.BottomStart else Alignment.BottomEnd)
                            .offset(x = if (artOnEnd) (-4).dp else 4.dp, y = 4.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .padding(3.dp)
                            .clip(CircleShape),
                    ) {
                        Image(appIcon, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
            }
        }
        val titleText: @Composable () -> Unit = {
            components.Text(
                text = title,
                title = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val details: @Composable ColumnScope.() -> Unit = {
            if (style == MediaPlayerStyle.ArtControls) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    actions.like?.let { like ->
                        HeartButton(
                            liked = liked,
                            fill = accent,
                            onClick = like,
                            modifier = Modifier.align(if (spec.growDirection < 0) Alignment.TopStart else Alignment.TopEnd),
                        )
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 6.dp),
                    ) {
                        titleText()
                        components.Text(
                            text = artist,
                            title = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        titleText()
                        components.Text(
                            text = artist,
                            title = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    PlayerButton(
                        onClick = actions.playPause,
                        container = buttonContainer,
                        modifier = Modifier.size(width = 64.dp, height = 48.dp),
                    ) {
                        AnimatedContent(
                            targetState = playing,
                            transitionSpec = { fadeIn(motion.contentIn) togetherWith fadeOut(motion.contentOut) },
                            label = "playPause",
                        ) {
                            ButtonGlyph(if (it) MediaPlayerIcon.Pause else MediaPlayerIcon.Play, buttonInk, 28.dp)
                        }
                    }
                }
            }
        }
        val info: @Composable RowScope.() -> Unit = {
            Column(
                Modifier
                    .weight(1f)
                    .height(ART_SIZE),
            ) {
                details()
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.previous?.let { previous ->
                        SkipButton(MediaPlayerIcon.Previous, buttonContainer, canSkipBack, previous)
                    }
                    Box(Modifier.weight(1f)) { seekBar() }
                    SkipButton(MediaPlayerIcon.Next, buttonContainer, canSkipForward, actions.next)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spec.expandedOutset)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (artOnEnd) {
                info()
                artBlock()
            } else {
                artBlock()
                info()
            }
        }
    }
}

private val ART_SIZE = 136.dp
private const val PLAYBACK_FLASH_MS = 900L
private val ART_MARK_OUTLINE = Color.Black.copy(alpha = 0.35f)

@Composable
private fun PlayerButton(
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val environment = LocalMediaPlayerEnvironment.current
    Box(
        modifier
            .clip(CircleShape)
            .background(container)
            .clickable {
                environment.host.buttonHaptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LikeMark(liked: Boolean, fill: Color, modifier: Modifier = Modifier) {
    val motion = LocalMediaPlayerEnvironment.current.motion
    val scale by animateFloatAsState(if (liked) 1f else 0.9f, motion.fastSpatial, label = "likeMark")
    Box(
        modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = liked,
            transitionSpec = { fadeIn(motion.contentIn) togetherWith fadeOut(motion.contentOut) },
            label = "likeMarkIcon",
        ) {
            ButtonGlyph(if (it) MediaPlayerIcon.Liked else MediaPlayerIcon.Like, if (it) fill else Color.White, 32.dp)
        }
    }
}

@Composable
private fun ArtPlaybackMark(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val motion = LocalMediaPlayerEnvironment.current.motion
    var flash by remember { mutableStateOf<Boolean?>(null) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        flash = playing
        delay(PLAYBACK_FLASH_MS)
        flash = null
    }
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = flash,
            transitionSpec = { (fadeIn(motion.contentIn) + scaleIn(motion.fastSpatial, initialScale = 0.7f)) togetherWith fadeOut(motion.contentOut) },
            label = "artPlayback",
        ) { shown ->
            when (shown) {
                null -> LocalMediaPlayerEnvironment.current.components.Equalizer(playing, color, size = 30.dp, outline = ART_MARK_OUTLINE)
                true -> OutlinedGlyph(MediaPlayerIcon.Play, color, 40.dp)
                false -> OutlinedGlyph(MediaPlayerIcon.Pause, color, 40.dp)
            }
        }
    }
}

@Composable
private fun OutlinedGlyph(icon: MediaPlayerIcon, tint: Color, size: Dp) {
    Box(contentAlignment = Alignment.Center) {
        listOf(-1, 1).forEach { dx ->
            listOf(-1, 1).forEach { dy ->
                Box(Modifier.offset(dx.dp, dy.dp)) { ButtonGlyph(icon, ART_MARK_OUTLINE, size) }
            }
        }
        ButtonGlyph(icon, tint, size)
    }
}

@Composable
private fun HeartButton(liked: Boolean, fill: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMediaPlayerEnvironment.current.motion
    val environment = LocalMediaPlayerEnvironment.current
    val scale by animateFloatAsState(if (liked) 1f else 0.9f, motion.fastSpatial, label = "heartButton")
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable {
                environment.host.buttonHaptic()
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = liked,
            transitionSpec = { fadeIn(motion.contentIn) togetherWith fadeOut(motion.contentOut) },
            label = "heartButtonIcon",
        ) {
            ButtonGlyph(if (it) MediaPlayerIcon.Liked else MediaPlayerIcon.Like, if (it) fill else Color.White, 24.dp)
        }
    }
}

@Composable
private fun ButtonGlyph(icon: MediaPlayerIcon, ink: Color, size: Dp) {
    LocalMediaPlayerEnvironment.current.components.Icon(icon, ink, size)
}

@Composable
private fun SkipButton(icon: MediaPlayerIcon, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val environment = LocalMediaPlayerEnvironment.current
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) {
                environment.host.buttonHaptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        ButtonGlyph(icon, tint.copy(alpha = if (enabled) 1f else 0.38f), 20.dp)
    }
}
