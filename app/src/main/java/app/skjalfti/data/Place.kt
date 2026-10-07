package app.skjalfti.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Where a location came from. */
enum class PlaceSource { PRESET, GPS, SAVED }

/**
 * A place the phone can call home. Distances and wave arrival times are measured from here.
 * [atMs] is when a GPS fix was taken (0 for presets).
 */
data class Place(
    val id: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val source: PlaceSource,
    val atMs: Long = 0L,
) {
    /** "63.976°N 22.548°W" */
    val coords: String
        get() = String.format(
            Locale.ROOT, "%.3f°%s %.3f°%s",
            kotlin.math.abs(lat), if (lat >= 0) "N" else "S",
            kotlin.math.abs(lon), if (lon >= 0) "E" else "W",
        )

    fun same(other: Place, withinKm: Double = 0.5): Boolean = Geo.km(lat, lon, other.lat, other.lon) <= withinKm

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("lat", lat).put("lon", lon)
        .put("source", source.name).put("at", atMs)

    companion object {
        fun fromJson(o: JSONObject): Place? {
            val lat = o.optDouble("lat")
            val lon = o.optDouble("lon")
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return Place(
                id = o.optString("id").ifBlank { "p${(lat * 1000).toLong()}_${(lon * 1000).toLong()}" },
                label = o.optString("label").ifBlank { "Saved place" },
                lat = lat,
                lon = lon,
                source = runCatching { PlaceSource.valueOf(o.optString("source")) }.getOrDefault(PlaceSource.SAVED),
                atMs = o.optLong("at"),
            )
        }

        fun fromJson(s: String?): Place? = s?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

        fun listToJson(list: List<Place>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String?): List<Place> {
            if (s.isNullOrBlank()) return emptyList()
            val a = runCatching { JSONArray(s) }.getOrNull() ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { fromJson(it) } }
        }
    }
}

object Places {
    val NJARDVIK = Place("njardvik", "Njarðvík", 63.976, -22.548, PlaceSource.PRESET)

    /** Towns around the peninsula and the capital, for a quick pick and for naming GPS fixes. */
    val presets = listOf(
        NJARDVIK,
        Place("keflavik", "Keflavík", 64.002, -22.558, PlaceSource.PRESET),
        Place("grindavik", "Grindavík", 63.842, -22.433, PlaceSource.PRESET),
        Place("vogar", "Vogar", 63.980, -22.383, PlaceSource.PRESET),
        Place("hafnarfjordur", "Hafnarfjörður", 64.067, -21.952, PlaceSource.PRESET),
        Place("reykjavik", "Reykjavík", 64.146, -21.942, PlaceSource.PRESET),
    )

    /** More towns, only used to name a GPS fix when the phone has no geocoder. */
    private val towns = presets + listOf(
        Place("sandgerdi", "Sandgerði", 64.038, -22.707, PlaceSource.PRESET),
        Place("gardur", "Garður", 64.068, -22.646, PlaceSource.PRESET),
        Place("hafnir", "Hafnir", 63.934, -22.687, PlaceSource.PRESET),
        Place("asbru", "Ásbrú", 63.968, -22.585, PlaceSource.PRESET),
        Place("kopavogur", "Kópavogur", 64.112, -21.906, PlaceSource.PRESET),
        Place("gardabaer", "Garðabær", 64.089, -21.923, PlaceSource.PRESET),
        Place("selfoss", "Selfoss", 63.934, -21.000, PlaceSource.PRESET),
        Place("hveragerdi", "Hveragerði", 63.999, -21.187, PlaceSource.PRESET),
        Place("thorlakshofn", "Þorlákshöfn", 63.856, -21.383, PlaceSource.PRESET),
    )

    /**
     * A name for a fix without a geocoder: the town itself when within 3 km, "Near X" within
     * 15 km, otherwise the coordinates.
     */
    fun nameFor(lat: Double, lon: Double): String {
        val nearest = towns.minByOrNull { Geo.km(lat, lon, it.lat, it.lon) }
        val d = nearest?.let { Geo.km(lat, lon, it.lat, it.lon) } ?: Double.MAX_VALUE
        return when {
            nearest != null && d <= 3.0 -> nearest.label
            nearest != null && d <= 15.0 -> "Near ${nearest.label}"
            else -> String.format(Locale.ROOT, "%.2f°, %.2f°", lat, lon)
        }
    }
}
