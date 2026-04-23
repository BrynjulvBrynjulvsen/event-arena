package io.practicegroup.arena.tui.mordant

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RendererIntegrationTest {

    @Test
    fun `rendered dashboard contains expected summary and detail`() {
        val recorder = TerminalRecorder(AnsiLevel.NONE, 120, 40, true, true, true, true)
        val terminal = Terminal(terminalInterface = recorder)
        val renderer = DashboardRenderer(terminal)
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)

        store.ingest(
            ParsedArenaEvent.MatchStarted("match-1", 0, Instant.parse("2026-01-01T00:00:00Z"), "balanced", "glass-cannon"),
            null
        )
        store.ingest(
            ParsedArenaEvent.EntitySpawned(
                "match-1",
                0,
                Instant.parse("2026-01-01T00:00:01Z"),
                SpawnedEntity("balanced", "FIGHTER", "BLUE", Point(0, 0), mapOf("hp" to "20", "maxHp" to "20"))
            ),
            null
        )

        val output = renderer.render(controller.snapshot(store))
        assertTrue(output.contains("match-1"))
        assertTrue(output.contains("balanced"))
        assertTrue(output.contains("board"))
    }

    @Test
    fun `rendered dashboard shows replay footer and help for finished matches`() {
        val recorder = TerminalRecorder(AnsiLevel.NONE, 120, 40, true, true, true, true)
        val terminal = Terminal(terminalInterface = recorder)
        val renderer = DashboardRenderer(terminal)
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)

        store.ingest(
            ParsedArenaEvent.MatchStarted("match-1", 0, Instant.parse("2026-01-01T00:00:00Z"), "balanced", "glass-cannon"),
            null
        )
        store.ingest(
            ParsedArenaEvent.EntitySpawned(
                "match-1",
                1,
                Instant.parse("2026-01-01T00:00:01Z"),
                SpawnedEntity("balanced", "FIGHTER", "BLUE", Point(0, 0), mapOf("hp" to "20", "maxHp" to "20"))
            ),
            null
        )
        store.ingest(
            ParsedArenaEvent.MatchEnded("match-1", 1, Instant.parse("2026-01-01T00:00:02Z"), "balanced", "glass-cannon"),
            null
        )

        controller.snapshot(store)
        controller.onInput(UiInput.Help, store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.PreviousFrame, store.summaries(), store.snapshot("match-1"), 8, 4)

        val snapshot = controller.snapshot(store)
        assertEquals(1, snapshot.visibleFrameIndex)

        val output = renderer.render(snapshot)
        assertTrue(output.contains("mode=replay"))
        assertTrue(output.contains("frame=2/3"))
        assertTrue(output.contains("step"))
        assertTrue(output.contains("finished matches use"))
    }
}
