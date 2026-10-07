package app.skjalfti.seismo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import app.skjalfti.data.Clip
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Which trace to show: one axis, or the combined shaking in all three. */
enum class Channel(val label: String) { Z("Z"), X("X"), Y("Y"), SUM("SUM") }

/**
 * The phone's seismometer. Reads the accelerometer at ~200 Hz on its own thread, high-passes
 * each axis to drop gravity, keeps the last seventy seconds in a ring buffer for drawing, and
 * runs the STA/LTA detector on the combined shaking.
 *
 * Several parts of the app can use it at once (the live screen, the night watch); it runs while
 * any of them holds it ([start]/[stop] by owner name).
 *
 * The ring buffer is written on the sensor thread and read on the UI thread each frame without
 * a lock: [head] is published last, so a reader sees complete samples up to it.
 */
object Seismo {
    private const val RATE_HZ = 200
    const val CAPACITY = RATE_HZ * 70

    val tNs = LongArray(CAPACITY)
    val x = FloatArray(CAPACITY)
    val y = FloatArray(CAPACITY)
    val z = FloatArray(CAPACITY)
    val sum = FloatArray(CAPACITY)

    /** Index of the newest sample, -1 before the first. */
    @Volatile var head = -1; private set
    /** Samples written so far (caps at [CAPACITY]). */
    @Volatile var count = 0; private set

    val detector = StaLta()
    @Volatile var lastTrigger: Trigger? = null; private set
    /** Elapsed-clock ms when the detector last fired, for the console's flash. */
    @Volatile var lastTriggerAtElapsedMs = 0L; private set

    /** Called on the sensor thread whenever a burst of shaking ends. */
    @Volatile var onTrigger: ((Trigger) -> Unit)? = null
    /** Called with a trace clip about ten seconds after each (non-bump) trigger ends. */
    @Volatile var onClip: ((Clip) -> Unit)? = null
    private val pendingClips = ArrayList<Trigger>()

    /** Called on the sensor thread every ~20 s while running, and on stop, with wall-clock ms. */
    @Volatile var onHeartbeat: ((Long) -> Unit)? = null

    private val owners = LinkedHashSet<String>()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var manager: SensorManager? = null
    private val hx = HighPass(); private val hy = HighPass(); private val hz = HighPass()
    private var lastNs = 0L
    private var wallOffsetMs = 0L
    private var lastBeatNs = 0L

    // Per-minute peak recorder for the night report strip.
    private val strip = ArrayList<Float>()
    @Volatile private var stripOn = false
    private var stripMinuteStartNs = 0L
    private var stripPeak = 0f

    val running: Boolean get() = synchronized(owners) { owners.isNotEmpty() }

