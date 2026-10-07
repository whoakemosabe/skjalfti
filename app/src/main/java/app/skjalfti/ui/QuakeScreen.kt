package app.skjalfti.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.data.Clip
import app.skjalfti.data.Felt
import app.skjalfti.data.FeltStatus
import app.skjalfti.data.Geo
import app.skjalfti.play.Play
import app.skjalfti.play.Playback
import app.skjalfti.share.ShareCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm:ss", Locale.ENGLISH)

@Composable
fun QuakeScreen(id: String) {
    val look = LocalLook.current
    val c = look.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quakes by Engine.quakes.collectAsState()
    val home by Engine.home.collectAsState()
    val skin by Engine.skin.collectAsState()
    val row = quakes.rows.firstOrNull { it.quake.id == id }
    val playhead = remember { Animatable(0f) }
    val clip = remember(row?.match?.triggerStartMs) { row?.let { Engine.clipFor(it) } }

    // Opened from a notification before the log has this quake: fetch it.
    LaunchedEffect(id) { if (row == null) Engine.refresh() }
    DisposableEffect(Unit) { onDispose { Playback.stop(context) } }

    fun animate(play: Play) {
        if (play.durationMs <= 0) return
        scope.launch {
            playhead.snapTo(0f)
            playhead.animateTo(1f, tween(play.durationMs.toInt(), easing = LinearEasing))
            playhead.snapTo(0f)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(if (look.pixel) "Quake.dat" else "Quake") {
            KButton("Back", { Nav.back() }, height = 36.dp)
        }
        if (row == null) {
            Panel(Modifier.fillMaxWidth()) {
                Txt(if (quakes.loading) "Loading…" else "This quake is no longer in the last 24 hours.", look.t.body, c.muted, maxLines = 3)
            }
            return@Column
        }
        val q = row.quake

        // Headline
        Panel(Modifier.fillMaxWidth(), frame = if (look.pixel) magColor(q.magnitude, c) else null, fill = if (look.pixel) c.bevelLo else null, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(Fmt.mag(q.magnitude), look.t.number.copy(fontSize = look.t.number.fontSize * 2.4f), magColor(q.magnitude, c))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(q.region, look.t.title, c.ink, maxLines = 2)
                    Txt(dayFmt.format(Instant.ofEpochMilli(q.timeMs).atZone(ZoneId.systemDefault())), look.t.small, c.muted)
                    Txt("${Fmt.km(row.distKm)} from ${home.label} · depth ${Fmt.mag(q.depthKm)} km", look.t.small, c.muted, maxLines = 2)
                    if (!q.reviewed) Txt("Automatic, not yet reviewed", look.t.label, c.faint)
                }
            }
        }

        // Where
        Panel(Modifier.fillMaxWidth(), raised = false, padding = if (look.pixel) 6.dp else 0.dp) {
            Box(Modifier.fillMaxWidth().height(200.dp).then(if (look.pixel) Modifier else Modifier.clip(RoundedCornerShape(26.dp)))) {
                QuakeMap(
                    rows = listOf(row), home = home,
                    halfWidthKm = max(12.0, row.distKm * 0.75),
                    centerLat = (q.lat + home.lat) / 2, centerLon = (q.lon + home.lon) / 2,
                    highlightId = q.id,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // What the phone saw
        val hyp = Geo.hypocentreKm(q, home)
        val pAt = q.timeMs + (hyp / Felt.P_KMS * 1000).toLong()
        val sAt = q.timeMs + (hyp / Felt.S_KMS * 1000).toLong()
        Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
            Txt("Your phone", look.t.label, c.muted)
            Txt(
                when (row.match.status) {
                    FeltStatus.FELT -> "Felt it ${String.format(Locale.ROOT, "%.1f", row.match.delaySec ?: 0.0)} s after the quake · peak ${Fmt.g(row.match.peakG ?: 0f, look.pixel)}"
                    else -> feltLine(row.match.status)
                },
                look.t.body, if (row.match.status == FeltStatus.FELT) c.phosphor else c.ink, maxLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            if (clip != null) {
                ClipTrace(clip, pAt, sAt, { playhead.value }, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(6.dp))
                Row {
                    Txt("P wave ${Fmt.hhmm(pAt)}:${String.format(Locale.ROOT, "%02d", (pAt / 1000) % 60)}", look.t.label, c.amber, Modifier.weight(1f))
                    Txt("S wave +${String.format(Locale.ROOT, "%.1f", (sAt - pAt) / 1000.0)} s", look.t.label, c.amber)
                }
            } else {
                Txt(
                    when (row.match.status) {
                        FeltStatus.FELT -> "Felt before trace clips existed, so there's no recording."
                        FeltStatus.MISS -> "Recording, but nothing rose above the background. P and S waves would have arrived ${String.format(Locale.ROOT, "%.1f", (pAt - q.timeMs) / 1000.0)} s and ${String.format(Locale.ROOT, "%.1f", (sAt - q.timeMs) / 1000.0)} s after the quake."
                        else -> "No recording for this one. Feel it plays a simulation from the magnitude and distance."
                    },
                    look.t.small, c.muted, maxLines = 4,
                )
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("Feel it", {
                val env = if (clip != null) Playback.envelope(clip, clip.triggerStartMs - 2000, clip.endMs)
                else Playback.synthetic(q.magnitude, hyp)
                val ms = Playback.feel(context, env)
                if (clip != null) animate(Play(ms))
            }, Modifier.weight(1f), selected = true, accent = c.amber)
            KButton("Hear it", {
                if (clip != null) animate(Play(Playback.hear(clip)))
            }, Modifier.weight(1f), selected = clip != null)
            KButton("Share", {
                val bmp = ShareCard.quake(context, skin, row, home, clip)
                ShareCard.share(context, bmp, "skjalfti-${q.id}")
            }, Modifier.weight(1f))
        }
        if (clip == null) Txt("Hear it needs a recording.", look.t.label, c.faint)
    }
}

/**
 * A saved clip drawn like the live trace, with the predicted P and S arrivals as amber lines and
 * the detector's trigger shaded. [playhead] (0..1) sweeps across during playback.
 */
@Composable
fun ClipTrace(clip: Clip, pAt: Long, sAt: Long, playhead: () -> Float, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val c = look.c
    val measurer = rememberTextMeasurer()
    Canvas(modifier.then(if (look.pixel) Modifier.background(c.screen) else Modifier)) {
        val w = size.width
        val h = size.height
        val total = clip.durationMs.toFloat().coerceAtLeast(1f)
        fun xAt(ms: Long) = ((ms - clip.startMs) / total) * w

        // Trigger shading
        val tx0 = xAt(clip.triggerStartMs).coerceIn(0f, w)
        val tx1 = xAt(clip.triggerEndMs).coerceIn(0f, w)
        drawRect(c.phosphor.copy(alpha = 0.08f), Offset(tx0, 0f), Size(max(2f, tx1 - tx0), h))

        val block = if (look.pixel) 3.dp.toPx().coerceAtLeast(3f) else 2f
        val cols = (w / block).toInt().coerceAtLeast(1)
        val peak = max(clip.z.maxOfOrNull { abs(it) } ?: 0f, 1e-5f)
        val mid = h / 2
        val half = h / 2 - 6f
        for (i in 0 until cols) {
            val a = (i.toLong() * clip.z.size / cols).toInt()
            val b = max(a + 1, ((i + 1).toLong() * clip.z.size / cols).toInt()).coerceAtMost(clip.z.size)
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (k in a until b) { lo = minOf(lo, clip.z[k]); hi = maxOf(hi, clip.z[k]) }
            if (lo > hi) continue
            var top = mid - hi / peak * half
            var bot = mid - lo / peak * half
            val x = i * block
            if (look.pixel) {
                top = floor(top / block) * block
                bot = max(top + block, ceil(bot / block) * block)
                drawRect(c.phosphor, Offset(x, top), Size(block, bot - top))
            } else {
                drawLine(c.phosphor, Offset(x + 1, top), Offset(x + 1, max(bot, top + 1.5f)), strokeWidth = block, cap = StrokeCap.Round)
            }
        }
        listOf(pAt to "P", sAt to "S").forEach { (t, label) ->
            val x = xAt(t)
            if (x in 0f..w) {
                drawRect(c.amber.copy(alpha = 0.8f), Offset(x - 1f, 0f), Size(2f, h))
                drawText(measurer, label, Offset(x + 4f, 2f), androidx.compose.ui.text.TextStyle(color = c.amber, fontSize = 11.sp, fontFamily = if (look.pixel) Fonts.departure else Fonts.geistMono))
            }
        }
        val ph = playhead()
        if (ph > 0f) {
            val x = xAt(clip.triggerStartMs - 2000).coerceAtLeast(0f) + ph * (w - xAt(clip.triggerStartMs - 2000).coerceAtLeast(0f))
            drawRect(Color.White, Offset(x - 1f, 0f), Size(2f, h))
        }
        if (look.pixel) {
            var y = 0f
            while (y < h) { drawRect(Color(0x33000000), Offset(0f, y), Size(w, 1f)); y += 3f }
        }
    }
}
