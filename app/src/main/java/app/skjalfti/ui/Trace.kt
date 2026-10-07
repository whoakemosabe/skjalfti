package app.skjalfti.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.skjalfti.seismo.Channel
import app.skjalfti.seismo.Seismo
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A state that changes every display frame; read it in a draw lambda to redraw each frame. */
@Composable
fun rememberFrameTick(): () -> Long {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { tick = it } }
    return { tick }
}

private class Envelope {
    var lo = FloatArray(0)
    var hi = FloatArray(0)
    fun ensure(n: Int) {
        if (lo.size < n) { lo = FloatArray(n); hi = FloatArray(n) }
        for (i in 0 until n) { lo[i] = Float.POSITIVE_INFINITY; hi[i] = Float.NEGATIVE_INFINITY }
    }
}

/**
 * The live seismograph. Each frame it bins the last [windowSec] seconds of samples into columns
 * (min and max per column, like a real drum recorder's ink width) and scrolls them by the exact
 * time since the last column boundary, so motion is smooth at any refresh rate while the
 * columns themselves stay put in time and never shimmer.
 *
 * Pixel: columns are chunky blocks snapped to a pixel grid, with a phosphor glow and scanlines.
 * Glass: thin continuous strokes with a soft glow.
 */
@Composable
fun Trace(channel: Channel, gain: Int, color: Color, modifier: Modifier = Modifier, windowSec: Int = 30) {
    val look = LocalLook.current
    val pixel = look.pixel
    val tick = rememberFrameTick()
    val env = remember { Envelope() }
    val scan = remember {
        Brush.verticalGradient(
            0f to Color(0x4D000000), 0.34f to Color(0x4D000000), 0.35f to Color.Transparent, 1f to Color.Transparent,
            startY = 0f, endY = 9f, tileMode = TileMode.Repeated,
        )
    }
    Canvas(modifier) {
        if (tick() < 0L) return@Canvas
        val block = if (pixel) max(3f, 3.dp.toPx().roundToInt().toFloat()) else max(2f, 1.5.dp.toPx())
        val w = size.width
        val h = size.height
        val mid = if (pixel) (floor(h / 2 / block) * block) else h / 2
        grid(pixel, color, mid, block)

        val colsF = w / block
        val n = colsF.toInt() + 2
        env.ensure(n)
        val nsPerCol = (windowSec * 1_000_000_000L / colsF).toLong().coerceAtLeast(1L)
        // A short delay so the newest edge doesn't stutter as samples arrive in batches.
        val now = SystemClock.elapsedRealtimeNanos() - 60_000_000L
        val nowCol = now / nsPerCol
        val frac = (now % nsPerCol).toFloat() / nsPerCol

        val head = Seismo.head
        val pxPerG = (h / 2f) * gain / 0.05f
        if (head >= 0) {
            val a = Seismo.array(channel)
            val t = Seismo.tNs
            var i = head
            var k = 0
            val total = Seismo.count
            while (k < total) {
                val col = (nowCol - t[i] / nsPerCol).toInt()
                if (col >= n) break
                if (col >= 0) {
                    val v = a[i]
                    if (v < env.lo[col]) env.lo[col] = v
                    if (v > env.hi[col]) env.hi[col] = v
                }
                i = if (i == 0) Seismo.CAPACITY - 1 else i - 1
                k++
            }
        }
        // Join neighbouring columns so the line never breaks.
        var prev = -1
        for (c in 0 until n) {
            if (env.lo[c] > env.hi[c]) continue
            if (prev >= 0) {
                if (env.hi[c] < env.lo[prev]) env.hi[c] = env.lo[prev]
                if (env.lo[c] > env.hi[prev]) env.lo[c] = env.hi[prev]
            }
            prev = c
        }

        val glow = color.copy(alpha = if (pixel) 0.22f else 0.28f)
        for (pass in 0..1) {
            for (c in 0 until n) {
                val lo = env.lo[c]
                val hi = env.hi[c]
                if (lo > hi) continue
                val xr = w - (c + frac) * block
                val x = if (pixel) xr.roundToInt().toFloat() - block else xr - block
                var top = (mid - hi * pxPerG).coerceIn(0f, h)
                var bot = (mid - lo * pxPerG).coerceIn(0f, h)
                if (pixel) {
                    top = floor(top / block) * block
                    bot = max(top + block, ceil(bot / block) * block)
                    if (pass == 0) drawRect(glow, Offset(x, top - block), Size(block, bot - top + 2 * block))
                    else drawRect(color, Offset(x, top), Size(block, bot - top))
                } else {
                    if (bot - top < 1.5f) { top -= 0.75f; bot += 0.75f }
                    val cx = x + block / 2
                    if (pass == 0) drawLine(glow, Offset(cx, top - 3f), Offset(cx, bot + 3f), strokeWidth = block * 3, cap = StrokeCap.Round)
                    else drawLine(color, Offset(cx, top), Offset(cx, bot), strokeWidth = block * 1.1f, cap = StrokeCap.Round)
                }
            }
        }
        // Write head.
        val lastY = if (head >= 0) (mid - Seismo.array(channel)[head] * pxPerG).coerceIn(0f, h) else mid
        if (pixel) {
            drawRect(Color.White, Offset(w - block * 2, floor(lastY / block) * block), Size(block * 2, block * 2))
            drawRect(scan, size = size)
        } else {
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(w - 4.dp.toPx(), lastY))
        }
    }
}

