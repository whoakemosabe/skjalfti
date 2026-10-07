package app.skjalfti

import android.content.Context
import app.skjalfti.data.Achievement
import app.skjalfti.data.Felt
import app.skjalfti.data.FeltStatus
import app.skjalfti.data.Geo
import app.skjalfti.data.Home
import app.skjalfti.data.Match
import app.skjalfti.data.NightReport
import app.skjalfti.data.Quake
import app.skjalfti.data.Quakes
import app.skjalfti.data.Store
import app.skjalfti.seismo.Channel
import app.skjalfti.seismo.Seismo
import app.skjalfti.seismo.Sensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One quake on the log, with how far away it was and whether the phone felt it. */
data class QuakeRow(val quake: Quake, val distKm: Double, val match: Match)

data class QuakeState(
    val loading: Boolean = false,
    val error: String? = null,
    val rows: List<QuakeRow> = emptyList(),
    val fetchedAtMs: Long = 0L,
    /** Events per hour over the last 24 h, oldest first. */
    val hourly: List<Int> = List(24) { 0 },
)

/**
 * App-wide state: settings, the quake log and the link between the seismometer and storage.
 * Shared by the screen and the night watch service, so it's a process-wide object.
 */
object Engine {
    lateinit var store: Store; private set
    lateinit var prefs: Prefs; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _skin = MutableStateFlow(Skin.PIXEL)
    val skin: StateFlow<Skin> = _skin.asStateFlow()
    private val _home = MutableStateFlow(Home.NJARDVIK)
    val home: StateFlow<Home> = _home.asStateFlow()
    private val _radius = MutableStateFlow(60)
    val radius: StateFlow<Int> = _radius.asStateFlow()
    private val _sensitivity = MutableStateFlow(Sensitivity.MED)
    val sensitivity: StateFlow<Sensitivity> = _sensitivity.asStateFlow()
    private val _channel = MutableStateFlow(Channel.Z)
    val channel: StateFlow<Channel> = _channel.asStateFlow()
    private val _gain = MutableStateFlow(2)
    val gain: StateFlow<Int> = _gain.asStateFlow()

    private val _quakes = MutableStateFlow(QuakeState())
    val quakes: StateFlow<QuakeState> = _quakes.asStateFlow()
    private var raw: List<Quake> = emptyList()

    /** True while the night watch service is recording. */
    val watching = MutableStateFlow(false)
    /** Bumped when the store changes in a way screens should notice (report, achievements). */
    val storeVersion = MutableStateFlow(0)

    private var initialised = false
    private var openSpan: Long? = null

    @Synchronized
    fun init(context: Context) {
        if (initialised) return
        initialised = true
        val app = context.applicationContext
        prefs = Prefs(app)
        store = Store(File(app.filesDir, "skjalfti.json"))
        _skin.value = prefs.skin
        _home.value = prefs.home
        _radius.value = prefs.radiusKm
        _sensitivity.value = prefs.sensitivity
        _channel.value = prefs.channel
        _gain.value = prefs.gain
        Seismo.setSensitivity(prefs.sensitivity)
        Seismo.onTrigger = { t ->
            store.addTrigger(t)
            scope.launch { recompute() }
        }
        Seismo.onHeartbeat = { wall -> openSpan?.let { store.extendSpan(it, wall) } }
    }

    fun setSkin(s: Skin) { prefs.skin = s; _skin.value = s }
    fun setHome(h: Home) { prefs.home = h; _home.value = h; scope.launch { recompute() } }
    fun setRadius(km: Int) { prefs.radiusKm = km; _radius.value = km; scope.launch { recompute() } }
    fun setSensitivity(s: Sensitivity) { prefs.sensitivity = s; _sensitivity.value = s; Seismo.setSensitivity(s) }
    fun setChannel(c: Channel) { prefs.channel = c; _channel.value = c }
    fun setGain(g: Int) { prefs.gain = g; _gain.value = g }

