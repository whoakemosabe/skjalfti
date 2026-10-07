package app.skjalfti

import app.skjalfti.data.Quakes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class QuakesTest {
    // The shape api.vedur.is/quakes/events returns (two real events, trimmed), plus a broken one.
    private val sample = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[-21.954281,63.958622]},
           "properties":{"event_id":"IMO2026tsdvfl","time":"2026-10-07T00:06:34.011211Z","magnitude":-0.114518,
             "depth":4.551627,"region":"Kleifarvatn","type":"earthquake","evaluation_mode":"manual",
             "updated_time":"2026-10-07T11:27:49.627324Z"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[-23.894039,63.516888]},
           "properties":{"event_id":"IMO2026tseaqa","time":"2026-10-07T00:12:41.927087Z","magnitude":2.014422,
             "depth":10.0,"region":"Reykjaneshryggur","type":"earthquake","evaluation_mode":"automatic"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[]},"properties":{"time":"bad"}}
        ]}
    """.trimIndent()

    @Test
    fun parsesImoGeoJson() {
        val q = Quakes.parse(sample)
        assertEquals(2, q.size)
        // Newest first.
        assertEquals("IMO2026tseaqa", q[0].id)
        assertEquals(2.014422, q[0].magnitude, 1e-6)
        assertEquals(63.516888, q[0].lat, 1e-6)
        assertEquals(-23.894039, q[0].lon, 1e-6)
        assertEquals("Reykjaneshryggur", q[0].region)
        assertFalse(q[0].reviewed)
        assertTrue(q[1].reviewed)
        assertEquals(Instant.parse("2026-10-07T00:06:34.011Z").toEpochMilli(), q[1].timeMs)
        assertEquals(4.551627, q[1].depthKm, 1e-6)
    }

    @Test
    fun emptyIsFine() {
        assertEquals(0, Quakes.parse("""{"type":"FeatureCollection","features":[]}""").size)
        assertEquals(0, Quakes.parse("{}").size)
    }

    @Test
    fun urlUsesIsoTimes() {
        val u = Quakes.url(0L, 3_600_000L)
        assertEquals("https://api.vedur.is/quakes/events?start_time=1970-01-01T00:00:00Z&end_time=1970-01-01T01:00:00Z", u)
    }
}
