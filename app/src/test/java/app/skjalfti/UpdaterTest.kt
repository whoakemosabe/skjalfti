package app.skjalfti

import app.skjalfti.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesVersions() {
        assertTrue(Updater.isNewer("v1.0.4", "1.0.3"))
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
        assertFalse(Updater.isNewer("v1.0.3", "1.0.3"))
        assertFalse(Updater.isNewer("1.0.2", "1.0.3"))
        assertTrue(Updater.isNewer("1.1", "1.0.99"))
        assertEquals(listOf(1, 0, 12), Updater.parts("v1.0.12"))
    }
}
