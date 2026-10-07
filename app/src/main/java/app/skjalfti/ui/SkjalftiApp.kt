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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import app.skjalfti.data.Locator
import app.skjalfti.update.UpdateWatch
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SkjalftiApp() {
    val context = LocalContext.current
    val skin by Engine.skin.collectAsState()
    val tab by Nav.tab.collectAsState()
    val overlay by Nav.overlay.collectAsState()
    val scope = rememberCoroutineScope()

    // Listen while the app is on screen, and keep the quake log fresh every two minutes.
    LifecycleResumeEffect(Unit) {
        Engine.listen(context, "ui")
        // Auto location: at most every 30 minutes, silently.
        val home = Engine.home.value
        if (Engine.autoLocate.value && Locator.hasPermission(context) &&
            System.currentTimeMillis() - home.atMs > 30 * 60_000L
        ) {
            scope.launch { Engine.locate(context, auto = true) }
        }
        scope.launch { runCatching { UpdateWatch.check(context, background = false) } }
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

    // The update notification or the Live card asks to open Setup.
    val openUpdates by UpdateWatch.openUpdates.collectAsState()
    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            Nav.overlay.value = null
            Nav.tab.value = Tab.SETUP
            UpdateWatch.openUpdates.value = false
        }
    }

    BackHandler(enabled = overlay != null) { Nav.back() }

    Crossfade(skin, animationSpec = tween(450), label = "skin") { s ->
        CompositionLocalProvider(LocalLook provides lookFor(s)) {
            Shell(s, tab, overlay, onTab = { Nav.overlay.value = null; Nav.tab.value = it })
        }
    }
}

@Composable
private fun Shell(skin: Skin, tab: Tab, overlay: Overlay?, onTab: (Tab) -> Unit) {
    val look = LocalLook.current
    val c = look.c
    if (skin == Skin.GLASS) {
        GlassShell(tab, overlay, onTab)
    } else {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            CompositionLocalProvider(LocalBackdrop provides null, LocalChrome provides Chrome()) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Box(Modifier.weight(1f)) { Content(tab, overlay) }
                    PixelNavBar(tab, onTab)
                }
            }
        }
    }
}

/**
 * Pure liquid glass, built like Ljós: the page (the molten sky plus everything on it) is recorded
 * once per frame. Cards refract the sky alone; the header and the floating tab bar refract the
 * whole page, so content visibly slides under them, frosted and bent. Nothing solid anywhere.
 */
@Composable
private fun GlassShell(tab: Tab, overlay: Overlay?, onTab: (Tab) -> Unit) {
    val c = LocalLook.current.c
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val home by Engine.home.collectAsState()
    val quakes by Engine.quakes.collectAsState()
    val page = rememberLayerBackdrop()
    val sky = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val chrome = Chrome(
        top = statusTop + HeaderBody - 8.dp,
        bottom = navBottom + TabBarHeight + TabBarGap,
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(page)) {
            GlassBackground(c.phosphor, Modifier.fillMaxSize().layerBackdrop(sky))
            CompositionLocalProvider(LocalBackdrop provides sky, LocalChrome provides chrome) {
                Content(tab, overlay)
            }
        }
        val headerPx = with(density) { (statusTop + HeaderBody).toPx() }
        GlassHeader(page, bodyPx = { headerPx }, fade = HeaderFade, tint = Color(0xFF06080B).copy(alpha = 0.34f))
        GlassTopBar(
            backdrop = page,
            place = home.label,
            accent = c.phosphor,
            showBack = overlay != null,
            loading = quakes.loading,
            onBack = { Nav.back() },
            onPlace = { onTab(Tab.SETUP) },
            onSync = { scope.launch { Engine.refresh() } },
            onGuide = { Nav.open(Overlay.Guide) },
        )
        GlassTabBar(
            backdrop = page,
            tab = tab,
            accent = c.phosphor,
            onTab = onTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = TabBarGap),
        )
    }
}

@Composable
private fun Content(tab: Tab, overlay: Overlay?) {
    when (overlay) {
        is Overlay.Quake -> QuakeScreen(overlay.id)
        Overlay.Guide -> GuideScreen()
        Overlay.Test -> TestScreen()
        null -> when (tab) {
            Tab.LIVE -> LiveScreen()
            Tab.LOG -> LogScreen()
            Tab.MAP -> MapScreen()
            Tab.NIGHT -> ReportScreen()
            Tab.SETUP -> SetupScreen()
        }
    }
}

@Composable
private fun PixelNavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val c = LocalLook.current.c
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tab.entries.forEach { t ->
            KButton(t.label, { onTab(t) }, Modifier.weight(1f), selected = t == tab, accent = c.amber, height = 46.dp)
        }
    }
}
