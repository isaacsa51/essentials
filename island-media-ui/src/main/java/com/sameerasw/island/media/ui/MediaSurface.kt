package com.sameerasw.island.media.ui

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Figma's enabled background-surface effects only; no outer drop shadow. */
@Composable
internal fun MediaSurface(state: MediaPlayerState, modifier: Modifier) {
    var viewportHeight by remember { mutableIntStateOf(1) }
    val shape = remember(state.surface) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val geometry = state.surface.geometry?.invoke()
                val radius = (geometry?.cornerRadius ?: with(density) { state.surface.corner.toPx() })
                    .coerceIn(0f, minOf(size.width, size.height) / 2f)
                return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
            }
        }
    }
    val progressiveBlur = Modifier.blur {
        edgeTreatment = BlurredEdgeTreatment.Unbounded
        val pulse = if (state.surface.blurEnabled) state.surface.pulse().coerceIn(0f, 1f) else 0f
        // Progressive *spatial* Layer Blur: top 2, bottom 16 at the pulse peak.
        val rect = state.surface.geometry?.invoke()?.bounds
        val top = ((rect?.top ?: 0f) / viewportHeight).coerceIn(0f, 1f)
        val bottom = ((rect?.bottom ?: viewportHeight.toFloat()) / viewportHeight).coerceIn(top, 1f)
        radius = BlurRadiusSpec.verticalGradient(listOf(0f to 2f, top to 2f, bottom to 16f, 1f to 16f)
            .distinctBy { it.first }.map { (position, value) -> BlurStop(position, value.dp * pulse) })
    }
    Box(modifier.onSizeChanged { viewportHeight = it.height.coerceAtLeast(1) }) {
        Box(Modifier.matchParentSize().then(progressiveBlur).drawWithCache {
            val geometry = state.surface.geometry?.invoke()
            val rect = geometry?.bounds ?: Rect(0f, 0f, size.width, size.height)
            val paint = mediaSurfacePaint(mediaSurfaceGradient(rect, state.surface.camera?.invoke(), MediaCameraClearance.toPx()))
            val corner = (geometry?.cornerRadius ?: state.surface.corner.toPx())
                .coerceIn(0f, minOf(rect.width, rect.height).coerceAtLeast(0f) / 2f)
            onDrawBehind {
                if (rect.width > 0f && rect.height > 0f)
                    drawContext.canvas.nativeCanvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, corner, corner, paint)
            }
        })
        // Apply this mask AFTER blur, so colored pixels cannot bleed back into the top band.
        // Its offscreen bounds are the complete visual canvas, not the card outline.
        Box(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val geometry = state.surface.geometry?.invoke()
                val rect = geometry?.bounds ?: Rect(0f, 0f, size.width, size.height)
                val corner = geometry?.cornerRadius ?: state.surface.corner.toPx()
                val clearUntil = mediaShadowClearUntil(rect, corner, state.surface.camera?.invoke(), MediaCameraClearance.toPx())
                val mask = Brush.verticalGradient(listOf(Color.Transparent, Color.White),
                    startY = clearUntil, endY = clearUntil + 24.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }.then(progressiveBlur)) {
            // Figma 199:867/199:838: two inner shadows form the blue rim. Keep the blur
            // on the full visual canvas, but rasterize inner-shadow masks at card-local size.
            // A full-window mask with an offset outline does not share the card's mask origin.
            Box(Modifier.layout { measurable, constraints ->
                val rect = state.surface.geometry?.invoke()?.bounds
                    ?: Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val child = measurable.measure(Constraints.fixed(
                    rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)))
                val left = rect.left.roundToInt()
                val top = rect.top.roundToInt()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    child.placeWithLayer(left, top) {
                        translationX = rect.left - left
                        translationY = rect.top - top
                    }
                }
            }.innerShadow(shape) {
                    radius = 24.dp.toPx()
                    color = state.accent.copy(alpha = 0.35f)
                    offset = Offset(0f, (-8).dp.toPx())
                }
                .innerShadow(shape) {
                    radius = 2.dp.toPx()
                    color = state.accent.copy(alpha = 0.35f)
                    offset = Offset.Zero
                })
        }
        MediaCameraCore(state, Modifier.matchParentSize())
    }
}

/** Keep the protected lens area opaque even while the underlying surface is progressively blurred. */
@Composable
private fun MediaCameraCore(state: MediaPlayerState, modifier: Modifier) {
    Box(modifier.drawWithCache {
        val camera = state.surface.camera?.invoke()
        val geometry = state.surface.geometry?.invoke()
        val rect = geometry?.bounds ?: Rect(0f, 0f, size.width, size.height)
        val corner = (geometry?.cornerRadius ?: state.surface.corner.toPx())
            .coerceIn(0f, minOf(rect.width, rect.height).coerceAtLeast(0f) / 2)
        val clip = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }
        val clearance = MediaCameraClearance.toPx()
        onDrawBehind {
            if (camera != null && camera.radius > 0f && rect.width > 0f && rect.height > 0f) {
                clipPath(clip) { drawCircle(Color.Black, radius = camera.radius + clearance, center = camera.center) }
            }
        }
    })
}

/** A non-interactive gradient underlay; drawing overflow never enlarges the hit target. */
@Composable
internal fun MediaTopShade(state: MediaPlayerState, modifier: Modifier) {
    Box(modifier.drawBehind {
        val geometry = state.surface.geometry?.invoke()
        val rect = geometry?.bounds ?: Rect(0f, 0f, size.width, size.height)
        val top = if (geometry != null) 0f else -state.surface.topInset.toPx()
        val height = 256.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 2f / 3f),
                0.2f to Color.Black.copy(alpha = 0.6333333f),
                0.4f to Color.Black.copy(alpha = 0.4666667f),
                0.6f to Color.Black.copy(alpha = 0.2f),
                0.8f to Color.Black.copy(alpha = 0.0333333f),
                1f to Color.Transparent,
                startY = top, endY = top + height,
            ),
            topLeft = Offset(if (geometry != null) 0f else rect.left - 8.dp.toPx(), top),
            size = Size(if (geometry != null) size.width else rect.width + 16.dp.toPx(), height),
        )
    })
}

/** The surface's black radial transparency gradient: opaque at [gradient]'s centre, half transparent at its edge. */
private fun mediaSurfacePaint(gradient: MediaSurfaceGradient) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFF000000.toInt(), 0xF2000000.toInt(), 0xE6000000.toInt(), 0xCC000000.toInt(), 0x8C000000.toInt(), 0x80000000.toInt()),
        gradient.stops,
        Shader.TileMode.CLAMP,
    ).apply {
        setLocalMatrix(Matrix().apply {
            setScale(gradient.radiusX, gradient.radiusY)
            postTranslate(gradient.center.x, gradient.center.y)
        })
    }
}

/**
 * The translucent media surface (the layered player's Translucent container) for shapes beside the card, such as the
 * lyrics attachment: the same black radial transparency gradient, opaque at the top centre and reaching its
 * half-transparent edge stop at this element's sides and bottom.
 */
fun Modifier.translucentMediaSurface(): Modifier = drawBehind {
    if (size.width <= 0f || size.height <= 0f) return@drawBehind
    val stops = floatArrayOf(0.05f, 0.25f, 0.45f, 0.65f, 0.85f, 1f)
    val paint = mediaSurfacePaint(MediaSurfaceGradient(Offset(size.width / 2f, 0f), size.width / 2f, size.height, stops))
    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
}
