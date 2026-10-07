package app.skjalfti.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.LocateState
import app.skjalfti.data.Locator
import app.skjalfti.data.Place
import app.skjalfti.data.PlaceSource
import app.skjalfti.data.Places
import kotlinx.coroutines.launch

/**
 * Returns a function that asks for location permission if needed and then finds the phone.
 * If the user says no, the failure shows where the button was.
 */
@Composable
fun rememberLocate(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { Engine.locate(context) }
    }
    return {
        Engine.markLocationAsked()
        Engine.clearLocateError()
        if (Locator.hasPermission(context)) scope.launch { Engine.locate(context) }
        else ask.launch(Locator.permissions)
    }
}

private fun sourceLine(p: Place): String = when (p.source) {
    PlaceSource.GPS -> if (p.atMs > 0) "GPS · ${Fmt.ago(p.atMs)}" else "GPS"
    PlaceSource.SAVED -> "Saved place"
    PlaceSource.PRESET -> "Preset town"
}

/** The Setup screen's location section: current home, GPS, saving, saved places and presets. */
@Composable
fun LocationSection() {
    val look = LocalLook.current
    val c = look.c
    val context = LocalContext.current
    val home by Engine.home.collectAsState()
    val saved by Engine.saved.collectAsState()
    val auto by Engine.autoLocate.collectAsState()
    val state by Engine.locating.collectAsState()
    val locate = rememberLocate()
    var naming by remember { mutableStateOf(false) }

    Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
        Txt("Location", look.t.title, c.ink)
        Txt("Distances and wave arrival times are measured from here.", look.t.small, c.muted, maxLines = 3)
        Spacer(Modifier.height(10.dp))

        // Current home
        Panel(Modifier.fillMaxWidth(), raised = false, padding = 10.dp) {
            Txt(sourceLine(home), look.t.label, if (home.source == PlaceSource.GPS) c.phosphor else c.muted)
            Txt(home.label, look.t.title, c.ink)
            Txt(home.coords, look.t.label.copy(fontFamily = if (look.pixel) look.t.label.fontFamily else Fonts.geistMono), c.faint)
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KButton(
                if (state == LocateState.Locating) "Locating…" else "Use my location",
                { if (state != LocateState.Locating) locate() },
                Modifier.weight(1.4f), selected = true,
            )
            val alreadySaved = home.source == PlaceSource.SAVED
            KButton(if (alreadySaved) "Saved" else "Save place", { if (!alreadySaved) naming = true }, Modifier.weight(1f))
        }
        (state as? LocateState.Failed)?.let { f ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(f.why, look.t.small, c.hot, Modifier.weight(1f), maxLines = 2)
                if (f.why.contains("turned off")) {
                    KButton("Settings", {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, height = 36.dp)
                }
            }
        }

        if (naming) {
            Spacer(Modifier.height(8.dp))
            NameField(
                initial = home.label,
                onDone = { name -> Engine.savePlace(name); naming = false },
                onCancel = { naming = false },
            )
        }

        Spacer(Modifier.height(10.dp))
        Txt("Update from GPS when the app opens", look.t.small, c.muted)
        Spacer(Modifier.height(6.dp))
        Choice(listOf(true, false), auto, { if (it) "Auto" else "Off" }, { on ->
            Engine.setAutoLocate(on)
            if (on && !Locator.hasPermission(context)) locate()
        })

        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Txt("Saved places", look.t.label, c.muted)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                saved.forEach { p -> SavedRow(p, selected = p.id == home.id) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Txt("Towns", look.t.label, c.muted)
        Spacer(Modifier.height(6.dp))
        Places.presets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p -> KButton(p.label, { Engine.setHome(p) }, Modifier.weight(1f), selected = p.id == home.id) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SavedRow(p: Place, selected: Boolean) {
    val look = LocalLook.current
    val c = look.c
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Panel(
            Modifier.weight(1f).clickable { Engine.setHome(p) },
            frame = if (look.pixel && selected) c.phosphor else null,
            fill = if (look.pixel && selected) c.bevelLo else null,
            padding = 10.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).then(
                        if (look.pixel) Modifier.background(if (selected) c.phosphor else c.off)
                        else Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.phosphor else c.off)
                    )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(p.label, look.t.small, c.ink)
                    Txt(p.coords, look.t.label, c.faint)
                }
            }
        }
        KButton("Del", { Engine.deletePlace(p.id) }, height = 44.dp)
    }
}

/** A one-line name editor in the current skin. */
@Composable
private fun NameField(initial: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val look = LocalLook.current
    val c = look.c
    var text by remember { mutableStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("Name this place", look.t.label, c.muted)
        val field = if (look.pixel) Modifier.pixelFrame(c.bevelLo, c.phosphor)
        else Modifier.clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color(0x1FFFFFFF))
            .border(1.dp, androidx.compose.ui.graphics.Color(0x33FFFFFF), RoundedCornerShape(16.dp))
        BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 32) text = it },
            singleLine = true,
            textStyle = look.t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.phosphor),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(text) }),
            modifier = Modifier.fillMaxWidth().then(field).padding(horizontal = 12.dp, vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KButton("Save", { onDone(text) }, Modifier.weight(1f), selected = true)
            KButton("Cancel", onCancel, Modifier.weight(1f))
        }
    }
}

/**
 * A one-time card on the Live screen: the app starts at Njarðvík until the user lets it use GPS.
 * Hidden once answered or once a GPS or saved place is home.
 */
@Composable
fun LocationNudge() {
    val look = LocalLook.current
    val c = look.c
    val home by Engine.home.collectAsState()
    val state by Engine.locating.collectAsState()
    var dismissed by remember { mutableStateOf(Engine.locationAsked) }
    val locate = rememberLocate()
    if (dismissed || home.source != PlaceSource.PRESET) return
    Panel(Modifier.fillMaxWidth(), frame = if (look.pixel) c.amber else null, fill = if (look.pixel) c.bevelLo else null, padding = 12.dp) {
        Txt("Using ${home.label}", look.t.label, c.amber)
        Txt("Let Skjálfti use GPS so distances and FELT timing are measured from where your phone really is.", look.t.small, c.muted, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KButton(if (state == LocateState.Locating) "Locating…" else "Use my location", { locate() }, Modifier.weight(1.4f), selected = true, accent = c.amber)
            KButton("Not now", { Engine.markLocationAsked(); dismissed = true }, Modifier.weight(1f))
        }
    }
}
