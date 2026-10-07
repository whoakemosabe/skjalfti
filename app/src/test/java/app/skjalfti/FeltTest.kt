package app.skjalfti

import app.skjalfti.data.Felt
import app.skjalfti.data.FeltStatus
import app.skjalfti.data.Geo
import app.skjalfti.data.Places
import app.skjalfti.data.Quake
import app.skjalfti.data.Span
import app.skjalfti.seismo.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeltTest {
    private val home = Places.NJARDVIK
    private val t0 = 1_800_000_000_000L
    // Roughly 14 km from Njarðvík, near Svartsengi.
    private val quake = Quake("q1", t0, 63.88, -22.43, 5.0, 2.4, "Svartsengi", true)

    @Test
    fun distanceIsSane() {
        val km = Geo.epicentreKm(quake, home)
        assertTrue("got $km", km in 10.0..15.0)
        assertTrue(Geo.hypocentreKm(quake, home) > km)
    }

    @Test
    fun feltWhenTriggerLandsInWindow() {
        val w = Felt.window(quake, home)
        val t = Trigger(w.first + 4000, w.first + 9000, 0.01f, bump = false)
        val m = Felt.match(quake, home, listOf(t), emptyList(), t0 + 600_000)
        assertEquals(FeltStatus.FELT, m.status)
        assertTrue((m.delaySec ?: -1.0) > 0)
    }

    @Test
    fun bumpsDontCount() {
        val w = Felt.window(quake, home)
        val bump = Trigger(w.first + 4000, w.first + 9000, 0.8f, bump = true)
        val span = Span(t0 - 60_000, t0 + 600_000)
        val m = Felt.match(quake, home, listOf(bump), listOf(span), t0 + 600_000)
        assertEquals(FeltStatus.MISS, m.status)
    }

    @Test
    fun missOfflinePending() {
        val span = Span(t0 - 60_000, t0 + 600_000)
        assertEquals(FeltStatus.MISS, Felt.match(quake, home, emptyList(), listOf(span), t0 + 600_000).status)
        assertEquals(FeltStatus.OFFLINE, Felt.match(quake, home, emptyList(), emptyList(), t0 + 600_000).status)
        assertEquals(FeltStatus.PENDING, Felt.match(quake, home, emptyList(), listOf(span), t0 + 1000).status)
    }

    @Test
    fun windowFollowsWaveSpeeds() {
        val d = Geo.hypocentreKm(quake, home)
        val w = Felt.window(quake, home)
        assertEquals(t0 + (d / 6.0 * 1000).toLong() - 3000, w.first)
        assertEquals(t0 + (d / 3.5 * 1000).toLong() + 15000, w.last)
    }
}
