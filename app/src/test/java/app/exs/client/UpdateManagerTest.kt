package app.exs.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(UpdateManager.compareVersions("0.2.0", "0.1.9") > 0)
        assertTrue(UpdateManager.compareVersions("1.0.0", "0.9.99") > 0)
        assertEquals(0, UpdateManager.compareVersions("0.1.0", "0.1.0-debug"))
    }
}
