package io.practicegroup.arena.tui.mordant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ProjectionStoreTest {

    @Test
    fun `match summaries update from lifecycle and combat events`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        store.ingest(started("match-1"), selectedMatchId = null)
        store.ingest(spawned("match-1", "balanced", "BLUE"), selectedMatchId = null)

        val summary = store.summaries().single()
        assertEquals("match-1", summary.matchId)
        assertEquals(MatchStatus.RUNNING, summary.status)
        assertEquals(listOf("balanced"), summary.fighters)
        assertTrue(summary.latestEvent.contains("spawn"))
    }

    @Test
    fun `detail projection reproduces spawn move damage and end`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        store.ingest(started("match-1"), null)
        store.ingest(spawned("match-1", "balanced", "BLUE"), null)
        store.ingest(
            ParsedArenaEvent.FighterMoved("match-1", 1, Instant.parse("2026-01-01T00:00:01Z"), "balanced", Point(2, 1)),
            null
        )
        store.ingest(
            ParsedArenaEvent.DamageApplied("match-1", 1, Instant.parse("2026-01-01T00:00:02Z"), "balanced", 12),
            null
        )
        store.ingest(
            ParsedArenaEvent.MatchEnded("match-1", 2, Instant.parse("2026-01-01T00:00:03Z"), "balanced", "glass-cannon"),
            null
        )

        val detail = store.snapshot("match-1")!!.liveDetail
        assertEquals(Point(2, 1), detail.entities["balanced"]!!.position)
        assertEquals(12, detail.entities["balanced"]!!.hp)
        assertEquals(MatchStatus.ENDED, detail.status)
        assertEquals("balanced", detail.winnerId)
    }

    @Test
    fun `match cache eviction never drops selected match`() {
        val store = MatchProjectionStore(maxTrackedMatches = 2, maxBufferedEventsPerMatch = 10)
        store.ingest(started("match-1"), null)
        store.ingest(started("match-2"), null)
        store.ingest(started("match-3"), "match-1")

        val ids = store.summaries().map { it.matchId }.toSet()
        assertTrue("match-1" in ids)
        assertEquals(2, ids.size)
    }

    @Test
    fun `finished match keeps one frame per ingested event even within a single turn`() {
        val store = MatchProjectionStore(maxTrackedMatches = 5, maxBufferedEventsPerMatch = 10)
        store.ingest(started("match-1"), null)
        store.ingest(spawned("match-1", "balanced", "BLUE"), null)
        store.ingest(
            ParsedArenaEvent.FighterMoved("match-1", 1, Instant.parse("2026-01-01T00:00:01Z"), "balanced", Point(2, 1)),
            null
        )
        store.ingest(
            ParsedArenaEvent.DamageApplied("match-1", 1, Instant.parse("2026-01-01T00:00:02Z"), "balanced", 12),
            null
        )
        store.ingest(
            ParsedArenaEvent.MatchEnded("match-1", 1, Instant.parse("2026-01-01T00:00:03Z"), "balanced", "glass-cannon"),
            null
        )

        val frames = store.snapshot("match-1")!!.frames
        assertEquals(5, frames.size)
        assertEquals("turn 1: balanced moved to (2,1)", frames[2].detail.latestEvent)
        assertEquals("turn 1: balanced hp 12", frames[3].detail.latestEvent)
        assertEquals("turn 1: balanced wins", frames[4].detail.latestEvent)
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

    private fun spawned(matchId: String, fighterId: String, faction: String): ParsedArenaEvent.EntitySpawned {
        return ParsedArenaEvent.EntitySpawned(
            matchId = matchId,
            turn = 0,
            occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
            entity = SpawnedEntity(
                entityId = fighterId,
                entityType = "FIGHTER",
                faction = faction,
                position = Point(0, 0),
                attributes = mapOf("hp" to "20", "maxHp" to "20")
            )
        )
    }
}
