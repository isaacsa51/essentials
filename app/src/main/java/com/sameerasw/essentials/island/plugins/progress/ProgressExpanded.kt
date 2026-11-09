package com.sameerasw.essentials.island.plugins.progress

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sameerasw.essentials.island.ui.IslandMotion
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.domain.model.NotificationActionItem
import com.sameerasw.essentials.island.model.IslandExpandedScope
import com.sameerasw.essentials.island.ui.IslandTextStyles
import com.sameerasw.essentials.island.ui.islandTextColor
import com.sameerasw.essentials.island.ui.components.CameraRow
import com.sameerasw.essentials.island.ui.components.ConnectedButtonRow
import com.sameerasw.essentials.island.ui.components.ConnectedItem
import com.sameerasw.essentials.island.ui.components.ConnectedTextLabel
import com.sameerasw.essentials.island.ui.components.IslandBitmap
import com.sameerasw.essentials.island.ui.components.MarqueeText

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressExpanded(
    icon: Bitmap?,
    title: String,
    text: String?,
    progress: Float,
    indeterminate: Boolean,
    segments: List<Float>,
    accent: Color,
    actions: List<NotificationActionItem>,
    onAction: (NotificationActionItem) -> Unit,
    onOpen: () -> Unit,
    scope: IslandExpandedScope,
) {
    val spec = scope.spec
    val sidePadding = spec.expandedPadding + spec.expandedCorner * 0.35f
    Box(propagateMinConstraints = true) {
        Column(Modifier.fillMaxWidth().padding(spec.expandedOutset)) {
            Spacer(Modifier.height(spec.expandedTopPadding))
            scope.CameraRow(
                horizontalPadding = spec.cameraGap + spec.expandedCorner * 0.35f,
                title = title,
                start = {
                    IslandBitmap(icon, spec.cellSize, fallbackRes = R.drawable.rounded_downloading_24)
                },
                end = {
                    if (!indeterminate) Text("${(progress * 100).toInt()}%", style = IslandTextStyles.body.copy(color = islandTextColor()))
                },
            )
            if (!text.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = IslandTextStyles.body.copy(color = islandTextColor(), fontSize = 15.sp, lineHeight = 20.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding),
                )
            }
            Spacer(Modifier.height(16.dp))
            val barModifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding)
            if (segments.isNotEmpty() && !indeterminate) {
                SegmentedProgressBar(progress, segments, accent, barModifier)
            } else if (indeterminate) {
                LinearWavyProgressIndicator(modifier = barModifier, color = accent, trackColor = Color.White.copy(alpha = 0.2f))
            } else {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = barModifier,
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
            Spacer(Modifier.height(16.dp))
            ConnectedButtonRow(
                height = 44.dp,
                modifier = Modifier.padding(horizontal = sidePadding).padding(bottom = spec.expandedBottomPadding),
                container = Color.White.copy(alpha = 0.2f),
                items = listOf(
                    ConnectedItem({
                        onOpen()
                        scope.collapse()
                    }) { ConnectedTextLabel(stringResource(R.string.island_progress_open)) },
                ) + actions.take(3).map { action ->
                    ConnectedItem({ onAction(action) }) { ConnectedTextLabel(action.title) }
                },
            )
        }
    }
}

/** Mirrors Android 16 ProgressStyle: equal-height track split into gapped segments, filled up to [progress]. */
@Composable
private fun SegmentedProgressBar(progress: Float, segments: List<Float>, accent: Color, modifier: Modifier) {
    val fraction by animateFloatAsState(progress.coerceIn(0f, 1f), IslandMotion.float(), label = "segmentProgress")
    Canvas(modifier.height(6.dp)) {
        val gap = 4.dp.toPx()
        val h = size.height
        val usable = size.width - gap * (segments.size - 1)
        val track = Color.White.copy(alpha = 0.2f)
        val radius = CornerRadius(h / 2f)
        var x = 0f
        var start = 0f
        segments.forEach { share ->
            val w = usable * share
            drawRoundRect(track, Offset(x, 0f), Size(w, h), radius)
            val filled = ((fraction - start) / share).coerceIn(0f, 1f)
            if (filled > 0f) drawRoundRect(accent, Offset(x, 0f), Size(w * filled, h), radius)
            x += w + gap
            start += share
        }
    }
}
