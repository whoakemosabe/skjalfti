package app.skjalfti

import app.skjalfti.data.AlertRule
import app.skjalfti.data.Clip
import app.skjalfti.data.Clips
import app.skjalfti.data.Coast
import app.skjalfti.data.Places
import app.skjalfti.data.Quake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class NewFeaturesTest {
    private val now = 1_800_000_000_000L
    private fun q(id: String, mag: Double, agoMin: Int, lat: Double = 63.88, lon: Double = -22.43) =
        Quake(id, now - agoMin * 60_000L, lat, lon, 5.0, mag, "Svartsengi", true)

    @Test
    fun alertsPickBigNearbyNewOnes() {
        val list = listOf(
            q("small", 2.0, 10),
            q("big", 3.4, 10),
            q("old", 3.4, 300),
            q("far", 4.0, 10, lat = 65.7, lon = -16.9),
            q("seen", 3.1, 5),
        )
        val picks = AlertRule.pick(list, 3.0f, 60, Places.NJARDVIK, since = 0L, already = setOf("seen"), now = now)
        assertEquals(listOf("big"), picks.map { it.id })
        assertEquals(0, AlertRule.pick(list, 0f, 60, Places.NJARDVIK, 0L, emptySet(), now).size)
        // Nothing from before alerts were turned on.
        assertEquals(0, AlertRule.pick(list, 3.0f, 60, Places.NJARDVIK, since = now - 60_000, already = emptySet(), now = now).size)
    }

    @Test
    fun clipsRoundTripAndPrune() {
        val dir = Files.createTempDirectory("clips").toFile()
        val clips = Clips(dir, keep = 3)
        for (i in 1..5) {
            val n = 100 * i
            clips.save(Clip(i * 1000L, 100, FloatArray(n) { it * 0.001f }, FloatArray(n) { 0.5f }, i * 1000L + 20_000, i * 1000L + 25_000, 0.01f * i))
        }
        assertEquals(listOf(25_000L, 24_000L, 23_000L), clips.list())
        assertNull(clips.load(21_000L))
        val c = clips.load(25_000L)
        assertNotNull(c)
        assertEquals(500, c!!.z.size)
        assertEquals(0.499f, c.z[499], 1e-6f)
        assertEquals(5_000L + 5000L, c.endMs)
        assertEquals(0.05f, c.peakG, 1e-6f)
        assertEquals(499, c.index(Long.MAX_VALUE / 4))
    }

    @Test
    fun coastlineKnowsLandFromSea() {
        assertTrue("Njarðvík is land", Coast.isLand(-22.548f, 63.976f))
        assertTrue("Reykjavík is land", Coast.isLand(-21.90f, 64.13f))
        assertFalse("Faxaflói is sea", Coast.isLand(-22.4f, 64.15f))
        assertFalse("West of Reykjanestá is sea", Coast.isLand(-23.2f, 63.8f))
    }
}
