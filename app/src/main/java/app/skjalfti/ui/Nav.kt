package app.skjalfti.ui

import kotlinx.coroutines.flow.MutableStateFlow

enum class Tab(val label: String) { LIVE("Live"), LOG("Log"), MAP("Map"), NIGHT("Night"), SETUP("Setup") }

/** Screens that open over the tabs and close with back. */
sealed interface Overlay {
    data object Guide : Overlay
    data object Test : Overlay
    data class Quake(val id: String) : Overlay
}

/** App navigation, shared with notifications (they open a quake or Setup). */
object Nav {
    val tab = MutableStateFlow(Tab.LIVE)
    val overlay = MutableStateFlow<Overlay?>(null)

    fun open(o: Overlay) { overlay.value = o }
    fun back(): Boolean {
        if (overlay.value == null) return false
        overlay.value = null
        return true
    }
}
