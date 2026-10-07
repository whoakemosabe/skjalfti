package app.skjalfti.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * A recording around one trigger: up to a minute of the phone's trace at [rateHz], starting at
 * [startMs] (wall clock). [z] is the vertical axis (signed), [sum] the combined shaking (≥ 0).
 */
class Clip(
    val startMs: Long,
    val rateHz: Int,
    val z: FloatArray,
    val sum: FloatArray,
    val triggerStartMs: Long,
    val triggerEndMs: Long,
    val peakG: Float,
) {
    val endMs: Long get() = startMs + z.size * 1000L / rateHz
    val durationMs: Long get() = endMs - startMs

    /** Sample index for a wall-clock time, clamped to the clip. */
    fun index(ms: Long): Int = ((ms - startMs).toDouble() * rateHz / 1000).toInt().coerceIn(0, (z.size - 1).coerceAtLeast(0))
}

/**
 * Trace clips on disk, one small binary file per trigger, named by the trigger's start time.
 * Keeps the newest [keep] clips.
 */
class Clips(private val dir: File, private val keep: Int = 80) {
    private val lock = Any()

    init { dir.mkdirs() }

    private fun file(triggerStartMs: Long) = File(dir, "$triggerStartMs.clip")

    fun save(c: Clip) = synchronized(lock) {
        val tmp = File(dir, "${c.triggerStartMs}.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(1)
            o.writeLong(c.startMs)
            o.writeInt(c.rateHz)
            o.writeLong(c.triggerStartMs)
            o.writeLong(c.triggerEndMs)
            o.writeFloat(c.peakG)
            o.writeInt(c.z.size)
            c.z.forEach { o.writeFloat(it) }
            c.sum.forEach { o.writeFloat(it) }
        }
        tmp.renameTo(file(c.triggerStartMs))
        prune()
    }

    fun load(triggerStartMs: Long): Clip? = synchronized(lock) {
        val f = file(triggerStartMs)
        if (!f.exists()) return null
        runCatching {
            DataInputStream(f.inputStream().buffered()).use { i ->
                if (i.readInt() != MAGIC) return null
                i.readInt()
                val start = i.readLong()
                val rate = i.readInt()
                val ts = i.readLong()
                val te = i.readLong()
                val peak = i.readFloat()
                val n = i.readInt()
                if (n !in 0..200_000 || rate !in 1..1000) return null
                val z = FloatArray(n) { i.readFloat() }
                val s = FloatArray(n) { i.readFloat() }
                Clip(start, rate, z, s, ts, te, peak)
            }
        }.getOrNull()
    }

    /** Trigger start times of saved clips, newest first. */
    fun list(): List<Long> = synchronized(lock) {
        (dir.listFiles() ?: emptyArray())
            .mapNotNull { it.name.removeSuffix(".clip").takeIf { n -> it.name.endsWith(".clip") }?.toLongOrNull() }
            .sortedDescending()
    }

    private fun prune() {
        list().drop(keep).forEach { file(it).delete() }
    }

    companion object {
        private const val MAGIC = 0x534B4A31 // "SKJ1"
    }
}
