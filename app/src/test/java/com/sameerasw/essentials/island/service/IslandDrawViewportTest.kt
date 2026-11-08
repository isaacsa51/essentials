package com.sameerasw.essentials.island.service

import com.sameerasw.essentials.island.state.CameraGeometry
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class IslandDrawViewportTest {
    @Test fun displayIsCoveredForCentreAndOffsetCamerasInBothOrientations() {
        for ((width, height) in listOf(1344 to 2992, 2992 to 1344)) {
            for (cameraX in listOf(80.5f, width / 2f, width - 80.5f)) {
                val frame = islandDrawViewport(CameraGeometry(cameraX, 60f, 24f, 4f, width, height))
                assertTrue(frame.left <= 0)
                assertTrue(frame.right >= width)
                assertEquals(0, frame.top)
                assertEquals(height, frame.bottom)
                assertTrue(abs((frame.left + frame.right) / 2f - cameraX) <= 0.5f)
            }
        }
    }
}
