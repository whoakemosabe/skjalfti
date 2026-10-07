package app.skjalfti.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.seismo.DetectorState
import app.skjalfti.seismo.Seismo
import app.skjalfti.seismo.Sensitivity
import app.skjalfti.seismo.Trigger
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

/** How still a spot is, from the background shaking (RMS, in g). */
private fun rating(noiseG: Float): Pair<String, Int> = when {
    noiseG <= 0f -> "Warming up" to 0
    noiseG < 0.0008f -> "Excellent" to 4
    noiseG < 0.0015f -> "Good" to 3
    noiseG < 0.004f -> "Fair" to 2
    else -> "Too busy" to 1
}

private fun mg(g: Float) = String.format(Locale.ROOT, "%.2f mg", g * 1000)

/**
 * Two tools in one. Quiet spot: the background shaking right now, live, with the quietest
 * reading so far, so you can carry the phone around and find the best place for night watch.
 * Shake test: tap the table near the phone and watch the detector fire.
 */
@Composable
fun TestScreen() {
    val look = LocalLook.current
    val c = look.c
    val sens by Engine.sensitivity.collectAsState()
    var noise by remember { mutableFloatStateOf(0f) }
    var best by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var state by remember { mutableStateOf(DetectorState.WARMUP) }
    var fired by remember { mutableIntStateOf(0) }
    var lastHit by remember { mutableStateOf<Trigger?>(null) }
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        val seen = Seismo.lastTrigger
        var known = seen
        var tick = 0
        while (true) {
            state = Seismo.detector.state
            val n = if (state == DetectorState.WARMUP) 0f else Seismo.detector.noiseFloorG
            noise = n
            if (n > 0f && state == DetectorState.QUIET) best = minOf(best, n)
            val t = Seismo.lastTrigger
            if (t != null && t !== known) { known = t; fired++; lastHit = t }
            if (tick % 2 == 0) {
                history.add(n)
                if (history.size > 120) history.removeAt(0)
            }
            tick++
            delay(250)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(if (look.pixel) "Test.exe" else "Test") {
            BackKey()
        }

        // Quiet spot
        val (word, level) = rating(noise)
        Panel(Modifier.fillMaxWidth(), frame = if (look.pixel) (if (level >= 3) c.phosphor else if (level == 2) c.amber else c.hot) else null, fill = if (look.pixel) c.bevelLo else null, padding = 16.dp) {
            Txt("Quiet spot finder", look.t.label, c.muted)
            Txt(word, look.t.number.copy(fontSize = look.t.number.fontSize * 1.3f), when (level) { 4, 3 -> c.phosphor; 2 -> c.amber; 0 -> c.faint; else -> c.hot })
            Txt("Background ${mg(noise)}", look.t.body, c.ink)
            if (best != Float.MAX_VALUE) Txt("Quietest so far ${mg(best)}", look.t.small, c.muted)
            Spacer(Modifier.height(10.dp))
            NoiseHistory(history, Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(8.dp))
            Txt(
                "Lay the phone flat and leave it for ten seconds per spot. Hard floors and concrete beat tables; away from the fridge, washing machine and footsteps.",
                look.t.small, c.muted, maxLines = 5,
            )
        }

        // Shake test
        val shaking = state == DetectorState.SHAKE
        Panel(Modifier.fillMaxWidth(), frame = if (look.pixel && shaking) c.amber else null, fill = if (look.pixel && shaking) c.bevelLo else null, padding = 16.dp) {
            Txt("Shake test", look.t.label, c.muted)
            Txt(
                if (shaking) "Trigger!" else if (fired == 0) "Waiting for a tap" else "Fired $fired×",
                look.t.title, if (shaking) c.amber else c.ink,
            )
            lastHit?.let {
                Txt(
                    "Last: peak ${Fmt.g(it.peakG, look.pixel)}, ${String.format(Locale.ROOT, "%.1f", (it.endMs - it.startMs) / 1000.0)} s${if (it.bump) " (counted as a bump)" else ""}",
                    look.t.small, c.muted, maxLines = 2,
                )
            }
            Spacer(Modifier.height(10.dp))
            AxisMeters(Modifier.fillMaxWidth().height(48.dp))
            Spacer(Modifier.height(10.dp))
            Txt("Sensitivity", look.t.label, c.muted)
            Spacer(Modifier.height(6.dp))
            Choice(Sensitivity.entries, sens, { it.label.lowercase().replaceFirstChar { ch -> ch.uppercase() } }, { Engine.setSensitivity(it) })
            Spacer(Modifier.height(8.dp))
            Txt(
                "Tap the table a hand's width from the phone. On High a light tap should fire, on Med a knock, on Low a thump. Anything over 0.15 g counts as a bump and never makes a quake FELT.",
                look.t.small, c.muted, maxLines = 5,
            )
        }
    }
}

@Composable
private fun NoiseHistory(values: List<Float>, modifier: Modifier) {
    val look = LocalLook.current
    val c = look.c
    Canvas(modifier) {
        val n = 120
        val bw = size.width / n
        // Log scale from 0.2 mg to 20 mg.
        fun f(v: Float) = (ln(max(v, 0.0002f) / 0.0002f) / ln(100f)).coerceIn(0f, 1f)
        values.forEachIndexed { i, v ->
            if (v <= 0f) return@forEachIndexed
            var hgt = f(v) * size.height
            val col = when { v < 0.0015f -> c.phosphor; v < 0.004f -> c.amber; else -> c.hot }
            val x = (n - values.size + i) * bw
            if (look.pixel) {
                val b = 3.dp.toPx()
                hgt = max(b, floor(hgt / b) * b)
            }
            drawRect(col, Offset(x, size.height - hgt), Size(max(1f, bw - 1f), hgt))
        }
        drawRect(Color.White.copy(alpha = 0.15f), Offset(0f, size.height - f(0.0015f) * size.height), Size(size.width, 1f))
    }
}
