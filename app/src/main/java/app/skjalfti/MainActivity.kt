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
import app.skjalfti.ui.SkjalftiApp
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
        setContent {
            // No stretch overscroll: inside the recorded glass layer its spring-back can stick.
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    SkjalftiApp()
                }
            }
        }
    }
}
