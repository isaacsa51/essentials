package com.sameerasw.island.media.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ParticleFieldTest {
    @Test fun falloffChangesOnlyAlphaAndZeroExponentDoesNotEscapeEmissionBand() {
        val original = ParticleFieldStyle()
        val tile = generatePoissonTile(original.minDistance.value)
        val shape = CapsuleParticleGeometry.create(Size(448f,240f),0f,28f,Rect(44f,100f,404f,156f))!!
        fun snapshot(exponent: Float): Map<Offset,Pair<Float,Float>> = buildMap {
            val style = original.copy(falloffExponent = exponent).resolve(Density(1f))
            visitParticles(tile,shape,style,Offset.Zero,0.8f) { x,y,r,a -> put(Offset(x,y),r to a) }
        }
        val normal = snapshot(1f)
        val fast = snapshot(2f)
        val flat = snapshot(0f)
        assertTrue(fast.isNotEmpty())
        for ((position,value) in fast) {
            val baseline = normal.getValue(position)
            assertEquals(baseline.first,value.first,0f)
            assertEquals(baseline.second*baseline.second,value.second,0.00001f)
        }
        assertTrue(flat.keys.containsAll(normal.keys))
        for ((position,value) in flat) {
            assertEquals(1f,value.second,0f)
            assertTrue(shape.signedDistance(position) in -original.innerBand.value..original.outerBand.value)
        }
    }

    @Test fun stylePreservesRequestedSpacingAndLargeRadiiAndDisplacements() {
        val style = ParticleFieldStyle(minDistance = 0.5.dp,maxRadius = 6.dp,displacement = 28.dp).resolve(Density(1f))
        assertEquals(0.5f,style.minDistance,0f)
        assertEquals(6f,style.radius,0f)
        assertEquals(28f,style.displacement,0f)
    }

    @Test fun poissonSeedsRespectMinimumDistanceIncludingTileSeamsAndAreNotAxisAligned() {
        val distance = 6f
        val tile = generatePoissonTile(distance)
        assertTrue(tile.points.size > 1500)
        var axialX = 0.0
        var axialY = 0.0
        for ((i,p) in tile.points.withIndex()) {
            var nearest = Float.POSITIVE_INFINITY
            var direction = 0f
            for ((j,q) in tile.points.withIndex()) {
                if (i == j) continue
                var dx = q.x-p.x
                var dy = q.y-p.y
                if (dx > tile.width/2) dx -= tile.width
                if (dx < -tile.width/2) dx += tile.width
                if (dy > tile.height/2) dy -= tile.height
                if (dy < -tile.height/2) dy += tile.height
                val d2 = dx*dx+dy*dy
                assertTrue("minimum spacing violated",d2 >= distance*distance-0.001f)
                if (d2 < nearest) { nearest=d2; direction=atan2(dy,dx) }
            }
            axialX += cos(4.0*direction)
            axialY += sin(4.0*direction)
        }
        // A grid produces an order parameter near one; an isotropic blue-noise cloud does not.
        assertTrue(hypot(axialX,axialY)/tile.points.size < 0.1)
    }

    @Test fun seedGenerationIsDeterministicAndTiledQueriesIncludeOffscreenSources() {
        val a = generatePoissonTile(9f)
        val b = generatePoissonTile(9f)
        assertEquals(a.points,b.points)
        var count = 0
        a.visit(Rect(-a.width,-a.height,a.width,a.height)) { _,_ -> count++ }
        assertEquals(a.points.size*4,count)
    }

    @Test fun curlAndVelocityRadiusMatchTheHtmlWithTheSameSeededByteTable() {
        val cases = listOf(
            floatArrayOf(143.5f,216.5f,0.8f,-1.522089266516f,-1.787142881762f,7.270811850709f,1.681862475952f),
            floatArrayOf(0f,0f,1.6f,-4.646898654311f,3.593659643750f,3.462422277139f,1.769931484841f),
            floatArrayOf(-23f,95f,2.1f,-2.858582339406f,-0.635407518735f,3.434014779129f,1.770588408233f),
        )
        for (c in cases) {
            val offset = referenceCurlOffset(c[0],c[1],c[2],0.012f,4f)
            val next = referenceCurlOffset(c[0],c[1],c[2]+0.05f,0.012f,4f)
            val speed = (next-offset).getDistance()/0.05f
            assertEquals(c[3],offset.x,0.00002f)
            assertEquals(c[4],offset.y,0.00002f)
            assertEquals(c[5],speed,0.0002f)
            assertEquals(c[6],referenceParticleRadius(speed,0.5f,1f,2f),0.00002f)
        }
    }

    @Test fun fieldsStayContinuousAcrossCellsAndScaleWithDisplayDensity() {
        for (i in -8..8) {
            assertEquals(flowNoise(i-0.0001,0.37),flowNoise(i+0.0001,0.37),0.001)
        }
        val css = referenceCurlOffset(143.5f,216.5f,0.8f,0.012f,4f)
        val pixels = referenceCurlOffset(143.5f*3,216.5f*3,0.8f,0.012f/3,4f*3)
        assertEquals(css.x*3,pixels.x,0.00002f)
        assertEquals(css.y*3,pixels.y,0.00002f)
        val a = referenceParticleRadius(8f,0.5f,1f,2f)
        val b = referenceParticleRadius(8f*3,0.5f,3f,6f,60f)
        assertEquals(a*3,b,0.00001f)
    }

    @Test fun distanceFadesOpacityWhileRadiusRemainsCrispAndBounded() {
        assertEquals(1f,particleAlphaEnvelope(0f,15f,55f),0f)
        assertEquals(0f,particleAlphaEnvelope(-15f,15f,55f),0f)
        assertEquals(0f,particleAlphaEnvelope(55f,15f,55f),0f)
        assertEquals(0.5f,particleAlphaEnvelope(-7.5f,15f,55f),0.00001f)
        assertEquals(0.5f,particleAlphaEnvelope(27.5f,15f,55f),0.00001f)
        assertEquals(2f,referenceParticleRadius(0f,1f,1f,2f),0f)
        assertEquals(1.7f,referenceParticleRadius(0f,0f,1f,2f),0.00001f)
        assertEquals(1.5f,referenceParticleRadius(40f,1f,1f,2f),0f)
    }

    @Test fun simulationCarriesVaryingAlphaAndMinimumRadiusInsteadOfCollapsingDots() {
        val style = ParticleFieldStyle().resolve(Density(1f))
        val tile = generatePoissonTile(style.minDistance)
        val shape = CapsuleParticleGeometry.create(Size(448f,240f),0f,28f,Rect(44f,100f,404f,156f))!!
        fun snapshot(t: Float): List<List<Float>> = buildList {
            visitParticles(tile,shape,style,Offset.Zero,t) { x,y,r,a -> add(listOf(x,y,r,a)) }
        }
        val a = snapshot(0.8f)
        val b = snapshot(1.8f)
        assertTrue(a.size > 100)
        assertNotEquals(a,b)
        assertTrue(a.all { it[2] in style.minRadius..style.radius })
        assertTrue(a.any { it[3] < 0.2f })
        assertTrue(a.any { it[3] > 0.9f })
        assertTrue(a.filter { it[3] < 0.2f }.all { it[2] >= style.minRadius })
    }
}
