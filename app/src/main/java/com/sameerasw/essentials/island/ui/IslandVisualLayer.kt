package com.sameerasw.essentials.island.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/** Window-local geometry, independent of the content layer's entrance/exit transform. */
internal data class IslandVisualFrame(val bounds: Rect, val cornerRadius: Float, val alpha: Float, val pulse: Float,
    val cameraCenter: Offset, val cameraRadius: Float)

internal class IslandVisualEntry(val owner: String, val content: @Composable (() -> IslandVisualFrame) -> Unit) {
    var registered by mutableStateOf(false)
}

/** Retain one entry across compact/expanded handoff; the host owns its visibility and lifetime. */
internal class IslandVisualSlot {
    var entry by mutableStateOf<IslandVisualEntry?>(null)
}

internal class IslandVisualRegistration(val slot: IslandVisualSlot, val owner: String, val publish: Boolean)

internal val LocalIslandVisualRegistration = staticCompositionLocalOf<IslandVisualRegistration?> { null }

/** Outgoing content is hosted too, but cannot overwrite the current item's visual entry. */
@Composable
internal fun DetachedSurfaceVisuals(content: @Composable (() -> IslandVisualFrame) -> Unit): Boolean {
    val registration = LocalIslandVisualRegistration.current ?: return false
    val latest by rememberUpdatedState(content)
    val entry = remember(registration.owner) { IslandVisualEntry(registration.owner) { frame -> latest(frame) } }
    DisposableEffect(registration.slot, registration.publish, entry) {
        if (registration.publish) {
            entry.registered = true
            registration.slot.entry = entry
        }
        // Intentionally retained when the expanded content leaves: no fresh shader/seed on collapse.
        onDispose { if (registration.publish) entry.registered = false }
    }
    return true
}

/** Use animated geometry, not content fade timing, to blend the expanded surface into the pill. */
internal fun visualExpansionAmount(live: IntSize, compact: IntSize, expanded: IntSize): Float {
    val heightRange = expanded.height - compact.height
    val widthRange = expanded.width - compact.width
    return when {
        heightRange > 0 -> (live.height - compact.height).toFloat() / heightRange
        widthRange > 0 -> (live.width - compact.width).toFloat() / widthRange
        else -> 0f
    }.coerceIn(0f, 1f)
}

val LocalIslandSurfaceAccent = staticCompositionLocalOf<(Color) -> Unit> { {} }

/** Lets an expanded card report its accent; a no-op unless a host provides a sink. */
@Composable
fun IslandSurfaceAccent(color: Color) {
    val report = LocalIslandSurfaceAccent.current
    SideEffect { report(color) }
}
