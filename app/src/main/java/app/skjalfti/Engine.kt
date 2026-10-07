package app.skjalfti

import android.content.Context
import app.skjalfti.data.Achievement
import app.skjalfti.alerts.QuakeAlerts
import app.skjalfti.data.Clip
import app.skjalfti.data.Clips
import app.skjalfti.data.Felt
import app.skjalfti.data.FeltStatus
import app.skjalfti.data.Geo
import app.skjalfti.data.Locator
import app.skjalfti.data.Place
import app.skjalfti.data.PlaceSource
import app.skjalfti.data.Places
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

sealed interface LocateState {
    data object Idle : LocateState
    data object Locating : LocateState
    data class Failed(val why: String) : LocateState
}

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
    lateinit var clips: Clips; private set
    lateinit var app: Context; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _skin = MutableStateFlow(Skin.PIXEL)
    val skin: StateFlow<Skin> = _skin.asStateFlow()
    private val _home = MutableStateFlow(Places.NJARDVIK)
    val home: StateFlow<Place> = _home.asStateFlow()
    private val _saved = MutableStateFlow<List<Place>>(emptyList())
    val saved: StateFlow<List<Place>> = _saved.asStateFlow()
    private val _autoLocate = MutableStateFlow(false)
    val autoLocate: StateFlow<Boolean> = _autoLocate.asStateFlow()
    private val _locating = MutableStateFlow<LocateState>(LocateState.Idle)
    val locating: StateFlow<LocateState> = _locating.asStateFlow()
    private val _radius = MutableStateFlow(60)
    val radius: StateFlow<Int> = _radius.asStateFlow()
    private val _sensitivity = MutableStateFlow(Sensitivity.MED)
    val sensitivity: StateFlow<Sensitivity> = _sensitivity.asStateFlow()
    private val _channel = MutableStateFlow(Channel.Z)
    val channel: StateFlow<Channel> = _channel.asStateFlow()
    private val _gain = MutableStateFlow(2)
    val gain: StateFlow<Int> = _gain.asStateFlow()

    private val _alertMag = MutableStateFlow(3.0f)
    /** Notify for quakes at least this big within the radius; 0 = off. */
    val alertMag: StateFlow<Float> = _alertMag.asStateFlow()

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
        this.app = app
        prefs = Prefs(app)
        store = Store(File(app.filesDir, "skjalfti.json"))
        clips = Clips(File(app.filesDir, "clips"))
        _alertMag.value = prefs.alertMag
        _skin.value = prefs.skin
        _home.value = prefs.home
        _saved.value = prefs.saved
        _autoLocate.value = prefs.autoLocate
        _radius.value = prefs.radiusKm
        _sensitivity.value = prefs.sensitivity
        _channel.value = prefs.channel
        _gain.value = prefs.gain
        Seismo.setSensitivity(prefs.sensitivity)
        Seismo.onTrigger = { t ->
            store.addTrigger(t)
            scope.launch { recompute() }
        }
        Seismo.onClip = { c -> clips.save(c) }
        Seismo.onHeartbeat = { wall -> openSpan?.let { store.extendSpan(it, wall) } }
    }

    fun setSkin(s: Skin) { prefs.skin = s; _skin.value = s }
    fun setHome(h: Place) {
        prefs.home = h
        _home.value = h
        scope.launch { recompute() }
    }

    fun setAlertMag(m: Float) {
        prefs.alertMag = m
        // Start the clock now so turning alerts on never replays old quakes.
        if (m > 0f) prefs.alertsSince = System.currentTimeMillis()
        _alertMag.value = m
    }

    /** The trace clip for a quake the phone felt, if one was saved. */
    fun clipFor(row: QuakeRow): Clip? = row.match.triggerStartMs?.let { clips.load(it) }

    fun row(id: String): QuakeRow? = _quakes.value.rows.firstOrNull { it.quake.id == id }

    fun setAutoLocate(on: Boolean) { prefs.autoLocate = on; _autoLocate.value = on }

    fun markLocationAsked() { prefs.locationAsked = true }
    val locationAsked: Boolean get() = prefs.locationAsked

    /**
     * Finds the phone with GPS and makes that home. With [auto] (the app opening), a fix within a
     * kilometre of a GPS home only refreshes it quietly, and failures stay silent.
     * Returns the new home, or null if nothing was found.
     */
    suspend fun locate(context: Context, auto: Boolean = false): Place? {
        if (_locating.value == LocateState.Locating) return null
        if (!Locator.hasPermission(context)) {
            if (!auto) _locating.value = LocateState.Failed("Location permission needed")
            return null
        }
        if (!Locator.servicesOn(context)) {
            if (!auto) _locating.value = LocateState.Failed("Location is turned off")
            return null
        }
        _locating.value = LocateState.Locating
        val fix = runCatching { Locator.current(context) }.getOrNull()
        if (fix == null) {
            _locating.value = if (auto) LocateState.Idle else LocateState.Failed("No fix yet. Try near a window")
            return null
        }
        val now = System.currentTimeMillis()
        val old = _home.value
        val moved = Geo.km(old.lat, old.lon, fix.latitude, fix.longitude)
        if (auto && old.source == PlaceSource.GPS && moved < 1.0) {
            setHomeQuiet(old.copy(atMs = now))
            _locating.value = LocateState.Idle
            return old
        }
        // A saved place this close wins, so its name sticks.
        val savedMatch = _saved.value.firstOrNull { Geo.km(it.lat, it.lon, fix.latitude, fix.longitude) <= 0.3 }
        val place = savedMatch ?: Place(
            id = "gps",
            label = Locator.nameFor(context, fix.latitude, fix.longitude),
            lat = fix.latitude,
            lon = fix.longitude,
            source = PlaceSource.GPS,
            atMs = now,
        )
        setHome(place)
        _locating.value = LocateState.Idle
        return place
    }

    private fun setHomeQuiet(h: Place) { prefs.home = h; _home.value = h }

    /** Saves the current home under [label] and keeps it selected. */
    fun savePlace(label: String) {
        val h = _home.value
        val p = h.copy(id = "s${System.currentTimeMillis()}", label = label.trim().ifBlank { h.label }, source = PlaceSource.SAVED)
        val list = listOf(p) + _saved.value.filterNot { it.same(p, 0.3) }
        prefs.saved = list
        _saved.value = list
        setHome(p)
    }

    fun deletePlace(id: String) {
        val list = _saved.value.filterNot { it.id == id }
        prefs.saved = list
        _saved.value = list
        // Deleting the place in use keeps it as home, just no longer saved.
        val h = _home.value
        if (h.id == id) setHomeQuiet(h.copy(id = "gps", source = PlaceSource.GPS))
    }

    fun clearLocateError() { if (_locating.value is LocateState.Failed) _locating.value = LocateState.Idle }
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
            runCatching { QuakeAlerts.check(app, raw) }
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
