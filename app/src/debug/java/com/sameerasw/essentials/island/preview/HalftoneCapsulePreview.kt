package com.sameerasw.essentials.island.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sameerasw.island.media.ui.GeminiParticlePill
import com.sameerasw.island.media.ui.ParticleFieldStyle
import com.sameerasw.island.media.ui.ParticleShape

/** Flat test scene: no wallpaper, background glow or blur to disguise the point geometry. */
@Composable
internal fun HalftoneCapsulePreview(time: Float?, diamond: Boolean, disabled: Boolean) {
    Box(Modifier.fillMaxSize().background(Color(0xFF202020))) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(240.dp).align(Alignment.BottomCenter)) {
            val width = minOf(360.dp, maxWidth - 48.dp)
            val left = (maxWidth-width)/2
            val top = 100.dp
            val rect = with(LocalDensity.current) {
                Rect(left.toPx(), top.toPx(), (left+width).toPx(), (top+56.dp).toPx())
            }
            Box(Modifier.offset(left, top).size(width, 56.dp).background(Color.Black, RoundedCornerShape(28.dp)))
            GeminiParticlePill(Modifier.fillMaxSize(), Color(0xFFADC6FF), cornerRadius = 28.dp,
                capsuleRect = rect, capsuleInset = 0.dp, enabled = !disabled, timeSeconds = time,
                style = ParticleFieldStyle(shape = if (diamond) ParticleShape.Diamond else ParticleShape.Circle))
            Box(Modifier.offset(left, top).size(width, 56.dp), contentAlignment = Alignment.Center) {
                Text("Capsule / halftone", color = Color.White)
            }
        }
    }
}
