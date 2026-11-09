package com.sameerasw.essentials.island.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.island.model.IslandAttachment
import com.sameerasw.island.media.ui.translucentMediaSurface
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Draws [attachment] centred below the expanded surface as a second droplet, bonded to the card through the
 * liquid field. Keeps the last attachment while it fades out so the shape can leave with its content.
 *
 * @param centerX Window x of the card's centre, read during layout so it follows the card live.
 * @param top Window y the attachment starts at (card bottom plus gap), read during layout.
 * @param collapse Collapse progress of the expanded stage; the attachment leaves first and the live surface pauses.
 */
@Composable
fun IslandAttachmentSlot(
    attachment: IslandAttachment?,
    shown: Boolean,
    widthPx: Int,
    centerX: () -> Float,
    top: () -> Float,
    collapse: () -> Float,
    outlineColor: Color?,
    outlineThickness: Dp,
    onSizeChanged: (IntSize) -> Unit,
    // Colour of the soft light falling from the top of the black body; null leaves it plain black
    glow: Color? = null,
) {
    // Plain holder: kept only so the shape can fade out with its last content, never read as state
    val last = remember { arrayOfNulls<IslandAttachment>(1) }
    if (attachment != null) last[0] = attachment
    val presence = animateFloatAsState(
        if (shown && attachment != null) 1f else 0f,
        IslandMotion.float(),
        label = "attachmentPresence",
    )
    val current = last[0] ?: return
    if (presence.value <= 0.001f && !shown) return
    val latestCollapse by rememberUpdatedState(collapse)
    // Leaves faster than the card collapses so it never trails a shrinking surface
    fun visible(): Float = presence.value * (1f - latestCollapse() * 2f).coerceIn(0f, 1f)
    val maxCornerPx = with(LocalDensity.current) { IslandAttachment.CORNER.toPx() }
    // Neck radius follows the clipped corner, not the default half-height pill
    val bond = remember(maxCornerPx) {
        LiquidBond("attachment", radius = { minOf(maxCornerPx, it.height / 2f) }, presence = { visible() })
    }
    val body = rememberLiquidBody(LiquidSpec.Droplet, bond)
    var heightPx by remember { mutableIntStateOf(0) }
    val corner = with(LocalDensity.current) {
        if (heightPx > 0) minOf(IslandAttachment.CORNER, (heightPx / 2f).toDp()) else IslandAttachment.CORNER
    }
    val shape = RoundedCornerShape(corner)
    val glowColor = glow?.let { animateColorAsState(it, label = "attachmentGlow").value }
    // Slow loop the light pools drift on; read only while drawing, so it redraws the body without recomposing
    val drift = rememberInfiniteTransition(label = "attachmentDrift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing)),
        label = "attachmentDriftPhase",
    )
    val settled by remember { derivedStateOf { presence.value > 0.99f && latestCollapse() <= 0.02f } }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints(minWidth = widthPx, maxWidth = widthPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        p.placeWithLayer((centerX() - p.width / 2f).roundToInt(), top().roundToInt()) {
                            val v = visible()
                            alpha = v
                            val s = 0.85f + 0.15f * v
                            scaleX = s
                            scaleY = s
                            // Grows out of the card's bottom edge
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                    }
                }
                .onSizeChanged {
                    heightPx = it.height
                    onSizeChanged(it)
                }
                .liquid(body, TransformOrigin(0.5f, 0f)),
        ) {
            // Body: the media player's translucent container lit from the top (the edge glow, mirrored to the top),
            // blurred on its own layer so its edges feather out instead of ending in a hard rim (Android 12+)
            Box(
                Modifier
                    .matchParentSize()
                    .blur(EDGE_BLUR, BlurredEdgeTreatment.Unbounded)
                    .clip(shape)
                    .translucentMediaSurface()
                    .drawBehind { glowColor?.let { scale(scaleX = 1f, scaleY = -1f) { drawEdgeGlow(it, drift.value) } } }
                    .islandOutline(outlineColor, outlineThickness, shape),
            )
            // Content stays sharp, clipped to the body's shape
            Box(Modifier.clip(shape)) {
                current.content(widthPx, settled)
            }
        }
    }
}

// How far the body's edges feather out, the strength of the light wash along the edge and of each light pool
private val EDGE_BLUR = 10.dp
private const val GLOW_ALPHA = 0.5f
private const val POOL_ALPHA = 0.32f

// One full loop of the drift; every pool's speed is a whole number of turns per loop, so the loop has no seam
private const val DRIFT_MS = 20_000

/**
 * A pool of light drifting along the edge.
 *
 * @param tint How far the accent is pulled towards white (positive) or black (negative).
 * @param turns Whole turns per loop, negative to drift the other way.
 * @param phase Where in its loop the pool starts, 0..1.
 * @param width Radius as a fraction of the body width.
 */
private class LightPool(val tint: Float, val turns: Int, val phase: Float, val width: Float)

private val LightPools = listOf(
    LightPool(tint = 0f, turns = 1, phase = 0f, width = 0.42f),
    LightPool(tint = 0.35f, turns = -2, phase = 0.33f, width = 0.32f),
    LightPool(tint = -0.3f, turns = 1, phase = 0.66f, width = 0.38f),
    LightPool(tint = 0.15f, turns = -1, phase = 0.5f, width = 0.36f),
)

/**
 * Light along one edge across the full width, drawn for the bottom edge: a faint wash of [color] fading away from the
 * edge, under pools of the accent in a few tones that drift slowly along it as [drift] runs from 0 to 1, so the
 * colour keeps shifting where they overlap. Mirror the scope for the top.
 */
private fun DrawScope.drawEdgeGlow(color: Color, drift: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.35f to color.copy(alpha = GLOW_ALPHA * 0.1f),
            0.75f to color.copy(alpha = GLOW_ALPHA * 0.35f),
            1f to color.copy(alpha = GLOW_ALPHA * 0.6f),
        ),
    )
    for (pool in LightPools) {
        val tone = if (pool.tint >= 0f) lerp(color, Color.White, pool.tint) else lerp(color, Color.Black, -pool.tint)
        val x = w * (0.5f + 0.36f * sin(2f * PI.toFloat() * (drift * pool.turns + pool.phase)))
        val radius = w * pool.width
        val center = Offset(x, h)
        // Wide and flat: each pool spreads along the edge more than it climbs
        scale(scaleX = 1f, scaleY = (h * 0.9f) / radius, pivot = center) {
            drawCircle(
                Brush.radialGradient(
                    0f to tone.copy(alpha = POOL_ALPHA),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