    /** Starts recording for [owner]; opens a recording span the first time. */
    @Synchronized
    fun listen(context: Context, owner: String) {
        val first = !Seismo.running
        Seismo.start(context, owner)
        if (first && Seismo.running) openSpan = store.beginSpan(System.currentTimeMillis())
    }

    @Synchronized
    fun unlisten(owner: String) {
        Seismo.stop(owner)
        if (!Seismo.running) {
            openSpan?.let { store.extendSpan(it, System.currentTimeMillis()) }
            openSpan = null
        }
    }

    /** Pulls the last 24 hours of quakes from IMO. */
    suspend fun refresh() {
        _quakes.value = _quakes.value.copy(loading = true, error = null)
        val now = System.currentTimeMillis()
        try {
            raw = withContext(Dispatchers.IO) { Quakes.fetch(now - 24L * 3600_000, now) }
            recompute(fetchedAt = now)
        } catch (e: Exception) {
            _quakes.value = _quakes.value.copy(loading = false, error = "OFFLINE")
        }
    }

    private fun recompute(fetchedAt: Long? = null) {
        val home = _home.value
        val radius = _radius.value
        val now = System.currentTimeMillis()
        val triggers = store.triggers()
        val spans = store.spans()
        val rows = raw
            .map { q -> QuakeRow(q, Geo.epicentreKm(q, home), Felt.match(q, home, triggers, spans, now)) }
            .filter { it.distKm <= radius }
        val hourly = MutableList(24) { 0 }
        rows.forEach { r ->
            val ago = ((now - r.quake.timeMs) / 3600_000).toInt()
            if (ago in 0..23) hourly[23 - ago]++
        }
        _quakes.value = QuakeState(
            loading = false,
            rows = rows,
            fetchedAtMs = fetchedAt ?: _quakes.value.fetchedAtMs,
            hourly = hourly,
        )
        checkAchievements(rows, now)
    }

    private fun checkAchievements(rows: List<QuakeRow>, now: Long) {
        var changed = false
        val felt = rows.filter { it.match.status == FeltStatus.FELT }
        if (felt.isNotEmpty()) changed = store.unlock(Achievement.FIRST_FELT, now) || changed
        if (felt.any { it.quake.magnitude >= 3.0 }) changed = store.unlock(Achievement.FELT_M3, now) || changed
        if (store.noteQuakes(rows.map { it.quake.id }) >= 100) changed = store.unlock(Achievement.CENTURION, now) || changed
        if (changed) storeVersion.value++
    }

    /** Builds and saves the morning report for a night watch from [startMs] to [endMs]. */
    suspend fun nightReport(startMs: Long, endMs: Long, strip: List<Float>): NightReport {
        val home = _home.value
        val radius = _radius.value
        val quakes = runCatching { withContext(Dispatchers.IO) { Quakes.fetch(startMs, endMs) } }.getOrDefault(emptyList())
            .filter { Geo.epicentreKm(it, home) <= radius }
        val triggers = store.triggers()
        val spans = store.spans()
        val now = System.currentTimeMillis()
        val matched = quakes.map { it to Felt.match(it, home, triggers, spans, now) }
        val strongest = matched.maxByOrNull { it.first.magnitude }
        val report = NightReport(
            startMs = startMs,
            endMs = endMs,
            quakes = quakes.size,
            felt = matched.count { it.second.status == FeltStatus.FELT },
            strongestMag = strongest?.first?.magnitude,
            strongestRegion = strongest?.first?.region,
            strongestTimeMs = strongest?.first?.timeMs,
            strongestFelt = strongest?.second?.status == FeltStatus.FELT,
            strongestPeakG = strongest?.second?.peakG,
            strip = strip,
        )
        store.saveReport(report)
        store.unlock(Achievement.FIRST_WATCH, now)
        if (store.nights >= 7) store.unlock(Achievement.NIGHT_WATCH, now)
        storeVersion.value++
        if (raw.isNotEmpty()) recompute()
        return report
    }
}
