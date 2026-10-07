package app.skjalfti.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.skjalfti.update.UpdateWatch
import app.skjalfti.update.Updater
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.floor

private sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data object UpToDate : UpdateUi()
    data class Available(val release: Updater.Release) : UpdateUi()
    data class Downloading(val release: Updater.Release) : UpdateUi()
    data class Ready(val file: File, val version: String, val note: String? = null) : UpdateUi()
    data class Error(val message: String) : UpdateUi()
}

/** Setup's update section: version, check, download with progress, install. */
@Composable
fun UpdateSection() {
    val look = LocalLook.current
    val c = look.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    val version = remember { Updater.installedVersion(context) }

    fun runCheck() {
        state = UpdateUi.Checking
        scope.launch {
            val r = Updater.check(context)
            state = when (r) {
                Updater.Check.UpToDate -> UpdateUi.UpToDate
                is Updater.Check.Available -> UpdateUi.Available(r.release)
                is Updater.Check.Failed -> UpdateUi.Error(r.message)
            }
            when (r) {
                Updater.Check.UpToDate -> UpdateWatch.remember(context, null)
                is Updater.Check.Available -> UpdateWatch.remember(context, r.release.version)
                is Updater.Check.Failed -> {}
            }
        }
    }

    // If an update was already spotted (card or notification), have it ready to download.
    LaunchedEffect(Unit) {
        if (UpdateWatch.waiting(context) != null) runCheck()
    }

    Panel(Modifier.fillMaxWidth(), padding = 12.dp, frame = if (look.pixel && state is UpdateUi.Available) c.amber else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Updates", look.t.title, c.ink)
                Txt("Version $version", look.t.small, c.muted)
                val line = when (val s = state) {
                    UpdateUi.Idle -> "Builds come from GitHub releases"
                    UpdateUi.Checking -> "Checking…"
                    UpdateUi.UpToDate -> "You're on the latest version"
                    is UpdateUi.Available -> "Version ${s.release.version} is available"
                    is UpdateUi.Downloading -> "Downloading ${s.release.version}… ${(progress * 100).toInt()}%"
                    is UpdateUi.Ready -> s.note ?: "Downloaded ${s.version}. Tap install."
                    is UpdateUi.Error -> s.message
                }
                Txt(
                    line, look.t.label,
                    when (state) {
                        is UpdateUi.Error -> c.hot
                        is UpdateUi.Available, is UpdateUi.Ready -> c.amber
                        UpdateUi.UpToDate -> c.phosphor
                        else -> c.faint
                    },
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when (val s = state) {
            is UpdateUi.Available -> KButton("Download ${s.release.version}", {
                state = UpdateUi.Downloading(s.release)
                progress = 0f
                scope.launch {
                    state = try {
                        UpdateUi.Ready(Updater.download(context, s.release) { progress = it }, s.release.version)
                    } catch (e: Exception) {
                        UpdateUi.Error(e.message ?: "Download failed")
                    }
                }
            }, Modifier.fillMaxWidth(), selected = true, accent = c.amber)
            is UpdateUi.Ready -> KButton("Install", {
                // Without "install unknown apps" Android opens that setting; keep the file so the
                // next tap retries the install, not the download.
                if (!Updater.install(context, s.file)) {
                    state = s.copy(note = "Allow Skjálfti to install updates, then tap Install again.")
                }
            }, Modifier.fillMaxWidth(), selected = true, accent = c.phosphor)
            is UpdateUi.Downloading -> ProgressBar(progress)
            else -> KButton(
                if (state == UpdateUi.Checking) "Checking…" else "Check for updates",
                { if (state != UpdateUi.Checking) runCheck() },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Download progress: chunky blocks in the console, a thin glowing bar on glass. */
@Composable
private fun ProgressBar(progress: Float) {
    val look = LocalLook.current
    val c = look.c
    val shown by animateFloatAsState(progress, tween(260), label = "download")
    Canvas(Modifier.fillMaxWidth().height(if (look.pixel) 24.dp else 6.dp)) {
        if (look.pixel) {
            val n = 20
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val lit = floor(shown * n).toInt()
            for (i in 0 until n) {
                drawRect(if (i < lit) c.amber else c.off, Offset(i * (w + gap), 0f), Size(w, size.height))
            }
        } else {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(c.off, cornerRadius = r)
            if (shown > 0.002f) drawRoundRect(c.phosphor, size = Size(size.width * shown, size.height), cornerRadius = r)
        }
    }
}

/** A card on the Live screen when a new version is waiting. Tap to go to Setup. */
@Composable
fun UpdateCard() {
    val look = LocalLook.current
    val c = look.c
    val waiting by UpdateWatch.waitingVersion.collectAsState()
    val v = waiting ?: return
    Panel(
        Modifier.fillMaxWidth().clickable { UpdateWatch.openUpdates.value = true },
        frame = if (look.pixel) c.amber else null,
        fill = if (look.pixel) c.bevelLo else null,
        padding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Txt(if (look.pixel) "> Update $v ready" else "Skjálfti $v is ready", look.t.small, c.amber)
                Txt("Tap to download and install", look.t.label, c.muted)
            }
            Txt(if (look.pixel) "[GO]" else "Update", look.t.label, c.amber)
        }
    }
}
