package com.sameerasw.essentials.island.preview

import android.graphics.BitmapFactory
import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.island.model.IslandExpandedScope
import com.sameerasw.essentials.island.plugins.media.MediaActions
import com.sameerasw.essentials.island.plugins.media.MediaExpanded
import com.sameerasw.essentials.island.plugins.media.MediaPlayerStyle
import com.sameerasw.essentials.island.state.CameraAnchor
import com.sameerasw.essentials.island.ui.IslandLayoutSpec
import com.sameerasw.essentials.island.ui.LocalIslandPulse
import com.sameerasw.island.media.ui.DefaultMediaPlayerRenderer
import com.sameerasw.island.media.ui.GeminiParticlePill
import com.sameerasw.island.media.ui.IslandMediaPlayerRenderer
import com.sameerasw.island.media.ui.MediaPlayerRenderer
import com.sameerasw.island.media.ui.MediaPlayerState
import com.sameerasw.island.media.ui.ParticleFieldStyle
import com.sameerasw.island.media.ui.ParticleShape
import com.sameerasw.island.media.ui.MediaParticleOutset
import kotlinx.coroutines.delay

/** Debug only: capture extras provide deterministic Figma comparison frames. */
class MediaPlayerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val capture = intent.getBooleanExtra("capture", false)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (capture) {
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFADC6FF),onPrimary = Color(0xFF082F62),
                primaryContainer = Color(0xFF263C5C),onPrimaryContainer = Color(0xFFD7E4FF),
                secondaryContainer = Color(0xFF263C5C),onSecondaryContainer = Color(0xFFD7E4FF),
                background = Color(0xFF0F1117),onBackground = Color(0xFFE9EBF3),surface = Color(0xFF1A1D26),
                onSurface = Color(0xFFE9EBF3),onSurfaceVariant = Color(0xFFBAC0D0))) {
                Surface(Modifier.fillMaxSize(),color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    val shaderTime = if (intent.hasExtra("shader_time")) intent.getFloatExtra("shader_time", 0f) else null
                    val diamond = intent.getIntExtra("shader_shape", 0) == 1
                    val disabled = intent.getBooleanExtra("shader_disabled", false)
                    if (intent.getBooleanExtra("capsule", false)) {
                        HalftoneCapsulePreview(shaderTime, diamond, disabled)
                    } else MediaPlayerStudio(capture, intent.getFloatExtra("pulse", if (capture) 0f else 1f),
                        intent.getBooleanExtra("legacy", false), intent.getIntExtra("width_dp", 432), intent.getBooleanExtra("host", false),
                        shaderTime, intent.getBooleanExtra("shader_fallback", false), intent.getBooleanExtra("paused", false), diamond, disabled,
                        intent.getFloatExtra("top_dp", 8f).coerceAtLeast(0f))
                }
            }
        }
    }
}

