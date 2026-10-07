package app.skjalfti.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter

/** One earthquake from the Icelandic Met Office (Veðurstofa Íslands). */
data class Quake(
    val id: String,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val depthKm: Double,
    val magnitude: Double,
    /** IMO's region name, e.g. "Kleifarvatn" or "Reykjaneshryggur". */
    val region: String,
    /** Checked by a seismologist ("manual") rather than automatic. */
    val reviewed: Boolean,
)

object Quakes {
    private const val BASE = "https://api.vedur.is/quakes/events"

    /**
     * Parses IMO's GeoJSON FeatureCollection. Each feature has geometry.coordinates as
     * [lon, lat] and properties event_id, time (ISO, UTC), magnitude, depth (km), region and
     * evaluation_mode ("manual" or "automatic"). Broken features are skipped, not fatal.
     */
    fun parse(json: String): List<Quake> {
        val root = JSONObject(json)
        val features = root.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Quake>(features.length())
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val p = f.optJSONObject("properties") ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            if (lat.isNaN() || lon.isNaN()) continue
            val time = p.optString("time").takeIf { it.isNotBlank() } ?: continue
            val ms = runCatching { Instant.parse(time).toEpochMilli() }.getOrNull() ?: continue
            val mag = p.optDouble("magnitude")
            if (mag.isNaN()) continue
            val depth = p.optDouble("depth").let { if (it.isNaN()) (if (coords.length() > 2) coords.optDouble(2, 0.0) else 0.0) else it }
            out += Quake(
                id = p.optString("event_id").ifBlank { f.optString("id").ifBlank { "q$ms" } },
                timeMs = ms,
                lat = lat,
                lon = lon,
                depthKm = depth,
                magnitude = mag,
                region = p.optString("region").ifBlank { "Iceland" },
                reviewed = p.optString("evaluation_mode").equals("manual", ignoreCase = true),
            )
        }
        return out.sortedByDescending { it.timeMs }
    }

    fun url(fromMs: Long, toMs: Long): String {
        val f = DateTimeFormatter.ISO_INSTANT
        return "$BASE?start_time=${f.format(Instant.ofEpochMilli(fromMs))}&end_time=${f.format(Instant.ofEpochMilli(toMs))}"
    }

    /** Blocking fetch; call from a background dispatcher. */
    fun fetch(fromMs: Long, toMs: Long): List<Quake> {
        val conn = URL(url(fromMs, toMs)).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Skjalfti/1.0 (Android seismograph app)")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("IMO quakes HTTP $code")
            return parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
