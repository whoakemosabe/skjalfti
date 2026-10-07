package app.skjalfti.play

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.skjalfti.data.Clip
import app.skjalfti.data.Felt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** One playback: how long it lasts, and the slice of the clip it covers (for the playhead). */
data class Play(val durationMs: Long, val fromMs: Long = 0L, val toMs: Long = 0L)

/**
 * "Feel it" and "Hear it". Feel replays the shaking through the vibration motor in real time,
 * its strength following the trace. Hear speeds the trace up 40× into audio, the way
 * seismologists listen to quakes: a minute of ground motion becomes a second and a half of rumble.
 */
object Playback {
    private const val FRAME_MS = 40L
    private var track: AudioTrack? = null

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun stop(context: Context) {
        vibrator(context)?.cancel()
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    /** Builds a vibration envelope (amplitudes 0..255 per [FRAME_MS]) from a clip slice. */
    fun envelope(clip: Clip, fromMs: Long, toMs: Long): IntArray {
        val a = clip.index(fromMs)
        val b = max(a + 1, clip.index(toMs))
        val per = max(1, (FRAME_MS * clip.rateHz / 1000).toInt())
        val frames = (b - a + per - 1) / per
        val raw = FloatArray(frames)
        for (f in 0 until frames) {
            var p = 0f
            for (i in a + f * per until min(b, a + (f + 1) * per)) p = max(p, clip.sum[i])
            raw[f] = p
        }
        val peak = raw.maxOrNull()?.takeIf { it > 0f } ?: return IntArray(frames)
        // Compress the range so quiet shaking is still faintly felt and the peak hits hard.
        return IntArray(frames) { i ->
            val v = (raw[i] / peak).toDouble().pow(0.6)
            if (v < 0.08) 0 else (v * 255).toInt().coerceIn(1, 255)
        }
    }

    /** Synthetic shaking for a quake without a recording: a P jolt, then a bigger S rumble. */
    fun synthetic(magnitude: Double, hypocentreKm: Double): IntArray {
        val pS = hypocentreKm / Felt.P_KMS
        val sS = hypocentreKm / Felt.S_KMS
        val gap = ((sS - pS) * 1000 / FRAME_MS).toInt().coerceIn(3, 60)
        val strength = ((magnitude + 1) / 5.0).coerceIn(0.25, 1.0)
        val rumble = ((1.5 + magnitude) * 1000 / FRAME_MS).toInt().coerceIn(20, 140)
        val out = ArrayList<Int>()
        repeat(6) { out += (120 * strength).toInt() }
        repeat(gap) { out += 0 }
        val rnd = Random(7)
        for (i in 0 until rumble) {
            val decay = (1 - i.toDouble() / rumble).pow(1.6)
            val v = (255 * strength * decay * (0.6 + 0.4 * rnd.nextDouble())).toInt()
            out += if (v < 20) 0 else v
        }
        return out.toIntArray()
    }

    /** Vibrates an envelope. Returns its length in ms. */
    fun feel(context: Context, env: IntArray): Long {
        val vib = vibrator(context) ?: return 0
        if (!vib.hasVibrator() || env.isEmpty()) return 0
        stop(context)
        val timings = LongArray(env.size) { FRAME_MS }
        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, env, -1)
        } else {
            // On/off motor: buzz the strong frames, rest the rest.
            val onOff = IntArray(env.size) { if (env[it] > 110) 255 else 0 }
            VibrationEffect.createWaveform(timings, onOff, -1)
        }
        vib.vibrate(effect)
        return env.size * FRAME_MS
    }

    /** Plays the vertical trace sped up 40× as audio. Returns its length in ms. */
    fun hear(clip: Clip): Long {
        val rate = clip.rateHz * 40
        val src = clip.z
        if (src.isEmpty()) return 0
        val peak = src.maxOf { abs(it) }.takeIf { it > 0f } ?: return 0
        val n = src.size
        val pcm = ShortArray(n)
        val fade = min(n / 10, rate / 20)
        for (i in 0 until n) {
            var v = src[i] / peak
            // Soft clip so the loudest part doesn't crackle.
            v = (v * 1.4f) / (1f + abs(v * 1.4f))
            val edge = when {
                i < fade -> i.toFloat() / fade
                i > n - fade -> (n - i).toFloat() / fade
                else -> 1f
            }
            // A touch of grit so it sounds like rock, not a sine.
            val grit = 0.05f * sin(i * 1.7f) * abs(v)
            pcm[i] = ((v + grit) * edge * 0.9f * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        track?.let { runCatching { it.stop() }; it.release() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
        t.write(pcm, 0, n)
        t.play()
        track = t
        return n * 1000L / rate
    }
}
