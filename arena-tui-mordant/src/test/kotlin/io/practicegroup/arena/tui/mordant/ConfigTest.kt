package io.practicegroup.arena.tui.mordant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigTest {

    @Test
    fun `missing optional config does not crash and defaults are used`() {
        val config = MordantTuiConfig.fromArgs(emptyArray())

        assertNull(config.pinnedMatchId)
        assertEquals("localhost:9092", config.bootstrapServers)
    }

    @Test
    fun `blank optional config is normalized to null`() {
        val config = MordantTuiConfig.fromArgs(arrayOf("--arena.tui.match-id="))

        assertNull(config.pinnedMatchId)
    }

    @Test
    fun `historical import record count can be configured and is clamped to zero`() {
        val configured = MordantTuiConfig.fromArgs(arrayOf("--arena.tui.historical-import-records=1234"))
        val clamped = MordantTuiConfig.fromArgs(arrayOf("--arena.tui.historical-import-records=-5"))

        assertEquals(1234, configured.historicalImportRecords)
        assertEquals(0, clamped.historicalImportRecords)
    }
}
