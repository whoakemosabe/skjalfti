package app.skjalfti

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Intent
import app.skjalfti.ui.SkjalftiApp
import app.skjalfti.update.UpdateWatch
import app.skjalfti.watch.WatchService

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Engine.init(this)
        WatchService.createChannels(this)
        UpdateWatch.createChannel(this)
        UpdateWatch.schedule(this)
        UpdateWatch.waitingVersion.value = UpdateWatch.waiting(this)
        handleIntent(intent)
        setContent {
            // No stretch overscroll: inside the recorded glass layer its spring-back can stick.
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    SkjalftiApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** The "update ready" notification asks to open Setup's update section. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateWatch.EXTRA_OPEN_UPDATES, false) == true) {
            intent.removeExtra(UpdateWatch.EXTRA_OPEN_UPDATES)
            UpdateWatch.openUpdates.value = true
        }
    }
}
