package com.sameerasw.essentials.island.plugins.media

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

typealias MediaActions = com.sameerasw.island.media.ui.MediaActions
typealias MediaPlayerStyle = com.sameerasw.island.media.ui.MediaPlayerStyle

class MediaSnapshot(
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val accent: Color?,
    val playing: Boolean,
    val liked: Boolean,
    val actions: MediaActions,
    val open: () -> Unit,
    val canSkipBack: Boolean = true,
    val canSkipForward: Boolean = true,
    val packageName: String? = null,
    val playerAccent: Color? = null,
    val style: MediaPlayerStyle = MediaPlayerStyle.Default,
)

object IslandMediaState {
    val current = kotlinx.coroutines.flow.MutableStateFlow<MediaSnapshot?>(null)
    val live = kotlinx.coroutines.flow.MutableStateFlow(false)
}
