package app.skjalfti

import app.skjalfti.seismo.DetectorState
import app.skjalfti.seismo.HighPass
import app.skjalfti.seismo.Sensitivity
import app.skjalfti.seismo.StaLta
import app.skjalfti.seismo.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class DspTest {
    private val dt = 1f / 200f

    @Test
    fun highPassRemovesGravity() {
        val hp = HighPass(0.5f)
        var out = 0f
        repeat(200 * 20) { out = hp.step(1.0f, dt) }
        assertTrue("constant 1 g should filter to ~0, got $out", abs(out) < 1e-3f)
    }

    @Test
    fun highPassKeepsShaking() {
        val hp = HighPass(0.5f)
        var peak = 0f
        for (i in 0 until 200 * 5) {
            val v = 1f + 0.01f * sin(2 * PI * 5.0 * i * dt).toFloat()
            val o = hp.step(v, dt)
            if (i > 400) peak = maxOf(peak, abs(o))
        }
        assertTrue("5 Hz shaking should pass, peak $peak", peak > 0.008f)
    }

    /** Quiet noise, then a quake-like burst, then quiet again. */
    private fun run(burstG: Float, sensitivity: Sensitivity = Sensitivity.MED): List<Trigger> {
        val d = StaLta(sensitivity)
        val rnd = Random(1)
        val out = ArrayList<Trigger>()
        var t = 0L
        for (i in 0 until 200 * 60) {
            val sec = i * dt
            val noise = 0.0005f * (rnd.nextFloat() * 2 - 1)
            val burst = if (sec in 30f..36f) burstG * sin(2 * PI * 8.0 * sec).toFloat() else 0f
            t += 5
            d.step(abs(noise + burst), dt, t)?.let { out += it }
        }
        return out
    }

    @Test
    fun quietNoiseNeverTriggers() {
        assertEquals(0, run(0f, Sensitivity.HIGH).size)
    }

    @Test
    fun burstTriggersOnce() {
        val t = run(0.01f)
        assertEquals(1, t.size)
        assertFalse(t[0].bump)
        // Starts near the 30 s mark.
        assertTrue("start ${t[0].startMs}", t[0].startMs in 29_500L..31_500L)
    }

    @Test
    fun handlingIsABump() {
        val t = run(0.5f)
        assertNotNull(t.firstOrNull())
        assertTrue(t.first().bump)
    }

    @Test
    fun warmsUpFirst() {
        val d = StaLta()
        d.step(0.001f, dt, 5)
        assertEquals(DetectorState.WARMUP, d.state)
    }
}