@Composable
private fun MediaPlayerStudio(capture: Boolean, initialPulse: Float, legacy: Boolean, initialWidth: Int, host: Boolean,
                              shaderTime: Float?, shaderFallback: Boolean, paused: Boolean,
                              initialDiamond: Boolean, shaderDisabled: Boolean, initialTop: Float) {
    val defaults = remember { ParticleFieldStyle() }
    var custom by remember { mutableStateOf(!legacy) }
    var background by remember { mutableStateOf(host) }
    var showHost by remember { mutableStateOf(host) }
    var studioBackground by remember { mutableStateOf(false) }
    var effects by remember { mutableStateOf(!shaderDisabled) }
    var diamond by remember { mutableStateOf(initialDiamond) }
    var pointMinRadius by remember { mutableFloatStateOf(defaults.minRadius.value) }
    var pointRadius by remember { mutableFloatStateOf(defaults.maxRadius.value) }
    var pointSpacing by remember { mutableFloatStateOf(defaults.minDistance.value) }
    var pointDisplacement by remember { mutableFloatStateOf(defaults.displacement.value) }
    var flowSpeed by remember { mutableFloatStateOf(defaults.speed) }
    var waveFrequency by remember { mutableFloatStateOf(ParticleFieldStyle.DEFAULT_NOISE_FREQUENCY) }
    var innerIngress by remember { mutableFloatStateOf(defaults.innerBand.value) }
    var outerSpread by remember { mutableFloatStateOf(defaults.outerBand.value) }
    var opacityFalloff by remember { mutableFloatStateOf(defaults.falloffExponent) }
    var overallOpacity by remember { mutableFloatStateOf(defaults.opacity) }
    var artControls by remember { mutableStateOf(false) }
    var mirror by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var fitPreview by remember { mutableStateOf(true) }
    var cardHeight by remember { mutableStateOf(160.dp) }
    val density = LocalDensity.current
    var playing by remember { mutableStateOf(!paused) }
    var liked by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.5f) }
    var pulseValue by remember { mutableFloatStateOf(initialPulse.coerceIn(0f, 1f)) }
    var width by remember { mutableFloatStateOf(initialWidth.toFloat()) }
    val context = LocalContext.current
    val artwork = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.media_preview_cover) }
    val appIcon = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.media_preview_service) }
    val wallpaper = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.media_preview_wallpaper).asImageBitmap() }
    val title = stringResource(R.string.media_preview_song)
    val artist = stringResource(R.string.media_preview_artist)
    LaunchedEffect(playing, capture) {
        while (playing && !capture) {
            delay(200)
            progress = (progress + 0.001f) % 1f
        }
    }
    val base = if (custom) IslandMediaPlayerRenderer else DefaultMediaPlayerRenderer
    val fieldStyle = ParticleFieldStyle(minDistance = pointSpacing.dp,minRadius = pointMinRadius.dp,maxRadius = pointRadius.dp,
        displacement = pointDisplacement.dp,noiseScale = (1f/waveFrequency).dp,speed = flowSpeed,
        innerBand = innerIngress.dp,outerBand = outerSpread.dp,shape = if (diamond) ParticleShape.Diamond else ParticleShape.Circle,
        falloffExponent = opacityFalloff,opacity = overallOpacity)
    val previewOutset = maxOf(MediaParticleOutset,outerSpread.dp+8.dp)
    val renderer = remember(base, effects, background, shaderTime, shaderFallback, fieldStyle, previewOutset) {
        object : MediaPlayerRenderer by base {
            override val effectOutset = previewOutset
            @Composable
            override fun Underlay(state: MediaPlayerState, modifier: Modifier) {
                if (background) base.Underlay(state, modifier)
            }
            @Composable
            override fun Background(state: MediaPlayerState, modifier: Modifier, fallback: @Composable (Modifier) -> Unit) {
                if (background) base.Background(state, modifier, fallback)
            }
            @Composable
            override fun Effects(state: MediaPlayerState, modifier: Modifier) {
                if (effects && base.usesLayeredSurface) {
                    GeminiParticlePill(modifier, state.accent, state.surface.corner, state.surface.pulse,
                        enabled = state.surface.blurEnabled, timeSeconds = shaderTime, forceFallback = shaderFallback,
                        capsuleInset = effectOutset,style = fieldStyle,geometry = state.surface.geometry,
                        protectedCamera = state.surface.camera)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (capture || showHost) Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val available = (maxWidth - 16.dp).coerceAtLeast(200.dp)
        val cardWidth = minOf(width.dp, available)
        val spec = IslandLayoutSpec(expandedWidth = cardWidth, expandedCorner = 32.dp, cameraPresence = 0f, surfaceTop = initialTop.dp,
            cameraAnchor = if (mirror) CameraAnchor.Start else CameraAnchor.Center)
        val scope = remember(spec) {
            object : IslandExpandedScope {
                override val spec: IslandLayoutSpec = spec
                override val accent = Color(0xFFADC6FF)
                override fun collapse() {}
                override fun dismiss() {}
                override fun openApp() {}
                override fun setTextInput(active: Boolean) {}
                override fun keepAlive() {}
            }
        }
        val mediaContent: @Composable (IslandExpandedScope) -> Unit = { mediaScope ->
                    MediaExpanded(
                        title = title, artist = artist, artwork = artwork, accent = scope.accent,
                        playing = playing, liked = liked,
                        actions = MediaActions(
                            playPause = { playing = !playing }, next = { progress = 0f },
                            previous = { progress = 0f }, like = { liked = !liked },
                            progress = { progress }, canSeek = { true }, seekTo = { progress = it },
                        ),
                        scope = mediaScope, canSkipBack = true, canSkipForward = true,
                        style = if (artControls) MediaPlayerStyle.ArtControls else MediaPlayerStyle.Default,
                        renderer = renderer, previewIcon = appIcon,
                    )
        }
        val card: @Composable (Modifier) -> Unit = { cardModifier ->
            val shell = if (custom) Modifier else Modifier.clip(RoundedCornerShape(spec.expandedCorner)).background(Color.Black)
            Box(Modifier.requiredWidth(cardWidth).onSizeChanged { cardHeight = with(density) { it.height.toDp() } }
                .then(shell).then(cardModifier)) {
                CompositionLocalProvider(LocalIslandPulse provides { pulseValue }) { mediaContent(scope) }
            }
        }
        if (showHost) {
            MediaHostPreview(spec, custom, artwork.asImageBitmap(), capture, {
                if (host) (context as? Activity)?.finish()
                else { showHost = false; background = studioBackground }
            }, mediaContent)
        } else if (capture) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { card(Modifier) }
            Box(Modifier.align(Alignment.TopCenter).offset(y = 16.dp).size(32.dp).clip(CircleShape).background(Color.Black))
        } else {
            StudioScreen(onReset = {
                custom = true; background = false; effects = true; diamond = defaults.shape == ParticleShape.Diamond
                pointMinRadius = defaults.minRadius.value; pointRadius = defaults.maxRadius.value
                pointSpacing = defaults.minDistance.value; pointDisplacement = defaults.displacement.value
                flowSpeed = defaults.speed; waveFrequency = ParticleFieldStyle.DEFAULT_NOISE_FREQUENCY
                innerIngress = defaults.innerBand.value; outerSpread = defaults.outerBand.value
                opacityFalloff = defaults.falloffExponent; overallOpacity = defaults.opacity
                artControls = false; mirror = false; showWallpaper = false; fitPreview = true
                pulseValue = 1f; width = initialWidth.toFloat()
            },preview = { previewModifier ->
                StudioPreview(previewModifier,wallpaper,showWallpaper,{ showWallpaper = it },fitPreview,{ fitPreview = it },cardWidth,cardHeight,previewOutset,card)
            }) {
                StudioGroup(stringResource(R.string.media_preview_group_particles)) {
                    PreviewToggle(stringResource(R.string.media_preview_effects), effects) { effects = it }
                    PreviewToggle(stringResource(R.string.media_preview_diamond), diamond) { diamond = it }
                    StudioNumberInput(stringResource(R.string.media_preview_falloff),opacityFalloff,"×") { opacityFalloff = it }
                    Text(stringResource(R.string.media_preview_falloff_help),style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StudioNumberInput(stringResource(R.string.media_preview_overall_opacity),overallOpacity*100,"%",maximum = 100f) {
                        overallOpacity = it/100f
                    }
                    StudioNumberInput(stringResource(R.string.media_preview_min_radius),pointMinRadius,"dp") {
                        pointMinRadius = it
                        pointRadius = maxOf(pointRadius,it)
                    }
                    StudioNumberInput(stringResource(R.string.media_preview_point_radius),pointRadius,"dp") {
                        pointRadius = it
                        pointMinRadius = minOf(pointMinRadius,it)
                    }
                    StudioNumberInput(stringResource(R.string.media_preview_point_spacing),pointSpacing,"dp",minimum = 1f) { pointSpacing = it }
                    StudioNumberInput(stringResource(R.string.media_preview_point_displacement),pointDisplacement,"dp") { pointDisplacement = it }
                    StudioNumberInput(stringResource(R.string.media_preview_flow_speed),flowSpeed,"×") { flowSpeed = it }
                    StudioNumberInput(stringResource(R.string.media_preview_frequency),waveFrequency,"/dp",minimum = 0.000001f) { waveFrequency = it }
                    StudioNumberInput(stringResource(R.string.media_preview_ingress),innerIngress,"dp") { innerIngress = it }
                    StudioNumberInput(stringResource(R.string.media_preview_spread),outerSpread,"dp",exclusiveMinimum = true) { outerSpread = it }
                    StudioNumberInput(stringResource(R.string.media_preview_pulse),pulseValue*100,"%",maximum = 100f) { pulseValue = it/100f }
                }
                StudioGroup(stringResource(R.string.media_preview_group_player)) {
                    PreviewToggle(stringResource(R.string.media_preview_custom), custom) { custom = it }
                    PreviewToggle(stringResource(R.string.media_preview_background), background) { background = it }
                    PreviewToggle(stringResource(R.string.media_preview_art_controls), artControls) { artControls = it }
                    PreviewToggle(stringResource(R.string.media_preview_mirror), mirror) { mirror = it }
                    StudioNumberInput(stringResource(R.string.media_preview_width),width,"dp",minimum = 200f,
                        maximum = maxOf(200f,available.value)) { width = it }
                    Button(onClick = {
                        studioBackground = background
                        background = true
                        showHost = true
                    }) { Text(stringResource(R.string.media_preview_host_test)) }
                }
                Text(stringResource(R.string.media_preview_help),style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
