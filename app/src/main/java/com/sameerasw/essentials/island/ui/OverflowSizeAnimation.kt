package com.sameerasw.essentials.island.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/** animateContentSize clips internally. Reserve draw-only space without changing card geometry.
 * Constant insets remain present across stages so the existing size animation stays continuous.
 * The opaque host still clips ordinary items to their original card shape.
 */
internal fun Modifier.animateSurfaceContentSize(
    animationSpec: FiniteAnimationSpec<IntSize>,
    alignment: Alignment,
    finishedListener: (IntSize, IntSize) -> Unit,
): Modifier = this
    .layout { measurable, constraints ->
        val horizontal = 24.dp.roundToPx()
        val top = 24.dp.roundToPx()
        val bottom = 128.dp.roundToPx()
        val child = measurable.measure(constraints.offset(horizontal * 2, top + bottom))
        layout((child.width - horizontal * 2).coerceAtLeast(0), (child.height - top - bottom).coerceAtLeast(0)) {
            child.place(-horizontal, -top)
        }
    }
    .animateContentSize(animationSpec, alignment, finishedListener)
    .layout { measurable, constraints ->
        val horizontal = 24.dp.roundToPx()
        val top = 24.dp.roundToPx()
        val bottom = 128.dp.roundToPx()
        val child = measurable.measure(constraints.offset(-horizontal * 2, -top - bottom))
        layout(child.width + horizontal * 2, child.height + top + bottom) {
            child.place(horizontal, top)
        }
    }
