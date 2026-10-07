package app.skjalfti.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.data.Achievement
import app.skjalfti.watch.WatchService

@Composable
fun ReportScreen() {
    val look = LocalLook.current
    val c = look.c
    val context = LocalContext.current
    val watching by Engine.watching.collectAsState()
    val version by Engine.storeVersion.collectAsState()
    val report = remember(version) { Engine.store.lastReport }
    val unlocked = remember(version) { Engine.store.unlocked() }
    val nights = remember(version) { Engine.store.nights }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Arm either way; without permission the report just won't pop up.
        WatchService.start(context)
    }
    fun arm() {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else WatchService.start(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(if (look.pixel) "Night.rpt" else "Night watch") {
            Txt(if (nights == 1) "1 night" else "$nights nights", look.t.label, c.muted)
        }

        if (watching) {
            Panel(Modifier.fillMaxWidth(), frame = if (look.pixel) c.phosphor else null, fill = if (look.pixel) c.bevelLo else null, padding = 16.dp) {
                Txt("Watch armed", look.t.label, c.phosphor)
                Spacer(Modifier.height(6.dp))
                Txt("Recording until 07:00", look.t.title, c.ink)
                Spacer(Modifier.height(4.dp))
                Txt("Leave the phone face up on a table, plugged in. Footsteps and bumps are filtered out.", look.t.small, c.muted, maxLines = 4)
                Spacer(Modifier.height(12.dp))
                AxisMeters(Modifier.fillMaxWidth().height(40.dp))
            }
            KButton("Stop watch", { WatchService.stop(context) }, Modifier.fillMaxWidth(), selected = true, accent = c.hot, height = 52.dp)
        }

        if (report != null) {
            ReportHero(report)
        } else if (!watching) {
            Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                Txt("No reports yet", look.t.title, c.ink)
                Spacer(Modifier.height(6.dp))
                Txt(
                    "Arm the night watch at bedtime. Your phone records all night, and at 07:00 you get a report: every quake nearby and which ones it felt.",
                    look.t.small, c.muted, maxLines = 5,
                )
            }
        }

        if (!watching) {
            KButton("Arm night watch", { arm() }, Modifier.fillMaxWidth(), selected = true, accent = c.magenta, height = 52.dp)
        }

        Txt("Achievements", look.t.label, c.muted, Modifier.padding(top = 4.dp))
        Achievement.entries.forEach { a -> AchievementRow(a, a in unlocked) }
    }
}

@Composable
private fun ReportHero(r: app.skjalfti.data.NightReport) {
    val look = LocalLook.current
    val c = look.c
    Panel(Modifier.fillMaxWidth(), frame = if (look.pixel) c.magenta else null, fill = if (look.pixel) androidx.compose.ui.graphics.Color(0xFF120A24) else null, padding = 16.dp) {
        Txt("While you slept · ${Fmt.hhmm(r.startMs)}–${Fmt.hhmm(r.endMs)}", look.t.label, c.magenta)
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("${r.quakes}", look.t.number.copy(fontSize = look.t.number.fontSize * 2.6f), c.ink)
            Spacer(Modifier.width(10.dp))
            Txt(if (r.quakes == 1) "quake" else "quakes", look.t.title, c.ink, Modifier.padding(bottom = 10.dp))
        }
        Txt(
            when {
                r.quakes == 0 -> "A quiet night on the peninsula"
                r.felt == 0 -> "Your phone felt none of them"
                else -> "Your phone felt ${r.felt} of them"
            },
            look.t.body, c.phosphor,
        )
    }
    Panel(Modifier.fillMaxWidth(), raised = false, padding = 10.dp) {
        Txt("Overnight trace · compressed", look.t.label, c.muted)
        Spacer(Modifier.height(8.dp))
        Strip(r.strip, c.phosphor, c.amber, Modifier.fillMaxWidth().height(60.dp))
    }
    if (r.strongestMag != null) {
        Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 58.dp, height = 50.dp)
                        .then(if (look.pixel) Modifier.background(magColor(r.strongestMag, c)) else Modifier.clip(RoundedCornerShape(16.dp)).background(magColor(r.strongestMag, c).copy(alpha = 0.22f))),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(Fmt.mag(r.strongestMag), look.t.number.copy(fontSize = look.t.title.fontSize * 1.5f), if (look.pixel) c.onAccent else c.ink)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt("Strongest", look.t.label, c.muted)
                    Txt("${r.strongestRegion ?: ""} ${r.strongestTimeMs?.let { Fmt.hhmm(it) } ?: ""}", look.t.title, c.ink)
                    Txt(
                        if (r.strongestFelt) "Felt · peak ${Fmt.g(r.strongestPeakG ?: 0f, look.pixel)}" else "Not felt",
                        look.t.small, if (r.strongestFelt) c.phosphor else c.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(a: Achievement, on: Boolean) {
    val look = LocalLook.current
    val c = look.c
    Panel(Modifier.fillMaxWidth(), frame = if (look.pixel && on) c.amber else null, fill = if (look.pixel && on) c.bevelLo else null, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).then(
                    if (look.pixel) Modifier.background(if (on) c.amber else c.off)
                    else Modifier.clip(RoundedCornerShape(50)).background(if (on) c.amber else c.off)
                )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(a.title, look.t.small, if (on) c.ink else c.faint)
                Txt(a.detail, look.t.label, if (on) c.muted else c.faint)
            }
            if (on) Txt("Unlocked", look.t.label, c.amber)
        }
    }
}
