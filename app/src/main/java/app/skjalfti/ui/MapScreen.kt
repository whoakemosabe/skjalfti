package app.skjalfti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skjalfti.Engine

@Composable
fun MapScreen() {
    val look = LocalLook.current
    val c = look.c
    val q by Engine.quakes.collectAsState()
    val home by Engine.home.collectAsState()
    val radius by Engine.radius.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleBar(if (look.pixel) "Quake.map" else "Map") {
            Txt("${q.rows.size} in 24 h", look.t.label, c.muted)
        }
        Panel(Modifier.fillMaxWidth().weight(1f), raised = false, padding = if (look.pixel) 6.dp else 0.dp) {
            Box(
                Modifier.fillMaxSize().then(if (look.pixel) Modifier else Modifier.clip(RoundedCornerShape(26.dp)))
            ) {
                QuakeMap(
                    rows = q.rows,
                    home = home,
                    halfWidthKm = radius * 0.6,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { Nav.open(Overlay.Quake(it.quake.id)) },
                )
            }
        }
        Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Key(c.phosphor, "< M1.5")
                Key(c.amber, "M1.5–3")
                Key(c.hot, "M3+")
                Spacer(Modifier.weight(1f))
                Key(Color.White, "Felt", ring = true)
            }
            Txt("Older quakes fade. Tap one to open it.", look.t.label, c.faint, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Key(color: Color, label: String, ring: Boolean = false) {
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).then(
                if (look.pixel) Modifier.background(color)
                else Modifier.clip(RoundedCornerShape(50)).background(if (ring) Color.Transparent else color)
            ).then(if (ring && !look.pixel) Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)) else Modifier)
        )
        Spacer(Modifier.width(6.dp))
        Txt(label, look.t.label, look.c.muted)
    }
}
