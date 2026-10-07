package app.skjalfti.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.skjalfti.Engine
import app.skjalfti.Skin
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String) { LIVE("Live"), LOG("Log"), REPORT("Report"), SETUP("Setup") }

@Composable
fun SkjalftiApp() {
    val context = LocalContext.current
    val skin by Engine.skin.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.LIVE) }
    val scope = rememberCoroutineScope()

    // Listen while the app is on screen, and keep the quake log fresh every two minutes.
    LifecycleResumeEffect(Unit) {
        Engine.listen(context, "ui")
        val job = scope.launch {
            while (true) {
                Engine.refresh()
                delay(120_000)
            }
        }
        onPauseOrDispose {
            job.cancel()
            Engine.unlisten("ui")
        }
    }

    Crossfade(skin, animationSpec = tween(450), label = "skin") { s ->
        CompositionLocalProvider(LocalLook provides lookFor(s)) {
            Shell(s, tab, onTab = { tab = it })
        }
    }
}

@Composable
private fun Shell(skin: Skin, tab: Tab, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    val c = look.c
    if (skin == Skin.GLASS) {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            GlassBackground(c.phosphor, Modifier.fillMaxSize().layerBackdrop(backdrop))
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Body(tab, onTab)
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            CompositionLocalProvider(LocalBackdrop provides null) {
                Body(tab, onTab)
            }
        }
    }
}

@Composable
private fun Body(tab: Tab, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.LIVE -> LiveScreen(onOpenLog = { onTab(Tab.LOG) })
                Tab.LOG -> LogScreen()
                Tab.REPORT -> ReportScreen()
                Tab.SETUP -> SetupScreen()
            }
        }
        NavBar(tab, onTab)
    }
}

@Composable
private fun NavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    val c = look.c
    if (look.pixel) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { t ->
                KButton(t.label, { onTab(t) }, Modifier.weight(1f), selected = t == tab, accent = c.amber)
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp)) {
            Panel(Modifier.fillMaxWidth(), padding = 6.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tab.entries.forEach { t ->
                        KButton(t.label, { onTab(t) }, Modifier.weight(1f), selected = t == tab, height = 44.dp)
                    }
                }
            }
        }
    }
}
