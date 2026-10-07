package app.skjalfti.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.skjalfti.Fmt
import app.skjalfti.QuakeRow
import app.skjalfti.R
import app.skjalfti.Skin
import app.skjalfti.data.Clip
import app.skjalfti.data.FeltStatus
import app.skjalfti.data.NightReport
import app.skjalfti.data.Place
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Shareable PNG cards (1080×1350, a portrait post) for a quake or a night report, drawn with
 * plain Android graphics in the current skin: pixel console or liquid glass.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    private class Style(private val ctx: Context, val skin: Skin) {
        val pixel = skin == Skin.PIXEL
        private fun font(id: Int) = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: Typeface.MONOSPACE
        val ui: Typeface = if (pixel) font(R.font.departure_mono) else font(R.font.geist_regular)
        val num: Typeface = if (pixel) font(R.font.doto_black) else font(R.font.geist_light)
        val mark: Typeface = if (pixel) font(R.font.martian_mono_extrabold) else font(R.font.geist_medium)
        val bg = if (pixel) 0xFF0B0B14.toInt() else 0xFF06080B.toInt()
        val panel = if (pixel) 0xFF1C1C33.toInt() else 0x1AFFFFFF
        val hi = 0xFF4A4A78.toInt()
        val lo = 0xFF05050A.toInt()
        val ink = Color.WHITE
        val muted = if (pixel) 0xFFA9A9D0.toInt() else 0xFFA7ADB8.toInt()
        val accent = if (pixel) 0xFF39FF7A.toInt() else 0xFFFF6A2B.toInt()
        val amber = if (pixel) 0xFFFFB000.toInt() else 0xFFFFB38A.toInt()
        val magenta = if (pixel) 0xFFFF4FD8.toInt() else 0xFF9B7BFF.toInt()
        fun say(s: String) = if (pixel) s.uppercase() else s
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface) {
        c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf })
    }

    private fun background(c: Canvas, st: Style) {
        c.drawColor(st.bg)
        if (st.pixel) {
            // Faint dot grid, like the console's casing.
            val p = Paint().apply { color = 0x14FFFFFF }
            var y = 12f
            while (y < H) { var x = 12f; while (x < W) { c.drawRect(x, y, x + 3, y + 3, p); x += 24f }; y += 24f }
        } else {
            fun blob(color: Int, cx: Float, cy: Float, r: Float) {
                c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                })
            }
            blob(0x99FF6A2B.toInt(), W * 0.15f, H * 0.28f, W * 0.9f)
            blob(0x772A6CFF, W * 1.0f, H * 0.7f, W * 0.8f)
            blob(0x559B3BFF, W * 0.45f, H * 1.05f, W * 0.6f)
        }
    }

    private fun panel(c: Canvas, st: Style, r: RectF, frame: Int? = null) {
        if (st.pixel) {
            val s = 9f
            c.drawRect(r, Paint().apply { color = if (frame != null) st.lo else st.panel })
            val a = Paint().apply { color = frame ?: st.hi }
            val b = Paint().apply { color = frame ?: st.lo }
            c.drawRect(r.left, r.top, r.right, r.top + s, a)
            c.drawRect(r.left, r.top, r.left + s, r.bottom, a)
            c.drawRect(r.left, r.bottom - s, r.right, r.bottom, b)
            c.drawRect(r.right - s, r.top, r.right, r.bottom, b)
        } else {
            val rr = 56f
            c.drawRoundRect(r, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF })
            c.drawRoundRect(r, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF; style = Paint.Style.STROKE; strokeWidth = 2.5f })
        }
    }

    private fun header(c: Canvas, st: Style) {
        text(c, st.say("Skjálfti"), 72f, 150f, if (st.pixel) 64f else 84f, st.ink, st.mark)
        if (st.pixel) c.drawRect(W - 132f, 100f, W - 72f, 160f, Paint().apply { color = st.accent })
    }

    private fun footer(c: Canvas, st: Style, line: String) {
        text(c, st.say(line), 72f, H - 72f, 30f, st.muted, st.ui)
    }

    private fun trace(c: Canvas, st: Style, r: RectF, values: FloatArray, signed: Boolean, markers: List<Pair<Float, String>> = emptyList()) {
        if (values.isEmpty()) return
        val peak = max(values.maxOf { abs(it) }, 1e-6f)
        val cols = if (st.pixel) ((r.width()) / 12f).toInt() else r.width().toInt() / 3
        val colW = r.width() / cols
        val mid = if (signed) r.centerY() else r.bottom - 12f
        val half = if (signed) r.height() / 2 - 12f else r.height() - 24f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.accent; alpha = 70
            if (!st.pixel) maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        markers.forEach { (f, label) ->
            val x = r.left + f * r.width()
            c.drawRect(x - 2f, r.top, x + 2f, r.bottom, Paint().apply { color = st.amber; alpha = 160 })
            text(c, label, x + 10f, r.top + 40f, 30f, st.amber, st.ui)
        }
        for (i in 0 until cols) {
            val a = (i.toLong() * values.size / cols).toInt()
            val b = max(a + 1, ((i + 1).toLong() * values.size / cols).toInt())
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (k in a until minOf(b, values.size)) { lo = minOf(lo, values[k]); hi = maxOf(hi, values[k]) }
            var top = mid - hi / peak * half
            var bot = mid - (if (signed) lo else 0f) / peak * half
            val x = r.left + i * colW
            if (st.pixel) {
                top = floor(top / 12f) * 12f
                bot = max(top + 12f, floor(bot / 12f) * 12f + 12f)
                c.drawRect(x, top - 12f, x + colW, bot + 12f, glow)
                c.drawRect(x, top, x + colW, bot, p)
            } else {
                c.drawRect(x, top - 4f, x + colW, bot + 4f, glow)
                c.drawRect(x, top, x + colW - 0.5f, max(bot, top + 3f), p)
            }
        }
        if (st.pixel) {
            val scan = Paint().apply { color = 0x30000000 }
            var y = r.top
            while (y < r.bottom) { c.drawRect(r.left, y, r.right, y + 3f, scan); y += 9f }
        }
    }

    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)
    private fun day(ms: Long) = dayFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun quake(context: Context, skin: Skin, row: QuakeRow, home: Place, clip: Clip?): Bitmap {
        val st = Style(context, skin)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, st)
        header(c, st)
        val q = row.quake
        panel(c, st, RectF(72f, 220f, W - 72f, 640f), if (st.pixel) st.amber else null)
        text(c, st.say("Earthquake"), 120f, 300f, 36f, st.amber, st.ui)
        text(c, Fmt.mag(q.magnitude), 110f, 540f, 260f, st.ink, st.num)
        text(c, st.say(q.region), 520f, 430f, 50f, st.ink, st.ui)
        text(c, st.say(day(q.timeMs)), 520f, 490f, 34f, st.muted, st.ui)
        text(c, st.say("${Fmt.km(row.distKm)} from ${home.label}"), 520f, 545f, 34f, st.muted, st.ui)
        text(c, st.say("Depth ${Fmt.mag(q.depthKm)} km"), 520f, 600f, 34f, st.muted, st.ui)

        val felt = row.match.status == FeltStatus.FELT
        panel(c, st, RectF(72f, 690f, W - 72f, 1180f))
        text(c, st.say(if (felt) "My phone felt it" else "My phone's seismograph"), 120f, 770f, 40f, if (felt) st.accent else st.muted, st.ui)
        val tr = RectF(120f, 810f, W - 120f, 1130f)
        if (clip != null) {
            val total = clip.durationMs.toFloat()
            val pAt = ((row.match.triggerStartMs ?: clip.triggerStartMs) - clip.startMs) / total
            trace(c, st, tr, clip.z, signed = true, markers = listOf(pAt.coerceIn(0f, 1f) to st.say("Felt")))
        } else {
            text(c, st.say(if (felt) "Felt, no trace saved" else "Not felt here"), 120f, 980f, 44f, st.muted, st.ui)
        }
        footer(c, st, "Skjálfti · data: Veðurstofa Íslands")
        return bmp
    }

    fun night(context: Context, skin: Skin, r: NightReport): Bitmap {
        val st = Style(context, skin)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, st)
        header(c, st)
        panel(c, st, RectF(72f, 220f, W - 72f, 700f), if (st.pixel) st.magenta else null)
        text(c, st.say("While I slept · ${Fmt.hhmm(r.startMs)}–${Fmt.hhmm(r.endMs)}"), 120f, 300f, 36f, st.magenta, st.ui)
        text(c, "${r.quakes}", 110f, 560f, 280f, st.ink, st.num)
        text(c, st.say(if (r.quakes == 1) "quake" else "quakes"), 120f + 170f * "${r.quakes}".length, 560f, 64f, st.ink, st.ui)
        text(c, st.say(if (r.felt == 0) "My phone felt none" else "My phone felt ${r.felt}"), 120f, 640f, 44f, st.accent, st.ui)
        panel(c, st, RectF(72f, 750f, W - 72f, 1180f))
        text(c, st.say("Overnight trace"), 120f, 830f, 36f, st.muted, st.ui)
        trace(c, st, RectF(120f, 860f, W - 120f, 1040f), r.strip.toFloatArray(), signed = false)
        r.strongestMag?.let {
            text(c, st.say("Strongest M${Fmt.mag(it)} ${r.strongestRegion ?: ""}"), 120f, 1120f, 40f, st.ink, st.ui)
        }
        footer(c, st, "Skjálfti · data: Veðurstofa Íslands")
        return bmp
    }

    /** Saves [bmp] to the share cache and opens the share sheet. */
    fun share(context: Context, bmp: Bitmap, name: String) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
