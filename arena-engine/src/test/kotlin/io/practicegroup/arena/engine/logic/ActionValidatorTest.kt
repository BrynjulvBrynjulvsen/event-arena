package io.practicegroup.arena.engine.logic

import io.practicegroup.arena.domain.Coordinate
import io.practicegroup.arena.domain.FighterActionCommand
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.FighterFeedbackStatus
import io.practicegroup.arena.domain.FighterProfiles
import io.practicegroup.arena.domain.FighterState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random

class ActionValidatorTest {
    private val validator = ActionValidator()

    @Test
    fun `accepts active fighter in-range attack`() {
        val state = baseState().copy(
            fighterA = baseState().fighterA.copy(position = Coordinate(1, 1)),
            fighterB = baseState().fighterB.copy(position = Coordinate(1, 2))
        )
        val cmd = FighterActionCommand(
            matchId = state.matchId,
            turn = state.turn,
            fighterId = state.actingFighterId,
            actionType = FighterActionType.ATTACK
        )

        val issue = validator.validateCommand(state, cmd, Instant.now())
        assertNull(issue)
    }

    @Test
    fun `rejects out of range attack`() {
        val state = baseState().copy(
            fighterA = baseState().fighterA.copy(position = Coordinate(0, 0)),
            fighterB = baseState().fighterB.copy(position = Coordinate(6, 4))
        )
        val cmd = FighterActionCommand(
            matchId = state.matchId,
            turn = state.turn,
            fighterId = state.actingFighterId,
            actionType = FighterActionType.ATTACK
        )

        val issue = validator.validateCommand(state, cmd, Instant.now())
        assertEquals(FighterFeedbackStatus.INVALID_MOVE, issue?.status)
        assertEquals("OUT_OF_RANGE", issue?.reasonCode)
    }

    @Test
    fun `rejects movement into cover cell`() {
        val state = baseState().copy(
            fighterA = baseState().fighterA.copy(position = Coordinate(2, 2)),
            coverEntities = mutableMapOf("cover-a" to CoverEntity("cover-a", Coordinate(3, 2)))
        )
        val cmd = FighterActionCommand(
            matchId = state.matchId,
            turn = state.turn,
            fighterId = state.actingFighterId,
            actionType = FighterActionType.MOVE_RIGHT
        )

        val issue = validator.validateCommand(state, cmd, Instant.now())
        assertEquals(FighterFeedbackStatus.INVALID_MOVE, issue?.status)
        assertEquals("CELL_BLOCKED_BY_COVER", issue?.reasonCode)
    }

    private fun baseState(): MatchState {
        val a = FighterState(FighterProfiles.Balanced, 90, Coordinate(0, 0))
        val b = FighterState(FighterProfiles.GlassCannon, 80, Coordinate(6, 4))
        return MatchState(
            matchId = "m1",
            traceId = "t1",
            fighterA = a,
            fighterB = b,
            random = Random(42),
            actingFighterId = a.profile.id,
            turn = 1,
            deadline = Instant.now().plusSeconds(5),
            worldVersion = 0,
            coverEntities = mutableMapOf(),
            pickupEntities = mutableMapOf(),
            attackBuffByFighter = mutableMapOf(a.profile.id to 0, b.profile.id to 0),
            factionByFighter = mutableMapOf(a.profile.id to "BLUE", b.profile.id to "RED"),
            boardWidth = 7,
            boardHeight = 5
        )
    }
}
