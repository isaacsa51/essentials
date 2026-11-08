package com.sameerasw.island.media.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class MediaActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: (() -> Unit)?,
    val like: (() -> Unit)?,
    val progress: () -> Float,
    val canSeek: () -> Boolean = { false },
    val seekTo: (Float) -> Unit = {},
)

enum class MediaPlayerStyle(val key: String) {
    Default("default"),
    ArtControls("art_controls");

    companion object {
        fun from(key: String?): MediaPlayerStyle = entries.firstOrNull { it.key == key } ?: Default
    }
}

data class MediaPlayerState(
    val title: String,
    val artist: String,
    val artwork: ImageBitmap?,
    val accent: Color,
    val playing: Boolean,
    val liked: Boolean,
    val appIcon: ImageBitmap? = null,
    val canSkipBack: Boolean = false,
    val canSkipForward: Boolean = false,
    val style: MediaPlayerStyle = MediaPlayerStyle.Default,
    val onAccent: Color = Color.Black,
    val surface: MediaPlayerSurface = MediaPlayerSurface(),
)

/** Live pulse is supplied by the host; this renderer never starts its own pulse clock. */
class MediaPlayerSurface(
    val corner: Dp = 24.dp,
    val pulse: () -> Float = { 0f },
    val blurEnabled: Boolean = true,
    val topInset: Dp = 8.dp,
    val geometry: (() -> MediaVisualGeometry)? = null,
    val camera: (() -> MediaCameraGeometry)? = null,
)

/** Card outline in the independent visual canvas; all measurements are physical pixels. */
data class MediaVisualGeometry(val bounds: Rect, val cornerRadius: Float)

/** Fixed camera geometry in the same draw canvas as the surface; never follows card motion. */
data class MediaCameraGeometry(val center: Offset, val radius: Float)

data class MediaPlayerLayout(
    val expandedCorner: Dp = 24.dp,
    val expandedOutset: Dp = 0.dp,
    val growDirection: Int = 0,
)

enum class MediaPlayerIcon { Play, Pause, Liked, Like, Previous, Next }

/** Host-provided primitives preserve the app's typography, icons and haptics. */
interface MediaPlayerComponents {
    @Composable
    fun Text(text: String, title: Boolean, modifier: Modifier)

    @Composable
    fun Icon(icon: MediaPlayerIcon, tint: Color, size: Dp)

    @Composable
    fun Equalizer(playing: Boolean, color: Color, size: Dp, outline: Color)

    @Composable
    fun SeekBar(
        value: Float,
        color: Color,
        enabled: Boolean,
        wavy: Boolean,
        modifier: Modifier,
        onInteraction: () -> Unit,
        onSeek: (Float) -> Unit,
    )
}

class MediaPlayerHost(
    val openApp: () -> Unit = {},
    val keepAlive: () -> Unit = {},
    val buttonHaptic: () -> Unit = {},
)

class MediaPlayerMotion(
    val contentIn: FiniteAnimationSpec<Float> = tween(220, delayMillis = 40),
    val contentOut: FiniteAnimationSpec<Float> = tween(120),
    val fastSpatial: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.7f, stiffness = 650f),
)

class MediaPlayerEnvironment(
    val components: MediaPlayerComponents,
    val host: MediaPlayerHost = MediaPlayerHost(),
    val motion: MediaPlayerMotion = MediaPlayerMotion(),
)

class MediaPlayerContentScope(
    val state: MediaPlayerState,
    val actions: MediaActions,
    val layout: MediaPlayerLayout,
    val environment: MediaPlayerEnvironment,
    val progress: Float,
    val canSeek: Boolean,
    val seekTo: (Float) -> Unit,
)
