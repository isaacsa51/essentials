package com.sameerasw.essentials.island.plugins.media

import com.sameerasw.essentials.island.ui.IslandSurfaceAccent
import com.sameerasw.essentials.island.ui.components.LocalIslandIconColor
import androidx.compose.foundation.layout.fillMaxSize
import com.sameerasw.essentials.island.ui.SurfaceBackdrop
import com.sameerasw.essentials.island.ui.components.ArtworkBackdrop
import com.sameerasw.essentials.island.ui.components.ArtworkBlur
import com.sameerasw.essentials.island.ui.components.MarqueeText
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.island.model.IslandExpandedScope
import com.sameerasw.essentials.island.ui.IslandHaptics
import com.sameerasw.essentials.island.ui.rememberIslandSurfaceMotion
import com.sameerasw.essentials.island.ui.IslandTextStyles
import com.sameerasw.essentials.island.ui.SurfaceBackdrop
import com.sameerasw.essentials.island.ui.DetachedSurfaceVisuals
import com.sameerasw.essentials.island.ui.LocalIslandPulse
import com.sameerasw.essentials.island.ui.components.ArtworkBackdrop
import com.sameerasw.essentials.island.ui.components.EqualizerBars
import com.sameerasw.essentials.island.ui.components.IslandSeekBar
import com.sameerasw.essentials.island.ui.components.LocalIslandIconColor
import com.sameerasw.essentials.island.ui.components.MarqueeText
import com.sameerasw.island.media.ui.DefaultMediaPlayerRenderer
import com.sameerasw.island.media.ui.MediaPlayer
import com.sameerasw.island.media.ui.MediaPlayerBackdrop
import com.sameerasw.island.media.ui.MediaPlayerComponents
import com.sameerasw.island.media.ui.MediaPlayerEnvironment
import com.sameerasw.island.media.ui.MediaPlayerHost
import com.sameerasw.island.media.ui.MediaPlayerIcon
import com.sameerasw.island.media.ui.MediaPlayerLayout
import com.sameerasw.island.media.ui.MediaPlayerMotion
import com.sameerasw.island.media.ui.MediaPlayerRenderer
import com.sameerasw.island.media.ui.MediaPlayerState
import com.sameerasw.island.media.ui.MediaPlayerSurface
import com.sameerasw.island.media.ui.MediaVisualGeometry
import com.sameerasw.island.media.ui.MediaCameraGeometry
import com.sameerasw.essentials.utils.DeviceUtils
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Adapts Island services/styles to the independent media renderer. */
@Composable
fun MediaExpanded(
    title: String,
    artist: String,
    artwork: Bitmap?,
    accent: Color,
    playing: Boolean,
    liked: Boolean,
    actions: MediaActions,
    scope: IslandExpandedScope,
    canSkipBack: Boolean = false,
    canSkipForward: Boolean = false,
    drawBackground: Boolean = true,
    packageName: String? = null,
    style: MediaPlayerStyle = MediaPlayerStyle.Default,
    // Background for the original renderer; null leaves the island's plain surface. The layered renderer draws its own.
    backdrop: ArtworkBlur? = ArtworkBlur(),
    renderer: MediaPlayerRenderer = DefaultMediaPlayerRenderer,
    previewIcon: Bitmap? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val image = remember(artwork) { artwork?.asImageBitmap() }
    val appIcon = remember(context, packageName, previewIcon) {
        previewIcon?.asImageBitmap() ?: packageName?.let { MediaSessionSource.appIcon(context, it)?.asImageBitmap() }
    }
    val colors = if (renderer.usesLayeredSurface) rememberMediaArtworkColors(artwork) else null
    val motion = rememberIslandSurfaceMotion(renderer.usesLayeredSurface)
    val pulse = LocalIslandPulse.current
    val state = MediaPlayerState(
        // Same art accent as the compact island and the lyrics box; the tonal scheme only styles the text
        title, artist, image, accent, playing, liked, appIcon, canSkipBack, canSkipForward, style,
        onAccent = if (colors != null && accent.luminance() < 0.4f) Color.White else Color.Black,
        surface = MediaPlayerSurface(scope.spec.expandedCorner, pulse, !DeviceUtils.isPowerSaveMode(context), scope.spec.surfaceTop,
            camera = { with(density) {
                val width = scope.spec.expandedWidth.toPx()
                val slot = scope.spec.cameraSlotWidth.toPx()
                MediaCameraGeometry(Offset(when {
                    scope.spec.growDirection > 0 -> slot / 2
                    scope.spec.growDirection < 0 -> width - slot / 2
                    else -> width / 2
                }, (scope.spec.compactHeight / 2 + scope.spec.expandedOutset).toPx()), scope.spec.cameraDiameter.toPx() / 2)
            } }),
    )
    val environment = MediaPlayerEnvironment(
        components = if (colors != null) remember(colors) { ArtworkMediaComponents(colors) } else IslandMediaComponents,
        host = MediaPlayerHost(scope::openApp, scope::keepAlive) { IslandHaptics.button(context) },
        motion = MediaPlayerMotion(motion.contentIn(), motion.contentOut(), motion.expressiveFastSpatial()),
    )
    val background: @Composable (Modifier) -> Unit = { modifier -> backdrop?.let { scope.ArtworkBackdrop(image, modifier, it) } }
    Box {
        val detached = drawBackground && renderer.usesLayeredSurface && DetachedSurfaceVisuals { frame ->
            val visualState = state.copy(surface = MediaPlayerSurface(
                state.surface.corner, { frame().pulse }, state.surface.blurEnabled, state.surface.topInset,
                geometry = { frame().let { MediaVisualGeometry(it.bounds, it.cornerRadius) } },
                camera = { frame().let { MediaCameraGeometry(it.cameraCenter, it.cameraRadius) } },
            ))
            MediaPlayerBackdrop(visualState, renderer, Modifier.fillMaxSize(), background)
        }
        // Ordinary items keep the original slot; standalone Studio uses the local fallback.
        if (drawBackground && !detached && !SurfaceBackdrop {
                MediaPlayerBackdrop(state, renderer, Modifier.fillMaxSize(), background)
            }) {
            MediaPlayerBackdrop(state, renderer, Modifier.matchParentSize(), background)
        }
        MediaPlayer(
            state = state,
            actions = actions,
            environment = environment,
            layout = MediaPlayerLayout(scope.spec.expandedCorner, scope.spec.expandedOutset, scope.spec.growDirection),
            renderer = renderer,
        )
    }
}

