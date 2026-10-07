package app.skjalfti

import app.skjalfti.data.Achievement
import app.skjalfti.data.NightReport
import app.skjalfti.data.Store
import app.skjalfti.seismo.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoreTest {
    private fun tmp(): File = File(Files.createTempDirectory("skj").toFile(), "s.json")

    @Test
    fun roundTrips() {
        val f = tmp()
        val now = System.currentTimeMillis()
        val s = Store(f)
        s.addTrigger(Trigger(now - 5000, now - 1000, 0.012f, false))
        val key = s.beginSpan(now - 60_000)
        s.extendSpan(key, now)
        s.saveReport(NightReport(now - 8 * 3600_000, now, 3, 1, 2.0, "Svartsengi", now - 3600_000, true, 0.006f, listOf(0.001f, 0.004f)))
        assertTrue(s.unlock(Achievement.FIRST_WATCH, now))
        assertFalse(s.unlock(Achievement.FIRST_WATCH, now))

        val r = Store(f)
        assertEquals(1, r.triggers().size)
        assertEquals(0.012f, r.triggers()[0].peakG, 1e-6f)
        assertEquals(now, r.spans()[0].endMs)
        assertEquals(1, r.nights)
        val rep = r.lastReport
        assertNotNull(rep)
        assertEquals("Svartsengi", rep!!.strongestRegion)
        assertEquals(2, rep.strip.size)
        assertTrue(Achievement.FIRST_WATCH in r.unlocked())
    }

    @Test
    fun spanKeySurvivesPruning() {
        val f = tmp()
        val now = System.currentTimeMillis()
        val s = Store(f)
        // An old span (8 days ago) gets pruned on the next save; the new one must still extend.
        val old = s.beginSpan(now - 8L * 24 * 3600_000)
        val key = s.beginSpan(now - 1000)
        s.extendSpan(old, now - 8L * 24 * 3600_000 + 10)
        s.extendSpan(key, now)
        assertEquals(1, s.spans().size)
        assertEquals(now, s.spans()[0].endMs)
    }

    @Test
    fun corruptFileStartsFresh() {
        val f = tmp()
        f.writeText("{not json")
        assertEquals(0, Store(f).triggers().size)
    }
}
