package app.skjalfti

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun hhmm(ms: Long): String = hm.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val s = ((now - ms) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }

    fun mag(m: Double): String = String.format(Locale.ROOT, "%.1f", m)

    /** Acceleration in g: ".012" in the console, "0.012 g" on glass. */
    fun g(v: Float, short: Boolean): String {
        val s = when {
            v >= 1f -> String.format(Locale.ROOT, "%.2f", v)
            v >= 0.1f -> String.format(Locale.ROOT, "%.2f", v)
            else -> String.format(Locale.ROOT, "%.3f", v)
        }
        return if (short) s.removePrefix("0") else "$s g"
    }

    fun km(d: Double): String = "${d.roundToInt()} km"
}
