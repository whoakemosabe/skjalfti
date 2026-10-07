package app.skjalfti.data

import app.skjalfti.seismo.Trigger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The morning summary of one night watch. */
data class NightReport(
    val startMs: Long,
    val endMs: Long,
    val quakes: Int,
    val felt: Int,
    val strongestMag: Double?,
    val strongestRegion: String?,
    val strongestTimeMs: Long?,
    val strongestFelt: Boolean,
    val strongestPeakG: Float?,
    /** Compressed peak-per-minute trace of the night, in g, for the report's strip. */
    val strip: List<Float>,
)

enum class Achievement(val title: String, val detail: String) {
    FIRST_WATCH("FIRST WATCH", "Logged your first night"),
    NIGHT_WATCH("NIGHT WATCH", "7 nights logged"),
    FIRST_FELT("FIRST FELT", "Your phone felt a quake"),
    FELT_M3("BIG ONE", "Felt an M3 or larger"),
    CENTURION("CENTURION", "100 quakes on your log"),
}

/**
 * Everything the app remembers between launches, in one small JSON file: the detector's
 * triggers and the time spans the phone was recording (both kept for a week), the last night
 * report, nights logged and unlocked achievements. Thread-safe; every change saves.
 */
class Store(private val file: File) {
    private val lock = Any()
    private val triggers = ArrayList<Trigger>()
    private val spans = ArrayList<Span>()
    private val unlocked = LinkedHashMap<Achievement, Long>()
    private val seenQuakes = LinkedHashSet<String>()
    var nights = 0; private set
    var lastReport: NightReport? = null; private set

    init { load() }

    fun triggers(): List<Trigger> = synchronized(lock) { triggers.toList() }
    fun spans(): List<Span> = synchronized(lock) { spans.toList() }
    fun unlocked(): Map<Achievement, Long> = synchronized(lock) { unlocked.toMap() }

    fun addTrigger(t: Trigger) = edit { triggers += t }

    /** Opens a new recording span; its start time is the key for [extendSpan]. */
    fun beginSpan(nowMs: Long): Long = synchronized(lock) {
        spans += Span(nowMs, nowMs)
        save()
        nowMs
    }

    fun extendSpan(startMs: Long, nowMs: Long) = edit {
        val i = spans.indexOfLast { it.startMs == startMs }
        if (i >= 0 && nowMs > spans[i].endMs) spans[i] = spans[i].copy(endMs = nowMs)
    }

    /** Remembers quake ids for the CENTURION count. Returns how many are known. */
    fun noteQuakes(ids: Collection<String>): Int = synchronized(lock) {
        val before = seenQuakes.size
        seenQuakes.addAll(ids)
        if (seenQuakes.size != before) save()
        seenQuakes.size
    }

    fun saveReport(r: NightReport) = edit {
        lastReport = r
        nights += 1
    }

    /** Unlocks [a] if new; returns true the first time. */
    fun unlock(a: Achievement, nowMs: Long): Boolean = synchronized(lock) {
        if (a in unlocked) return false
        unlocked[a] = nowMs
        save()
        true
    }

    private inline fun edit(block: () -> Unit) = synchronized(lock) { block(); save() }

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - 7L * 24 * 3600 * 1000
        triggers.removeAll { it.endMs < cutoff }
        spans.removeAll { it.endMs < cutoff }
        while (seenQuakes.size > 5000) seenQuakes.remove(seenQuakes.first())
    }

    private fun save() {
        prune(System.currentTimeMillis())
        val o = JSONObject()
        o.put("triggers", JSONArray().apply {
            triggers.forEach { put(JSONObject().put("s", it.startMs).put("e", it.endMs).put("p", it.peakG.toDouble()).put("b", it.bump)) }
        })
        o.put("spans", JSONArray().apply { spans.forEach { put(JSONArray().put(it.startMs).put(it.endMs)) } })
        o.put("nights", nights)
        o.put("unlocked", JSONObject().apply { unlocked.forEach { (k, v) -> put(k.name, v) } })
        o.put("seen", JSONArray(seenQuakes.toList()))
        lastReport?.let { r ->
            o.put("report", JSONObject()
                .put("start", r.startMs).put("end", r.endMs)
                .put("quakes", r.quakes).put("felt", r.felt)
                .put("mag", r.strongestMag ?: JSONObject.NULL)
                .put("region", r.strongestRegion ?: JSONObject.NULL)
                .put("time", r.strongestTimeMs ?: JSONObject.NULL)
                .put("sfelt", r.strongestFelt)
                .put("peak", r.strongestPeakG?.toDouble() ?: JSONObject.NULL)
                .put("strip", JSONArray().apply { r.strip.forEach { put(it.toDouble()) } }))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    private fun load() {
        if (!file.exists()) return
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        o.optJSONArray("triggers")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                triggers += Trigger(t.optLong("s"), t.optLong("e"), t.optDouble("p").toFloat(), t.optBoolean("b"))
            }
        }
        o.optJSONArray("spans")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optJSONArray(i) ?: continue
                spans += Span(s.optLong(0), s.optLong(1))
            }
        }
        nights = o.optInt("nights")
        o.optJSONObject("unlocked")?.let { u ->
            u.keys().forEach { k -> runCatching { Achievement.valueOf(k) }.getOrNull()?.let { unlocked[it] = u.optLong(k) } }
        }
        o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length()) seenQuakes += a.optString(i) }
        o.optJSONObject("report")?.let { r ->
            fun d(k: String): Double? = if (r.isNull(k)) null else r.optDouble(k)
            val strip = r.optJSONArray("strip")
            lastReport = NightReport(
                startMs = r.optLong("start"), endMs = r.optLong("end"),
                quakes = r.optInt("quakes"), felt = r.optInt("felt"),
                strongestMag = d("mag"),
                strongestRegion = if (r.isNull("region")) null else r.optString("region"),
                strongestTimeMs = if (r.isNull("time")) null else r.optLong("time"),
                strongestFelt = r.optBoolean("sfelt"),
                strongestPeakG = d("peak")?.toFloat(),
                strip = if (strip == null) emptyList() else List(strip.length()) { strip.optDouble(it).toFloat() },
            )
        }
    }
}