private fun DrawScope.grid(pixel: Boolean, color: Color, mid: Float, block: Float) {
    val w = size.width
    val h = size.height
    if (pixel) {
        val faint = color.copy(alpha = 0.10f)
        listOf(h / 4, h * 3 / 4).forEach { y -> drawRect(faint, Offset(0f, floor(y / block) * block), Size(w, block * 0.67f)) }
        var x = 0f
        while (x < w) { drawRect(color.copy(alpha = 0.22f), Offset(x, mid), Size(block * 2, block * 0.67f)); x += block * 4 }
        listOf(w / 4, w / 2, w * 3 / 4).forEach { gx -> drawRect(color.copy(alpha = 0.06f), Offset(floor(gx / block) * block, 0f), Size(block * 0.67f, h)) }
    } else {
        val line = Color.White.copy(alpha = 0.07f)
        drawLine(line, Offset(0f, h / 4), Offset(w, h / 4), 1f)
        drawLine(line, Offset(0f, h * 3 / 4), Offset(w, h * 3 / 4), 1f)
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, mid), Offset(w, mid), 1f)
    }
}

/** LED-style level meters for X, Y and Z, on a log scale from the noise floor up to a bump. */
@Composable
fun AxisMeters(modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val c = look.c
    val tick = rememberFrameTick()
    Canvas(modifier) {
        if (tick() < 0L) return@Canvas
        val segs = 7
        val gap = 3.dp.toPx()
        val colW = (size.width - gap * 2) / 3
        val segH = (size.height - gap * (segs - 1)) / segs
        listOf(Channel.X, Channel.Y, Channel.Z).forEachIndexed { idx, ch ->
            val v = Seismo.peak(ch, 400)
            // 0.0005 g (noise) → 0, 0.1 g → full.
            val f = (ln(max(v, 0.0005f) / 0.0005f) / ln(200f)).coerceIn(0f, 1f)
            val lit = (f * segs).roundToInt()
            val x = idx * (colW + gap)
            for (s in 0 until segs) {
                val y = size.height - (s + 1) * segH - s * gap
                val on = s < lit
                val col = when {
                    !on -> c.off
                    s >= segs - 1 -> c.hot
                    s >= segs - 2 -> c.amber
                    else -> c.phosphor
                }
                if (look.pixel) drawRect(col, Offset(x, y), Size(colW, segH))
                else drawRoundRect(col, Offset(x, y), Size(colW, segH), androidx.compose.ui.geometry.CornerRadius(segH / 2))
            }
        }
    }
}

/** A compressed trace (one value per minute) for the night report. */
@Composable
fun Strip(values: List<Float>, color: Color, highlight: Color, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        if (values.isEmpty()) {
            drawLine(color.copy(alpha = 0.5f), Offset(0f, mid), Offset(w, mid), 2.dp.toPx())
            return@Canvas
        }
        val peak = max(values.max(), 0.002f)
        val step = w / values.size
        val bi = values.indices.maxByOrNull { values[it] } ?: 0
        drawRect(highlight.copy(alpha = 0.15f), Offset(max(0f, bi * step - 10.dp.toPx()), 0f), Size(20.dp.toPx() + step, h))
        if (look.pixel) {
            val b = 2.dp.toPx()
            values.forEachIndexed { i, v ->
                val a = (v / peak) * (h / 2 - b)
                val top = floor((mid - a) / b) * b
                val bot = max(top + b, ceil((mid + a) / b) * b)
                drawRect(color, Offset(i * step, top), Size(max(b, step), bot - top))
            }
        } else {
            val p = Path()
            values.forEachIndexed { i, v ->
                val y = mid - (v / peak) * (h / 2 - 4f) * (if (i % 2 == 0) 1f else -1f)
                if (i == 0) p.moveTo(0f, y) else p.lineTo(i * step, y)
            }
            drawPath(p, color, style = Stroke(1.6.dp.toPx()))
        }
    }
}

/** Events per hour, oldest on the left. Tall bars run amber then hot. */
@Composable
fun HourBars(counts: List<Int>, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val c = look.c
    Canvas(modifier) {
        val n = counts.size.coerceAtLeast(1)
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val top = max(4, counts.maxOrNull() ?: 0)
        counts.forEachIndexed { i, v ->
            val hgt = if (v == 0) 2.dp.toPx() else max(4.dp.toPx(), size.height * v / top)
            val col = when {
                v == 0 -> c.off
                v >= top * 0.75f -> c.hot
                v >= top * 0.5f -> c.amber
                else -> c.phosphor
            }
            val x = i * (bw + gap)
            if (look.pixel) {
                val b = 3.dp.toPx()
                val hh = max(b, floor(hgt / b) * b)
                drawRect(col, Offset(x, size.height - hh), Size(bw, hh))
            } else {
                drawRoundRect(col, Offset(x, size.height - hgt), Size(bw, hgt), androidx.compose.ui.geometry.CornerRadius(min(bw, hgt) / 2))
            }
        }
    }
}