private val MediaArtistFont = FontFamily(Font(R.font.google_sans_flex,
    variationSettings = FontVariation.Settings(FontVariation.Setting("ROND", 0f))))

private class ArtworkMediaComponents(private val colors: MediaArtworkColors) : MediaPlayerComponents by IslandMediaComponents {
    @Composable
    override fun Text(text: String, title: Boolean, modifier: Modifier) {
        val style = if (title) IslandTextStyles.title.copy(fontSize = 18.sp, color = colors.onSurface)
            else IslandTextStyles.body.copy(fontSize = 12.sp, color = colors.onSurfaceVariant,
                fontFamily = MediaArtistFont, fontWeight = FontWeight.Normal)
        MarqueeText(text, style, modifier)
    }
}

private object IslandMediaComponents : MediaPlayerComponents {
    @Composable
    override fun Text(text: String, title: Boolean, modifier: Modifier) {
        MarqueeText(text, if (title) IslandTextStyles.title.copy(fontSize = 18.sp) else IslandTextStyles.body, modifier)
    }

    @Composable
    override fun Icon(icon: MediaPlayerIcon, tint: Color, size: Dp) {
        val resource = when (icon) {
            MediaPlayerIcon.Play -> R.drawable.rounded_play_arrow_24
            MediaPlayerIcon.Pause -> R.drawable.rounded_pause_24
            MediaPlayerIcon.Liked -> R.drawable.round_favorite_24
            MediaPlayerIcon.Like -> R.drawable.rounded_favorite_24
            MediaPlayerIcon.Previous -> R.drawable.rounded_skip_previous_24
            MediaPlayerIcon.Next -> R.drawable.rounded_skip_next_24
        }
        MaterialIcon(painterResource(resource), contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }

    @Composable
    override fun Equalizer(playing: Boolean, color: Color, size: Dp, outline: Color) {
        EqualizerBars(playing, color, size = size, outline = outline)
    }

    @Composable
    override fun SeekBar(
        value: Float,
        color: Color,
        enabled: Boolean,
        wavy: Boolean,
        modifier: Modifier,
        onInteraction: () -> Unit,
        onSeek: (Float) -> Unit,
    ) {
        IslandSeekBar(
            value = value, color = color, enabled = enabled, wavy = wavy, modifier = modifier,
            onInteraction = onInteraction, onValueChangeFinished = onSeek,
        )
    }
}

@Composable
fun mediaAccent(artAccent: Color?): Color = artAccent ?: LocalIslandIconColor.current ?: Color.White
