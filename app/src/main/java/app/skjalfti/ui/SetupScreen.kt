package app.skjalfti.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skjalfti.Engine
import app.skjalfti.Skin
import app.skjalfti.seismo.Sensitivity

@Composable
fun SetupScreen() {
    val look = LocalLook.current
    val c = look.c
    val skin by Engine.skin.collectAsState()
    val radius by Engine.radius.collectAsState()
    val sens by Engine.sensitivity.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(if (look.pixel) "Setup.cfg" else "Settings")

        Section("Look", "Pixel console or liquid glass. Same app, different skin.") {
            Choice(Skin.entries, skin, { it.label }, { Engine.setSkin(it) })
        }

        UpdateSection()

        LocationSection()

        Section("Radius", "Only quakes this close show on the log.") {
            Choice(listOf(30, 60, 100), radius, { "$it km" }, { Engine.setRadius(it) })
        }

        Section("Sensitivity", "How small a shake counts. High may flag footsteps on wooden floors.") {
            Choice(Sensitivity.entries, sens, { it.label.lowercase().replaceFirstChar { ch -> ch.uppercase() } }, { Engine.setSensitivity(it) })
        }

        Panel(Modifier.fillMaxWidth(), raised = false, padding = 12.dp) {
            Txt("About", look.t.label, c.muted)
            Spacer(Modifier.height(6.dp))
            Txt(
                "Phones feel nearby M2+ quakes best lying flat on a hard surface. Quake data from Veðurstofa Íslands. Fonts: Departure Mono, Doto, Martian Mono, Geist (SIL OFL).",
                look.t.small, c.muted, maxLines = 6,
            )
        }
    }
}

@Composable
private fun Section(title: String, hint: String, content: @Composable () -> Unit) {
    val look = LocalLook.current
    Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
        Txt(title, look.t.title, look.c.ink)
        Txt(hint, look.t.small, look.c.muted, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
