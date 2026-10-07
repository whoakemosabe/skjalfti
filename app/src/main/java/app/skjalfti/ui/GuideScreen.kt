package app.skjalfti.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private class Topic(val title: String, val intro: String?, val items: List<Pair<String, String>>)

private val topics = listOf(
    Topic(
        "What this is",
        "Skjálfti turns your phone's accelerometer into a seismograph and checks every quake the Icelandic Met Office records near you against what your phone actually felt.",
        emptyList(),
    ),
    Topic(
        "Live", null,
        listOf(
            "Trace" to "The last 30 seconds of ground motion, newest on the right. It scrolls by real time, so it's smooth at any refresh rate.",
            "Gain" to "Zooms the trace vertically: 1×, 2×, 4×, 8×. Display only; detection doesn't change.",
            "Axis" to "Which direction to draw. Z is up-and-down with the phone flat, X and Y are sideways, Sum is all three combined. The detector always uses Sum.",
            "Sens" to "How big a jump over the background counts as a shake. High catches smaller quakes and also footsteps; Low only strong shaking.",
            "Test" to "Opens the quiet spot finder and shake test.",
            "Status" to "Warming up: the first 10 s while it learns the background. Listening: normal. Shake detected: the detector has fired. Phone moving: too much background to detect anything. Sensor off: not recording.",
            "Readouts" to "Peak g is the biggest shake in the last 30 s. Last M and Dist are the newest quake near you.",
            "Axis meters" to "Live shaking on each axis, on a log scale from sensor noise (bottom) to a bump (top).",
            "Ticker" to "The newest nearby quake and whether your phone felt it. Tap it for the full story.",
        ),
    ),
    Topic(
        "Log", "Every quake in the last 24 hours within your radius, newest first, with events per hour on top.",
        listOf(
            "FELT" to "The detector fired while the waves were arriving at your phone.",
            "MISS" to "The phone was recording but stayed calm: the quake was too small or too far for it.",
            "OFF" to "The phone wasn't recording then (app closed and no night watch).",
            "WAIT" to "The waves may still be on their way; check back in a minute.",
            "Auto" to "The Met Office hasn't reviewed this one yet; magnitude and place can change.",
            "Filters" to "All, only Felt, or only M2 and up. Sync fetches the latest right away (it also refreshes every two minutes).",
        ),
    ),
    Topic(
        "Map", "The real coastline of south-west Iceland with you in the middle and rings every 10–30 km.",
        listOf(
            "Squares or dots" to "Each quake, sized by magnitude and coloured green (under M1.5), amber (to M3) or red (M3+). Older ones fade.",
            "White outline" to "Your phone felt it.",
            "Tap" to "Opens the quake.",
        ),
    ),
    Topic(
        "A quake", null,
        listOf(
            "Map" to "Where it was relative to you.",
            "Recording" to "If your phone felt it, the minute of trace it saved around the trigger. Amber lines show when the P wave (≈6 km/s) and S wave (≈3.5 km/s) should have reached you; the green shading is when the detector fired.",
            "Feel it" to "Plays the shaking through the vibration motor at real speed. Without a recording it simulates a P jolt then the S rumble from the magnitude and distance.",
            "Hear it" to "The recording sped up 40×, the way seismologists listen to quakes. A minute of ground motion becomes a second and a half of rumble.",
            "Share" to "Makes a card image in your current look and opens the share sheet.",
        ),
    ),
    Topic(
        "Night watch", "Arm it at bedtime. The phone records all night and at 07:00 posts a report: how many quakes, how many it felt, the strongest, and a compressed trace of the night.",
        listOf(
            "Placement" to "Face up on a hard surface, plugged in. A bedside table works; a bed doesn't.",
            "Stop" to "Ends it early. Under 30 minutes counts as a test and makes no report.",
            "Battery saver" to "On OnePlus, Oppo and others, set Skjálfti's battery to Unrestricted or the system may stop the watch.",
        ),
    ),
    Topic(
        "Alerts", "A notification for every quake at or above your chosen size within your radius: Off, M2.5, M3 or M4 in Setup.",
        listOf(
            "Timing" to "Checked every refresh while the app is open and about every 15 minutes in the background (Android's shortest repeat).",
            "No replays" to "Each quake alerts once, and nothing older than when you turned alerts on.",
        ),
    ),
    Topic(
        "Test", null,
        listOf(
            "Quiet spot finder" to "Shows the background shaking live with a rating and the quietest reading so far. Walk the phone round the house and leave it ten seconds per spot.",
            "Shake test" to "Tap the table near the phone and watch the detector fire. Use it to pick a sensitivity.",
        ),
    ),
    Topic(
        "Setup", null,
        listOf(
            "Look" to "Pixel console or liquid glass. Same app, different skin.",
            "Updates" to "Checks GitHub for a new version, downloads it and opens the installer. It also checks by itself and shows a card on Live.",
            "Location" to "Use my location takes a GPS fix and names it. Save place keeps it under your own name. Auto updates it when the app opens. Or pick a town.",
            "Radius" to "Only quakes this close appear in the log, map and alerts.",
        ),
    ),
    Topic(
        "How FELT works", "From the quake's position and depth Skjálfti knows the straight-line distance to your phone, so it knows when the shaking should arrive. If the detector fired in that window (with a few seconds' slack for clock error), the quake was felt. Shakes stronger than 0.15 g are treated as someone handling the phone and never count.",
        emptyList(),
    ),
    Topic(
        "Limits", "A phone's accelerometer is far less sensitive than a real seismometer. Expect to feel nearby M2+ quakes with the phone lying still on a hard surface; small or distant ones will be a MISS, and that's normal.",
        emptyList(),
    ),
    Topic(
        "Achievements", null,
        listOf(
            "First watch" to "Your first night report.",
            "Night watch" to "Seven nights logged.",
            "First felt" to "Your phone felt a quake.",
            "Big one" to "Felt an M3 or larger.",
            "Centurion" to "A hundred quakes on your log.",
        ),
    ),
    Topic(
        "Credits", "Quake data: Veðurstofa Íslands. Coastline: Natural Earth. Fonts: Departure Mono, Doto, Martian Mono and Geist, all under the SIL Open Font License. Liquid glass: Kyant's Backdrop.",
        emptyList(),
    ),
)

/** The in-app manual: HELP.TXT in the console, clean cards on glass. */
@Composable
fun GuideScreen() {
    val look = LocalLook.current
    val c = look.c
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TitleBar(if (look.pixel) "Help.txt" else "Guide") {
                BackKey()
            }
        }
        items(topics) { t ->
            Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Txt(if (look.pixel) "== ${t.title} ==" else t.title, look.t.title, if (look.pixel) c.phosphor else c.ink)
                t.intro?.let {
                    Spacer(Modifier.height(6.dp))
                    Txt(it, look.t.small, c.muted, maxLines = 12, caps = false)
                }
                if (t.items.isNotEmpty()) Spacer(Modifier.height(4.dp))
                t.items.forEach { (term, text) ->
                    Column(Modifier.padding(top = 8.dp)) {
                        Txt(if (look.pixel) "> $term" else term, look.t.small, c.amber)
                        Txt(text, look.t.small, c.muted, maxLines = 12, caps = false)
                    }
                }
            }
        }
    }
}
