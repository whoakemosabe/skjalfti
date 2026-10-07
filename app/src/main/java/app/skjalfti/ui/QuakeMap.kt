package app.skjalfti.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skjalfti.QuakeRow
import app.skjalfti.data.Coast
import app.skjalfti.data.FeltStatus
import app.skjalfti.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Equirectangular projection around a centre, in km. Fine at this scale. */
private class Proj(val cLat: Double, val cLon: Double, val kmPerPx: Double, val w: Float, val h: Float) {
    private val kx = 111.32 * cos(Math.toRadians(cLat))
    private val ky = 110.57
    fun x(lon: Double) = (w / 2 + (lon - cLon) * kx / kmPerPx).toFloat()
    fun y(lat: Double) = (h / 2 - (lat - cLat) * ky / kmPerPx).toFloat()
    fun lon(x: Float) = cLon + (x - w / 2) * kmPerPx / kx
    fun lat(y: Float) = cLat - (y - h / 2) * kmPerPx / ky
}

/**
 * The quake map: the real coastline of south-west Iceland (pixel-rasterised in the console,
 * a smooth outline on glass), distance rings around home, and every quake in the log sized by
 * magnitude and fading with age. Tap a quake to open it.
 */
@Composable
fun QuakeMap(
    rows: List<QuakeRow>,
    home: Place,
    halfWidthKm: Double,
    modifier: Modifier = Modifier,
    centerLat: Double = home.lat,
    centerLon: Double = home.lon,
    highlightId: String? = null,
    onTap: ((QuakeRow) -> Unit)? = null,
) {
    val look = LocalLook.current
    val c = look.c
    val density = LocalDensity.current
    val cell = with(density) { 4.dp.toPx() }.roundToInt().coerceAtLeast(4).toFloat()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )

    // Land cells for the pixel map, computed off the main thread whenever the view changes.
    val land by produceState<BooleanArray?>(null, size, centerLat, centerLon, halfWidthKm, look.pixel) {
        value = null
        if (!look.pixel || size.width == 0) return@produceState
        value = withContext(Dispatchers.Default) {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val p = Proj(centerLat, centerLon, 2 * halfWidthKm / w, w, h)
            val cols = (w / cell).toInt() + 1
            val rowsN = (h / cell).toInt() + 1
            BooleanArray(cols * rowsN) { i ->
                val cx = (i % cols) * cell + cell / 2
                val cy = (i / cols) * cell + cell / 2
                Coast.isLand(p.lon(cx).toFloat(), p.lat(cy).toFloat())
            }
        }
    }

    val tap = if (onTap == null) Modifier else Modifier.pointerInput(rows, size, centerLat, centerLon, halfWidthKm) {
        detectTapGestures { pos ->
            if (size.width == 0) return@detectTapGestures
            val p = Proj(centerLat, centerLon, 2 * halfWidthKm / size.width, size.width.toFloat(), size.height.toFloat())
            val hit = rows.minByOrNull { hypot(p.x(it.quake.lon) - pos.x, p.y(it.quake.lat) - pos.y) }
            if (hit != null && hypot(p.x(hit.quake.lon) - pos.x, p.y(hit.quake.lat) - pos.y) < 32.dp.toPx()) onTap(hit)
        }
    }

    Canvas(modifier.onSizeChanged { size = it }.then(tap)) {
        val w = this.size.width
        val h = this.size.height
        if (w <= 0f) return@Canvas
        val p = Proj(centerLat, centerLon, 2 * halfWidthKm / w, w, h)
        val now = System.currentTimeMillis()

        if (look.pixel) {
            drawRect(Color(0xFF080812))
            val g = land
            if (g != null) {
                val cols = (w / cell).toInt() + 1
                val rowsN = g.size / cols
                for (i in g.indices) {
                    if (!g[i]) continue
                    val cx = i % cols
                    val cy = i / cols
                    val coast = (cx > 0 && !g[i - 1]) || (cx < cols - 1 && !g[i + 1]) ||
                        (cy > 0 && !g[i - cols]) || (cy < rowsN - 1 && !g[i + cols])
                    drawRect(if (coast) c.phosphor.copy(alpha = 0.55f) else c.panel, Offset(cx * cell, cy * cell), Size(cell, cell))
                }
            }
        } else {
            Coast.rings.forEach { r ->
                val path = Path()
                var i = 0
                while (i < r.size) {
                    val x = p.x(r[i].toDouble())
                    val y = p.y(r[i + 1].toDouble())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    i += 2
                }
                path.close()
                drawPath(path, Color(0x1AFFFFFF))
                drawPath(path, Color(0x4DFFFFFF), style = Stroke(1.2.dp.toPx()))
            }
        }

        // Distance rings around home.
        val hx = p.x(home.lon)
        val hy = p.y(home.lat)
        val step = when { halfWidthKm <= 35 -> 10.0; halfWidthKm <= 70 -> 20.0; else -> 30.0 }
        var km = step
        while (km <= halfWidthKm * 1.5) {
            val r = (km / p.kmPerPx).toFloat()
            if (look.pixel) {
                val n = (2 * PI * r / (cell * 2)).toInt().coerceAtLeast(12)
                for (k in 0 until n) {
                    val a = 2 * PI * k / n
                    val x = floor((hx + r * cos(a).toFloat()) / cell) * cell
                    val y = floor((hy + r * sin(a).toFloat()) / cell) * cell
                    drawRect(c.faint.copy(alpha = 0.6f), Offset(x, y), Size(cell * 0.75f, cell * 0.75f))
                }
            } else {
                drawCircle(Color(0x33FFFFFF), r, Offset(hx, hy), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            }
            label(measurer, look.say("${km.toInt()} km"), hx + 4f, hy - r - 16.dp.toPx(), c.faint, look.pixel)
            km += step
        }

        // Quakes, oldest first so the newest sit on top.
        rows.sortedBy { it.quake.timeMs }.forEach { row ->
            val q = row.quake
            val x = p.x(q.lon)
            val y = p.y(q.lat)
            val age = ((now - q.timeMs) / (24 * 3600_000.0)).coerceIn(0.0, 1.0).toFloat()
            val col = magColor(q.magnitude, c).copy(alpha = 1f - age * 0.65f)
            val felt = row.match.status == FeltStatus.FELT
            if (look.pixel) {
                val n = (1 + q.magnitude.coerceAtLeast(0.0)).roundToInt().coerceIn(1, 5)
                val s = n * cell
                val x0 = floor((x - s / 2) / cell) * cell
                val y0 = floor((y - s / 2) / cell) * cell
                if (felt) drawRect(Color.White, Offset(x0 - cell / 2, y0 - cell / 2), Size(s + cell, s + cell))
                drawRect(col, Offset(x0, y0), Size(s, s))
            } else {
                val r = (3 + 3 * q.magnitude.coerceAtLeast(0.0)).dp.toPx()
                drawCircle(col.copy(alpha = col.alpha * 0.3f), r * 1.8f, Offset(x, y))
                drawCircle(col, r, Offset(x, y))
                if (felt) drawCircle(Color.White, r + 2.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
            }
            if (q.id == highlightId) {
                val pr = (14 + 26 * pulse).dp.toPx()
                drawCircle(c.amber.copy(alpha = 1f - pulse), pr, Offset(x, y), style = Stroke(2.dp.toPx()))
            }
        }

        // You.
        if (look.pixel) {
            val on = pulse < 0.6f
            val s = cell * 2
            drawRect(if (on) Color.White else c.faint, Offset(floor((hx - s / 2) / cell) * cell, floor((hy - s / 2) / cell) * cell), Size(s, s))
        } else {
            drawCircle(Color.White.copy(alpha = 0.25f * (1 - pulse)), (8 + 16 * pulse).dp.toPx(), Offset(hx, hy))
            drawCircle(Color.White, 5.dp.toPx(), Offset(hx, hy))
        }
        label(measurer, look.say("You"), hx + 10.dp.toPx(), hy - 6.dp.toPx(), Color.White, look.pixel)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(
    m: TextMeasurer, s: String, x: Float, y: Float, color: Color, pixel: Boolean,
) {
    if (x < 0 || y < 0 || x > size.width - 20 || y > size.height - 10) return
    drawText(
        m, s, Offset(x, y),
        style = androidx.compose.ui.text.TextStyle(
            color = color,
            fontSize = 10.sp,
            fontFamily = if (pixel) Fonts.departure else Fonts.geistMono,
        ),
    )
}
