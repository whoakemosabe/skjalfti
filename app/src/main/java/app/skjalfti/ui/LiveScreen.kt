package app.skjalfti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.data.FeltStatus
import app.skjalfti.seismo.Channel
import app.skjalfti.seismo.DetectorState
import app.skjalfti.seismo.Seismo
import app.skjalfti.seismo.Sensitivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class LiveStatus(
    val peak: Float = 0f,
    val noise: Float = 0f,
    val state: DetectorState = DetectorState.WARMUP,
    val running: Boolean = false,
    val blink: Boolean = true,
)

private fun LiveStatus.text(): String = when {
    !running -> "Sensor off"
    state == DetectorState.SHAKE -> "Shake detected"
    state == DetectorState.WARMUP -> "Warming up"
    noise > 0.01f -> "Phone moving"
    else -> "Listening"
}

@Composable
fun LiveScreen(onOpenLog: () -> Unit) {
    val look = LocalLook.current
    val c = look.c
    val channel by Engine.channel.collectAsState()
    val gain by Engine.gain.collectAsState()
    val sens by Engine.sensitivity.collectAsState()
    val quakes by Engine.quakes.collectAsState()
    val home by Engine.home.collectAsState()
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(LiveStatus()) }
    LaunchedEffect(channel) {
        var n = 0
        while (true) {
            status = LiveStatus(
                peak = Seismo.peak(channel, 30_000),
                noise = Seismo.detector.noiseFloorG,
                state = Seismo.detector.state,
                running = Seismo.running,
                blink = (n / 3) % 2 == 0,
            )
            n++
            delay(180)
        }
    }
    val shaking = status.state == DetectorState.SHAKE
    val traceColor = if (look.pixel) c.phosphor else c.phosphor
    val last = quakes.rows.firstOrNull()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        if (look.pixel) {
            TitleBar("Skjálfti") {
                Box(Modifier.size(9.dp).background(if (status.running && status.blink) c.hot else c.off))
                Spacer(Modifier.width(6.dp))
                Txt("REC", look.t.label, c.hot)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Skjálfti", look.t.mark)
                    Txt("${home.label} · ${status.text().lowercase()}", look.t.small, c.muted)
                }
                Panel(padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (status.running) c.phosphor else c.faint))
                        Spacer(Modifier.width(8.dp))
                        Txt("Live 200 Hz", look.t.label.copy(fontFamily = Fonts.geistMono), c.ink)
                    }
                }
            }
        }

        LocationNudge()

        // The screen
        Panel(raised = false, padding = if (look.pixel) 6.dp else 16.dp, frame = if (look.pixel && shaking) c.hot else null) {
            if (!look.pixel) {
                Row {
                    Txt("Ground motion · ${channel.label} axis", look.t.small, c.muted, Modifier.weight(1f))
                    Txt("−30s → now", look.t.label.copy(fontFamily = Fonts.geistMono), c.muted)
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(
                Modifier.fillMaxWidth().height(if (look.pixel) 236.dp else 180.dp)
                    .then(if (look.pixel) Modifier.background(c.screen) else Modifier)
            ) {
                Trace(channel, gain, traceColor, Modifier.fillMaxSize())
                if (look.pixel) {
                    Row(Modifier.fillMaxWidth().padding(8.dp)) {
                        Txt("CH1 // ${channel.label}-axis", look.t.label, c.phosphor, Modifier.weight(1f))
                        Txt("x$gain  T-30s", look.t.label, c.phosphor)
                    }
                    Txt(
                        "> ${status.text()}${if (status.blink) "_" else " "}",
                        look.t.body.copy(fontSize = look.t.title.fontSize),
                        if (shaking) c.amber else c.phosphor,
                        Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                }
            }
            if (!look.pixel) {
                Spacer(Modifier.height(14.dp))
                Row {
                    GlassStat("Peak", Fmt.g(status.peak, false), c.ink, Modifier.weight(1f))
                    GlassStat("Noise floor", Fmt.g(status.noise, false), c.ink, Modifier.weight(1f))
                    GlassStat("Status", status.text(), if (shaking) c.phosphor else c.ink, Modifier.weight(1f))
                }
            }
        }

        if (look.pixel) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Readout("Peak g", Fmt.g(status.peak, true), c.amber, Modifier.weight(1f))
                Readout("Last M", last?.let { Fmt.mag(it.quake.magnitude) } ?: "--", c.amber, Modifier.weight(1f))
                Readout("Dist km", last?.let { "${it.distKm.toInt()}" } ?: "--", c.amber, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Panel(Modifier.weight(1f), padding = 10.dp) {
                    Txt("Axis level", look.t.label, c.muted)
                    Spacer(Modifier.height(8.dp))
                    AxisMeters(Modifier.fillMaxWidth().height(86.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        listOf("X", "Y", "Z").forEach { Txt(it, look.t.label, c.muted, Modifier.weight(1f)) }
                    }
                }
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KButton("Gain ${gain}x", { Engine.setGain(nextGain(gain)) }, Modifier.weight(1f), selected = true)
                        KButton("Axis ${channel.label}", { Engine.setChannel(nextChannel(channel)) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KButton("Sens ${sens.label}", { Engine.setSensitivity(nextSens(sens)) }, Modifier.weight(1f))
                        KButton("Sync", { scope.launch { Engine.refresh() } }, Modifier.weight(1f))
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Channel.entries.forEach { ch ->
                    KButton(ch.label, { Engine.setChannel(ch) }, Modifier.weight(1f), selected = ch == channel)
                }
                KButton("${gain}×", { Engine.setGain(nextGain(gain)) }, Modifier.weight(1f))
            }
        }

        // Last quake
        LastQuake(last, onOpenLog)
    }
}

@Composable
private fun GlassStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Column(modifier) {
        Txt(label, look.t.label, look.c.muted)
        Txt(value, look.t.number.copy(fontSize = look.t.title.fontSize), color)
    }
}

@Composable
private fun LastQuake(row: app.skjalfti.QuakeRow?, onOpenLog: () -> Unit) {
    val look = LocalLook.current
    val c = look.c
    if (look.pixel) {
        Panel(Modifier.fillMaxWidth().clickable(onClick = onOpenLog), frame = c.amber, fill = c.bevelLo, padding = 10.dp) {
            Row {
                val text = row?.let { "> M${Fmt.mag(it.quake.magnitude)} ${it.quake.region}" } ?: "> No quakes nearby yet"
                Txt(text, look.t.body.copy(fontSize = look.t.title.fontSize), c.amber, Modifier.weight(1f))
                row?.let { Txt(statusWord(it.match.status), look.t.body.copy(fontSize = look.t.title.fontSize), c.amber) }
            }
        }
    } else {
        Panel(Modifier.fillMaxWidth().clickable(onClick = onOpenLog), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(60.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Color(0x1FFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(row?.let { Fmt.mag(it.quake.magnitude) } ?: "–", look.t.number, c.ink)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(row?.quake?.region ?: "No quakes nearby yet", look.t.title, c.ink)
                    if (row != null) {
                        Txt("${Fmt.ago(row.quake.timeMs)} · ${Fmt.km(row.distKm)} away", look.t.small, c.muted)
                        Txt(feltLine(row.match.status), look.t.small, if (row.match.status == FeltStatus.FELT) c.phosphor else c.muted)
                    }
                }
            }
        }
    }
}

fun statusWord(s: FeltStatus) = when (s) {
    FeltStatus.FELT -> "FELT"
    FeltStatus.MISS -> "MISS"
    FeltStatus.OFFLINE -> "OFF"
    FeltStatus.PENDING -> "WAIT"
}

fun feltLine(s: FeltStatus) = when (s) {
    FeltStatus.FELT -> "Your phone felt it"
    FeltStatus.MISS -> "Recording, but too faint to feel"
    FeltStatus.OFFLINE -> "Phone wasn't listening"
    FeltStatus.PENDING -> "Waves still arriving…"
}

private fun nextGain(g: Int) = when (g) { 1 -> 2; 2 -> 4; 4 -> 8; else -> 1 }
private fun nextChannel(c: Channel) = Channel.entries[(c.ordinal + 1) % Channel.entries.size]
private fun nextSens(s: Sensitivity) = Sensitivity.entries[(s.ordinal + 1) % Sensitivity.entries.size]
