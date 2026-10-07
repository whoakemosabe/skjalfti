package app.skjalfti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import app.skjalfti.QuakeRow
import app.skjalfti.data.FeltStatus
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { ALL("All"), FELT("Felt"), M2("M2+") }

@Composable
fun LogScreen() {
    val look = LocalLook.current
    val c = look.c
    val q by Engine.quakes.collectAsState()
    val radius by Engine.radius.collectAsState()
    var filter by remember { mutableStateOf(Filter.ALL) }
    val scope = rememberCoroutineScope()
    val rows = when (filter) {
        Filter.ALL -> q.rows
        Filter.FELT -> q.rows.filter { it.match.status == FeltStatus.FELT }
        Filter.M2 -> q.rows.filter { it.quake.magnitude >= 2.0 }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (look.pixel) 6.dp else 10.dp),
    ) {
        item {
            TitleBar(if (look.pixel) "Quake.log" else "Quakes") {
                KButton(if (q.loading) "…" else "Sync", { scope.launch { Engine.refresh() } }, height = 36.dp)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth(), raised = false, padding = 12.dp) {
                Row {
                    Txt("Events / hour", look.t.label, c.muted, Modifier.weight(1f))
                    Txt("within $radius km", look.t.label, c.muted)
                }
                Spacer(Modifier.height(10.dp))
                HourBars(q.hourly, Modifier.fillMaxWidth().height(72.dp))
                Spacer(Modifier.height(6.dp))
                Row {
                    Txt("-24h", look.t.label, c.faint, Modifier.weight(1f))
                    Txt("-12h", look.t.label, c.faint, Modifier.weight(1f))
                    Txt("Now", look.t.label, c.faint)
                }
            }
        }
        item {
            Choice(Filter.entries, filter, { it.label }, { filter = it })
        }
        when {
            q.error != null && q.rows.isEmpty() -> item { Note("Offline — can't reach Veðurstofa. Tap sync to retry.") }
            q.loading && q.rows.isEmpty() -> item { Note("Scanning the network…") }
            rows.isEmpty() -> item { Note(if (filter == Filter.ALL) "No quakes within $radius km in the last 24 h." else "Nothing matches that filter today.") }
            else -> items(rows, key = { it.quake.id }) { QuakeLine(it) }
        }
        item {
            Txt("Data: Veðurstofa Íslands", look.t.label, c.faint)
        }
    }
}

@Composable
private fun Note(text: String) {
    val look = LocalLook.current
    Panel(Modifier.fillMaxWidth()) { Txt(text, look.t.body, look.c.muted, maxLines = 3) }
}

fun magColor(m: Double, c: Palette): Color = when {
    m >= 3.0 -> c.hot
    m >= 1.5 -> c.amber
    else -> c.phosphor
}

@Composable
private fun QuakeLine(r: QuakeRow) {
    val look = LocalLook.current
    val c = look.c
    val q = r.quake
    Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val badge = magColor(q.magnitude, c)
            Box(
                Modifier.size(width = 54.dp, height = 44.dp)
                    .then(if (look.pixel) Modifier.background(badge) else Modifier.clip(RoundedCornerShape(14.dp)).background(badge.copy(alpha = 0.22f))),
                contentAlignment = Alignment.Center,
            ) {
                Txt(Fmt.mag(q.magnitude), look.t.number.copy(fontSize = look.t.title.fontSize * 1.4f), if (look.pixel) c.onAccent else c.ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(q.region, look.t.title.copy(fontSize = look.t.small.fontSize * 1.05f), c.ink)
                Txt(
                    "${Fmt.hhmm(q.timeMs)}  ${Fmt.km(r.distKm)}  d${Fmt.mag(q.depthKm)}${if (q.reviewed) "" else "  auto"}",
                    look.t.small, c.muted,
                )
            }
            val s = r.match.status
            Txt(
                if (look.pixel) statusWord(s) else statusWord(s).lowercase().replaceFirstChar { it.uppercase() },
                look.t.label,
                when (s) { FeltStatus.FELT -> c.phosphor; FeltStatus.PENDING -> c.amber; else -> c.faint },
            )
        }
    }
}
