package io.practicegroup.arena.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ArenaMatchEngineTest {
    @Test
    fun `engine is deterministic for fixed seed`() {
        val engine = ArenaMatchEngine()
        val spec = MatchSpec(
            matchId = "match-1",
            fighterA = FighterProfiles.Balanced,
            fighterB = FighterProfiles.GlassCannon,
            seed = 42L
        )

        val first = engine.run(spec, traceId = "trace-1")
        val second = engine.run(spec, traceId = "trace-1")

        val firstWinner = (first.last() as MatchEndedEvent).payload.winnerFighterId
        val secondWinner = (second.last() as MatchEndedEvent).payload.winnerFighterId

        assertEquals(first.size, second.size)
        assertEquals(firstWinner, secondWinner)
    }
}