    fun hasSensor(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun start(context: Context, owner: String) {
        synchronized(owners) {
            val wasEmpty = owners.isEmpty()
            owners += owner
            if (!wasEmpty) return
        }
        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val t = HandlerThread("seismo", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
        val h = Handler(t.looper)
        thread = t; handler = h; manager = sm
        h.post {
            hx.reset(); hy.reset(); hz.reset(); detector.reset()
            lastNs = 0L; lastBeatNs = 0L
            wallOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos() / 1_000_000
        }
        sm.registerListener(listener, sensor, 1_000_000 / RATE_HZ, h)
        onHeartbeat?.invoke(System.currentTimeMillis())
    }

    fun stop(owner: String) {
        synchronized(owners) {
            if (!owners.remove(owner) || owners.isNotEmpty()) return
        }
        manager?.unregisterListener(listener)
        flushClips(force = true)
        onHeartbeat?.invoke(System.currentTimeMillis())
        thread?.quitSafely()
        thread = null; handler = null; manager = null
    }

    fun setSensitivity(s: Sensitivity) { detector.sensitivity = s }

    private const val CLIP_BEFORE_MS = 20_000L
    private const val CLIP_AFTER_MS = 10_000L
    private const val CLIP_MAX_MS = 60_000L

    /** Saves a clip for every pending trigger whose tail has been recorded (or all, on stop). */
    private fun flushClips(force: Boolean) {
        val now = System.currentTimeMillis()
        val due = synchronized(pendingClips) {
            val d = pendingClips.filter { force || now >= it.endMs + CLIP_AFTER_MS }
            pendingClips.removeAll(d.toSet())
            d
        }
        for (t in due) {
            val from = t.startMs - CLIP_BEFORE_MS
            val to = minOf(t.endMs + CLIP_AFTER_MS, from + CLIP_MAX_MS)
            val snap = snapshot(from, to, 100) ?: continue
            runCatching { onClip?.invoke(Clip(from, 100, snap.first, snap.second, t.startMs, t.endMs, t.peakG)) }
        }
    }

    /**
     * Resamples the ring buffer between two wall-clock times to [rateHz]: the vertical axis
     * averaged per bin, the combined shaking as each bin's peak. Gaps hold the last value.
     */
    fun snapshot(fromWallMs: Long, toWallMs: Long, rateHz: Int): Pair<FloatArray, FloatArray>? {
        val h = head
        if (h < 0 || toWallMs <= fromWallMs) return null
        val n = ((toWallMs - fromWallMs) * rateHz / 1000).toInt()
        if (n <= 0) return null
        val zs = FloatArray(n)
        val ss = FloatArray(n)
        val counts = IntArray(n)
        var i = h
        var k = 0
        val total = count
        while (k < total) {
            val w = wallMs(tNs[i])
            if (w < fromWallMs) break
            if (w < toWallMs) {
                val b = ((w - fromWallMs) * rateHz / 1000).toInt().coerceIn(0, n - 1)
                zs[b] += z[i]
                counts[b]++
                if (sum[i] > ss[b]) ss[b] = sum[i]
            }
            i = if (i == 0) CAPACITY - 1 else i - 1
            k++
        }
        var any = false
        var lastZ = 0f
        var lastS = 0f
        for (b in 0 until n) {
            if (counts[b] > 0) { zs[b] /= counts[b]; lastZ = zs[b]; lastS = ss[b]; any = true }
            else { zs[b] = lastZ; ss[b] = lastS }
        }
        return if (any) zs to ss else null
    }

    fun startStrip() {
        synchronized(strip) { strip.clear(); stripPeak = 0f; stripMinuteStartNs = 0L }
        stripOn = true
    }

    fun stopStrip(): List<Float> {
        stripOn = false
        return synchronized(strip) { strip.toList() }
    }

    fun array(c: Channel): FloatArray = when (c) {
        Channel.Z -> z; Channel.X -> x; Channel.Y -> y; Channel.SUM -> sum
    }

    /** Wall-clock ms for a sample's elapsed-clock nanoseconds. */
    fun wallMs(ns: Long): Long = wallOffsetMs + ns / 1_000_000

    /** Largest |value| on [c] over the last [windowMs]. */
    fun peak(c: Channel, windowMs: Long): Float {
        val h = head
        if (h < 0) return 0f
        val a = array(c)
        val newest = tNs[h]
        var i = h
        var n = 0
        var p = 0f
        val total = count
        while (n < total) {
            if ((newest - tNs[i]) / 1_000_000 > windowMs) break
            p = max(p, abs(a[i]))
            i = if (i == 0) CAPACITY - 1 else i - 1
            n++
        }
        return p
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val ns = e.timestamp
            val dt = if (lastNs == 0L) 1f / RATE_HZ else ((ns - lastNs) / 1e9f)
            lastNs = ns
            val gx = hx.step(e.values[0] / G, dt)
            val gy = hy.step(e.values[1] / G, dt)
            val gz = hz.step(e.values[2] / G, dt)
            val m = sqrt(gx * gx + gy * gy + gz * gz)

            val i = if (head + 1 >= CAPACITY) 0 else head + 1
            tNs[i] = ns; x[i] = gx; y[i] = gy; z[i] = gz; sum[i] = m
            if (count < CAPACITY) count++
            head = i

            val wall = wallOffsetMs + ns / 1_000_000
            detector.step(m, dt, wall)?.let { t ->
                lastTrigger = t
                onTrigger?.invoke(t)
                if (!t.bump) {
                    synchronized(pendingClips) { pendingClips += t }
                    handler?.postDelayed({ flushClips(force = false) }, CLIP_AFTER_MS + 200)
                }
            }
            if (detector.state == DetectorState.SHAKE) lastTriggerAtElapsedMs = ns / 1_000_000

            if (stripOn) synchronized(strip) {
                if (stripMinuteStartNs == 0L) stripMinuteStartNs = ns
                stripPeak = max(stripPeak, m)
                if (ns - stripMinuteStartNs >= 60_000_000_000L) {
                    strip += stripPeak
                    stripPeak = 0f
                    stripMinuteStartNs = ns
                }
            }

            if (ns - lastBeatNs > 20_000_000_000L) {
                lastBeatNs = ns
                onHeartbeat?.invoke(wall)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
