package com.sameerasw.essentials.island.plugins.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sameerasw.essentials.island.model.IslandAttachment
import com.sameerasw.essentials.island.model.IslandAttachments
import com.sameerasw.island.sdk.IslandAnchor
import kotlinx.coroutines.delay

/**
 * TEMPORARY test for island attachments: fake synced lyrics under the expanded media card on debug builds,
 * so the slot, motion and liquid neck can be checked without a provider. Remove before merging.
 * A real provider (Rush) wins over this one because owners resolve in name order.
 */
object DebugLyricsAttachment {
    private const val OWNER = "zz.debug"
    // Off now that Rush provides real lyrics; set to BuildConfig.DEBUG to test the slot without a provider
    private const val ENABLED = false

    private val lines = listOf(
        "Streetlights hum a borrowed tune",
        "I count the seconds back to you",
        "The radio keeps the night awake",
        "Every chorus feels like a mistake",
        "But I sing it anyway",
    )

    fun install() {
        if (!ENABLED) return
        IslandAttachments.set(
            OWNER,
            listOf(IslandAttachment(key = "$OWNER:lyrics", anchor = IslandAnchor.MEDIA) { _, settled -> Content(settled) }),
        )
    }

    fun uninstall() {
        if (!ENABLED) return
        IslandAttachments.set(OWNER, emptyList())
    }

    @Composable
    private fun Content(settled: Boolean) {
        var index by remember { mutableIntStateOf(0) }
        LaunchedEffect(settled) {
            while (settled) {
                delay(2_400)
                index = (index + 1) % lines.size
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "debugLyric",
            ) { i ->
                Column {
                    Text(
                        lines[i],
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        lines[(i + 1) % lines.size],
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
