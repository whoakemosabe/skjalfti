package app.skjalfti.seismo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Standard gravity, for turning m/s² into g. */
const val G = 9.80665f

/**
 * First-order high-pass filter. Removes gravity and slow tilt so only shaking is left.
 * [cutoffHz] around 0.5 keeps the 1–20 Hz band where local quakes live.
 */
class HighPass(private val cutoffHz: Float = 0.5f) {
    private var lastIn = 0f
    private var lastOut = 0f
    private var primed = false

    fun reset() { primed = false; lastIn = 0f; lastOut = 0f }

    fun step(x: Float, dtSec: Float): Float {
        if (!primed) { primed = true; lastIn = x; lastOut = 0f; return 0f }
        val rc = 1f / (2f * PI.toFloat() * cutoffHz)
        val a = rc / (rc + dtSec.coerceIn(0.0005f, 0.1f))
        val y = a * (lastOut + x - lastIn)
        lastIn = x
        lastOut = y
        return y
    }
}

/** How eager the trigger is. Higher sensitivity = lower STA/LTA ratio needed. */
enum class Sensitivity(val label: String, val onRatio: Float) {
    LOW("LOW", 4.0f),
    MED("MED", 3.0f),
    HIGH("HIGH", 2.4f),
}

/** One burst of shaking the phone noticed. Times are wall-clock milliseconds. */
data class Trigger(
    val startMs: Long,
    val endMs: Long,
    val peakG: Float,
    /** Too strong to be a distant quake: someone picked up or bumped the phone. */
    val bump: Boolean,
)

enum class DetectorState { WARMUP, QUIET, SHAKE }

/**
 * Classic STA/LTA event detector, as used on real seismographs. It compares short-term
 * average energy (half a second) against long-term background energy (fifteen seconds); a
 * jump in the ratio means something arrived. The background is frozen while triggered so a
 * long quake can't raise its own bar.
 *
 * Feed it the high-passed vector magnitude in g, one sample at a time.
 */
class StaLta(
    var sensitivity: Sensitivity = Sensitivity.MED,
    private val staSec: Float = 0.5f,
    private val ltaSec: Float = 15f,
    private val offRatio: Float = 1.6f,
    /** Below this the ratio can jump on pure sensor noise, so ignore it. */
    private val minG: Float = 0.0015f,
    /** Above this it's a bump, not a quake you could feel from kilometres away. */
    private val bumpG: Float = 0.15f,
    private val warmupSec: Float = 10f,
) {
    var sta = 0f; private set
    var lta = 0f; private set
    var state = DetectorState.WARMUP; private set
    private var elapsed = 0f
    private var startMs = 0L
    private var peak = 0f

    /** Background shaking level in g (RMS), what the console calls the noise floor. */
    val noiseFloorG: Float get() = sqrt(lta)

    fun ratio(): Float = if (lta <= 1e-12f) 0f else sta / lta

    fun reset() {
        sta = 0f; lta = 0f; elapsed = 0f; peak = 0f; state = DetectorState.WARMUP
    }

    /** Returns a finished [Trigger] when a burst ends, else null. */
    fun step(g: Float, dtSec: Float, nowMs: Long): Trigger? {
        val dt = dtSec.coerceIn(0.0005f, 0.1f)
        val e = g * g
        sta += (e - sta) * (dt / staSec).coerceAtMost(1f)
        if (state != DetectorState.SHAKE) {
            lta += (e - lta) * (dt / ltaSec).coerceAtMost(1f)
        }
        elapsed += dt
        when (state) {
            DetectorState.WARMUP -> if (elapsed >= warmupSec) state = DetectorState.QUIET
            DetectorState.QUIET -> {
                if (ratio() >= sensitivity.onRatio && sqrt(sta) >= minG) {
                    state = DetectorState.SHAKE
                    startMs = nowMs
                    peak = abs(g)
                }
            }
            DetectorState.SHAKE -> {
                peak = max(peak, abs(g))
                val long = nowMs - startMs > 90_000
                if (ratio() <= offRatio || long) {
                    state = DetectorState.QUIET
                    return Trigger(startMs, nowMs, peak, bump = peak >= bumpG)
                }
            }
        }
        return null
    }
}
