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
import androidx.compose.runtime.setValue
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("Guide", { Nav.open(Overlay.Guide) }, Modifier.weight(1f), selected = true)
            KButton("Shake test", { Nav.open(Overlay.Test) }, Modifier.weight(1f))
        }

        Section("Look", "Pixel console or liquid glass. Same app, different skin.") {
            Choice(Skin.entries, skin, { it.label }, { Engine.setSkin(it) })
        }

        UpdateSection()

        LocationSection()

        Section("Radius", "Only quakes this close show on the log.") {
            Choice(listOf(30, 60, 100), radius, { "$it km" }, { Engine.setRadius(it) })
        }

        AlertSection()

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

/** Big-quake alerts: threshold, plus a nudge if notifications are blocked. */
@Composable
private fun AlertSection() {
    val look = LocalLook.current
    val c = look.c
    val context = androidx.compose.ui.platform.LocalContext.current
    val mag by Engine.alertMag.collectAsState()
    var allowed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(app.skjalfti.alerts.QuakeAlerts.canNotify(context)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { allowed = it }
    Section("Quake alerts", "A notification for bigger quakes within your radius, checked about every 15 minutes.") {
        Choice(listOf(0f, 2.5f, 3f, 4f), mag, { if (it == 0f) "Off" else "M${if (it % 1f == 0f) it.toInt().toString() else it.toString()}+" }, { m ->
            Engine.setAlertMag(m)
            if (m > 0f && !allowed && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        })
        if (mag > 0f && !allowed) {
            Spacer(Modifier.height(8.dp))
            Txt("Notifications are off for Skjálfti, so alerts can't show.", look.t.small, c.hot, maxLines = 2)
        }
    }
}
