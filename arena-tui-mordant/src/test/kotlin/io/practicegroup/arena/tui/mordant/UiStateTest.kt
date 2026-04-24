package io.practicegroup.arena.tui.mordant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class UiStateTest {

    @Test
    fun `pinned match selection overrides auto follow at startup`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 5)
        store.ingest(started("match-1"), null)
        store.ingest(started("match-2"), null)

        val controller = DashboardController("match-1", initialAutoFollow = true, initialReplaySpeed = 1.0)
        val snapshot = controller.snapshot(store)

        assertEquals("match-1", snapshot.selectedMatchId)
        assertFalse(snapshot.autoFollow)
    }

    @Test
    fun `focus filter pause replay speed and viewport change`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 5)
        store.ingest(started("match-1"), null)
        store.ingest(
            ParsedArenaEvent.TurnOpened(
                "match-1",
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                boardWidth = 20,
                boardHeight = 12,
                visibleEntities = emptyList()
            ),
            null
        )

        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)
        controller.snapshot(store)
        controller.onInput(UiInput.TogglePause, store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.SpeedUp, store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.Filter, store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.Character('m'), store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.Enter, store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.Enter, store.summaries(), store.snapshot("match-1"), 8, 4)
        controller.onInput(UiInput.NextFrame, store.summaries(), store.snapshot("match-1"), 8, 4)

        val snapshot = controller.snapshot(store)
        assertTrue(snapshot.paused)
        assertEquals(2.0, snapshot.replaySpeed)
        assertEquals("m", snapshot.filterText)
        assertEquals(FocusPane.BOARD, snapshot.focusPane)
        assertEquals(1, snapshot.viewport.x)
    }

    @Test
    fun `manual navigation resists auto selecting new matches`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 5)
        store.ingest(started("match-1"), null)
        store.ingest(started("match-2"), null)

        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)
        controller.snapshot(store)
        controller.onInput(UiInput.Up, store.summaries(), store.snapshot("match-2"), 8, 4)
        store.ingest(started("match-3"), "match-1")

        val snapshot = controller.snapshot(store)
        assertEquals("match-1", snapshot.selectedMatchId)
        assertFalse(snapshot.autoFollow)
    }

    @Test
    fun `selecting ended match pauses on final frame and disables follow`() {
        val store = finishedMatchStore()
        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)

        val snapshot = controller.snapshot(store)

        assertEquals("match-ended", snapshot.selectedMatchId)
        assertTrue(snapshot.paused)
        assertFalse(snapshot.autoFollow)
        assertTrue(snapshot.replayControlsActive)
        assertEquals(snapshot.totalFrameCount - 1, snapshot.visibleFrameIndex)
        assertTrue(snapshot.atLastFrame)
        assertEquals("turn 2: blue wins", snapshot.visibleFrame?.detail?.latestEvent)
    }

    @Test
    fun `stepping backward and forward uses stored match frames`() {
        val store = finishedMatchStore()
        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)
        controller.snapshot(store)

        controller.onInput(UiInput.PreviousFrame, store.summaries(), store.snapshot("match-ended"), 8, 4)
        val previous = controller.snapshot(store)
        assertEquals(2, previous.visibleFrameIndex)
        assertEquals(Point(1, 0), previous.visibleFrame?.detail?.entities?.get("blue")?.position)
        assertEquals("turn 2: blue moved to (1,0)", previous.visibleFrame?.detail?.latestEvent)

        controller.onInput(UiInput.NextFrame, store.summaries(), store.snapshot("match-ended"), 8, 4)
        val next = controller.snapshot(store)
        assertEquals(3, next.visibleFrameIndex)
        assertEquals("turn 2: blue wins", next.visibleFrame?.detail?.latestEvent)
        assertTrue(next.atLastFrame)
    }

    @Test
    fun `stepping backward clamps at first frame`() {
        val store = finishedMatchStore()
        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)
        controller.snapshot(store)

        repeat(10) {
            controller.onInput(UiInput.PreviousFrame, store.summaries(), store.snapshot("match-ended"), 8, 4)
        }

        val snapshot = controller.snapshot(store)
        assertEquals(0, snapshot.visibleFrameIndex)
        assertTrue(snapshot.atFirstFrame)
        assertEquals("turn 0: match started", snapshot.visibleFrame?.detail?.latestEvent)
    }

    @Test
    fun `selecting live match keeps live behavior`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        store.ingest(started("match-ended"), null)
        store.ingest(ended("match-ended"), null)
        store.ingest(started("match-live"), null)

        val controller = DashboardController(null, initialAutoFollow = true, initialReplaySpeed = 1.0)
        controller.snapshot(store)
        controller.onInput(UiInput.Down, store.summaries(), store.snapshot("match-ended"), 8, 4)

        val snapshot = controller.snapshot(store)
        assertEquals("match-live", snapshot.selectedMatchId)
        assertEquals(MatchStatus.RUNNING, snapshot.liveRecord?.summary?.status)
        assertFalse(snapshot.replayControlsActive)
        assertEquals(snapshot.totalFrameCount - 1, snapshot.visibleFrameIndex)
        assertTrue(snapshot.atLastFrame)
    }

    @Test
    fun `re enabling follow and switching matches resets replay position`() {
        val store = finishedMatchStore()
        store.ingest(started("match-live"), null)
        val controller = DashboardController(null, initialAutoFollow = false, initialReplaySpeed = 1.0)
        controller.snapshot(store)
        controller.onInput(UiInput.PreviousFrame, store.summaries(), store.snapshot("match-ended"), 8, 4)
        assertEquals(2, controller.snapshot(store).visibleFrameIndex)

        controller.onInput(UiInput.ToggleFollow, store.summaries(), store.snapshot("match-ended"), 8, 4)
        val followed = controller.snapshot(store)
        assertEquals("match-live", followed.selectedMatchId)
        assertFalse(followed.replayControlsActive)
        assertEquals(followed.totalFrameCount - 1, followed.visibleFrameIndex)

        controller.onInput(UiInput.Up, store.summaries(), store.snapshot("match-live"), 8, 4)
        val returned = controller.snapshot(store)
        assertEquals("match-ended", returned.selectedMatchId)
        assertNotNull(returned.visibleFrameIndex)
        assertEquals(returned.totalFrameCount - 1, returned.visibleFrameIndex)
        assertTrue(returned.atLastFrame)
    }

    private fun started(matchId: String): ParsedArenaEvent.MatchStarted {
        return ParsedArenaEvent.MatchStarted(
            matchId = matchId,
            turn = 0,
            occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
            fighterAId = "balanced",
            fighterBId = "glass-cannon"
        )
    }

    private fun ended(matchId: String): ParsedArenaEvent.MatchEnded {
        return ParsedArenaEvent.MatchEnded(
            matchId = matchId,
            turn = 1,
            occurredAt = Instant.parse("2026-01-01T00:00:03Z"),
            winnerFighterId = "blue",
            loserFighterId = "red"
        )
    }

    private fun finishedMatchStore(): MatchProjectionStore {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        store.ingest(
            ParsedArenaEvent.MatchStarted(
                "match-ended",
                0,
                Instant.parse("2026-01-01T00:00:00Z"),
                "blue",
                "red"
            ),
            null
        )
        store.ingest(
            ParsedArenaEvent.EntitySpawned(
                "match-ended",
                1,
                Instant.parse("2026-01-01T00:00:01Z"),
                SpawnedEntity("blue", "FIGHTER", "BLUE", Point(0, 0), mapOf("hp" to "20", "maxHp" to "20"))
            ),
            null
        )
        store.ingest(
            ParsedArenaEvent.FighterMoved(
                "match-ended",
                2,
                Instant.parse("2026-01-01T00:00:02Z"),
                "blue",
                Point(1, 0)
            ),
            null
        )
        store.ingest(
            ParsedArenaEvent.MatchEnded(
                "match-ended",
                2,
                Instant.parse("2026-01-01T00:00:03Z"),
                "blue",
                "red"
            ),
            null
        )
        return store
    }
}
