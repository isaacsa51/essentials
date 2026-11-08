package com.sameerasw.essentials.island.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.island.model.*
import com.sameerasw.essentials.island.state.CompactEntry
import com.sameerasw.essentials.island.state.CompactLayoutEngine
import com.sameerasw.essentials.island.state.IslandUiState
import com.sameerasw.essentials.island.ui.IslandActions
import com.sameerasw.essentials.island.ui.IslandLayoutSpec
import com.sameerasw.essentials.island.ui.IslandRoot

/** Runs the actual host and pulse producer with synthetic content, without service permissions. */
@Composable
internal fun MediaHostPreview(spec: IslandLayoutSpec, layered: Boolean, artwork: ImageBitmap,
    initialExpanded: Boolean, onBack: () -> Unit, content: @Composable (IslandExpandedScope) -> Unit) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    var revision by remember { mutableIntStateOf(0) }
    var pulseEnabled by remember { mutableStateOf(true) }
    var collapseAnimator by remember { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    val item = IslandItem(
        key = "preview-media", priority = IslandPriority.MEDIA, placement = CompactPlacement.Dynamic,
        compact = listOf(CompactCell("cover") {
            Image(artwork, null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        }),
        expanded = ExpandedContent(content), accent = Color(0xFFADC6FF), contentId = revision.toString(),
        expandedSurfaceStyle = if (layered) ExpandedSurfaceStyle.Layered else ExpandedSurfaceStyle.Default,
    )
    val state = IslandUiState(
        stage = if (expanded) IslandStage.Expanded else IslandStage.Compact,
        items = mapOf(item.key to item), focusedKey = if (expanded) item.key else null,
        arrangement = CompactLayoutEngine.arrange(
            listOf(CompactEntry(item.key, item.priority, false, item.compact.map { it.key })), spec.cameraAnchor),
    )
    val actions = object : IslandActions {
        override fun onTap(itemKey: String?): Boolean { expanded = true; return true }
        override fun onLongPress(itemKey: String?) { revision++ }
        override fun onCollapse() {
            val animate = collapseAnimator
            if (animate != null) animate { expanded = false } else expanded = false
        }
        override fun onDismiss(): Boolean = false
        override fun onOpenFocused() {}
        override fun onInteraction() {}
        override fun onTextInputChanged(active: Boolean) {}
        override fun onAdvance(): Boolean = false
    }
    Box(Modifier.fillMaxSize()) {
        IslandRoot(state, spec.copy(pulseShadow = pulseEnabled), actions,
            onTargetBoundsChanged = { android.util.Log.d("MediaHostPreview", "cardBounds=$it") },
            registerCollapseAnimator = { collapseAnimator = it })
        Surface(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 32.dp),
            shape = RoundedCornerShape(20.dp), color = Color(0xF21A1D26), contentColor = Color(0xFFE9EBF3)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewToggle(stringResource(R.string.media_preview_host_pulse), pulseEnabled) { pulseEnabled = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.media_preview_host_back)) }
                    Button(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.media_preview_host_collapse else R.string.media_preview_host_expand))
                    }
                    OutlinedButton(onClick = { revision++ }) { Text(stringResource(R.string.media_preview_host_update)) }
                }
            }
        }
    }
}
