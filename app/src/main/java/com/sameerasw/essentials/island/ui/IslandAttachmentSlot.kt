package com.sameerasw.essentials.island.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.sameerasw.essentials.island.model.IslandAttachment
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
                .liquid(body, TransformOrigin(0.5f, 0f))
                .clip(shape)
                .background(Color.Black)
                .drawBehind { glowColor?.let { drawTopGlow(it) } }
                .islandOutline(outlineColor, outlineThickness, shape),
        ) {
            current.content(widthPx, settled)
        }
    }
}

// Peak strength of the glow at the top centre, and how far it reaches: half the body width across, past the bottom down
private const val GLOW_ALPHA = 0.38f
private const val GLOW_WIDTH = 0.42f
private const val GLOW_DEPTH = 1.15f

/** A soft light falling from the top centre: an elliptical radial fade of [color], strongest at the top edge. */
private fun DrawScope.drawTopGlow(color: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val radius = h * GLOW_DEPTH
    val center = Offset(w / 2f, 0f)
    // Draw a circle-based fade, then stretch it sideways into the ellipse
    scale(scaleX = (w * GLOW_WIDTH) / radius, scaleY = 1f, pivot = center) {
        drawRect(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = GLOW_ALPHA),
                0.45f to color.copy(alpha = GLOW_ALPHA * 0.5f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            topLeft = Offset(center.x - radius, 0f),
            size = Size(radius * 2f, h),
        )
    }
}
