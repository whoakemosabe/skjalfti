package app.skjalfti

import app.skjalfti.data.Place
import app.skjalfti.data.PlaceSource
import app.skjalfti.data.Places
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTest {
    @Test
    fun roundTripsThroughJson() {
        val p = Place("s1", "Heima", 63.9771, -22.5502, PlaceSource.SAVED, 123L)
        assertEquals(p, Place.fromJson(p.toJson().toString()))
        val list = listOf(p, Places.NJARDVIK)
        assertEquals(list, Place.listFromJson(Place.listToJson(list)))
    }

    @Test
    fun rejectsJunk() {
        assertNull(Place.fromJson("{not json"))
        assertNull(Place.fromJson("""{"lat":200,"lon":0}"""))
        assertEquals(0, Place.listFromJson(null).size)
        assertEquals(0, Place.listFromJson("[1,2]").size)
    }

    @Test
    fun namesFixesByNearestTown() {
        assertEquals("Njarðvík", Places.nameFor(63.977, -22.55))
        assertTrue(Places.nameFor(63.90, -22.30).startsWith("Near "))
        assertEquals("65.68°, -18.09°", Places.nameFor(65.68, -18.09))
    }

    @Test
    fun coordsReadWell() {
        assertEquals("63.976°N 22.548°W", Places.NJARDVIK.coords)
        assertTrue(Places.NJARDVIK.same(Place("x", "x", 63.977, -22.549, PlaceSource.GPS)))
    }
}
