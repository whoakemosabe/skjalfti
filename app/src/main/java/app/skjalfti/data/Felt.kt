package app.skjalfti.data

import app.skjalfti.seismo.Trigger
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Great-circle distance in km. */
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun epicentreKm(q: Quake, home: Place) = km(home.lat, home.lon, q.lat, q.lon)

    /** Straight-line distance to the quake's focus, through the ground. */
    fun hypocentreKm(q: Quake, home: Place): Double {
        val e = epicentreKm(q, home)
        return sqrt(e * e + q.depthKm * q.depthKm)
    }
}

/** A stretch of time the phone was recording. Wall-clock ms. */
data class Span(val startMs: Long, val endMs: Long)

enum class FeltStatus { FELT, MISS, OFFLINE, PENDING }

data class Match(
    val status: FeltStatus,
    /** Seconds after the quake's origin time when the phone started shaking. */
    val delaySec: Double? = null,
    val peakG: Float? = null,
    /** The detector trigger that matched, which is also the key of its trace clip. */
    val triggerStartMs: Long? = null,
)

/**
 * Did the phone feel a quake? Seismic P waves travel at roughly 6 km/s through the crust here
 * and S waves at roughly 3.5 km/s, so from the distance we know when shaking should reach the
 * phone. If the detector fired in that window (with a few seconds of slack for clock error) the
 * quake was FELT; if the phone was recording the whole window and stayed calm it was a MISS;
 * if it wasn't recording at all, OFFLINE. Bumps (the phone being handled) never count.
 */
object Felt {
    const val P_KMS = 6.0
    const val S_KMS = 3.5
    private const val SLACK_MS = 3_000L
    private const val TAIL_MS = 15_000L

    fun window(q: Quake, home: Place): LongRange {
        val d = Geo.hypocentreKm(q, home)
        val p = q.timeMs + (d / P_KMS * 1000).toLong()
        val s = q.timeMs + (d / S_KMS * 1000).toLong()
        return (p - SLACK_MS)..(s + TAIL_MS)
    }

    fun match(q: Quake, home: Place, triggers: List<Trigger>, spans: List<Span>, nowMs: Long): Match {
        val w = window(q, home)
        val hit = triggers
            .filter { !it.bump && it.startMs <= w.last && it.endMs >= w.first }
            .minByOrNull { it.startMs }
        if (hit != null) {
            return Match(FeltStatus.FELT, (hit.startMs - q.timeMs) / 1000.0, hit.peakG, hit.startMs)
        }
        if (nowMs < w.last) return Match(FeltStatus.PENDING)
        val covered = spans.any { it.startMs <= w.first && it.endMs >= w.last }
        return Match(if (covered) FeltStatus.MISS else FeltStatus.OFFLINE)
    }
}
