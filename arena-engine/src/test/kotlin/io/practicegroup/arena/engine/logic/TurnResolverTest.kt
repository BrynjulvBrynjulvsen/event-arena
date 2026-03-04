package io.practicegroup.arena.engine.logic

import io.practicegroup.arena.domain.ActionResolvedEvent
import io.practicegroup.arena.domain.ActionEffectType
import io.practicegroup.arena.domain.Coordinate
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.FighterProfiles
import io.practicegroup.arena.domain.FighterState
import io.practicegroup.arena.domain.MatchEndedEvent
import io.practicegroup.arena.domain.TurnClosedEvent
import io.practicegroup.arena.domain.TurnOpenedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random

class TurnResolverTest {
    @Test
    fun `ranged attack emits damage effect with range metadata`() {
        val state = baseState(
            activeFighterId = FighterProfiles.GlassCannon.id,
            fighterAPos = Coordinate(1, 1),
            fighterBPos = Coordinate(1, 3)
        )
        state.pendingAction = FighterActionType.ATTACK

        val result = TurnResolver(turnDurationMs = 1000).resolveTurn(state, Instant.now())
        val actionResolved = result.matchEvents.filterIsInstance<ActionResolvedEvent>().first()

        assertEquals(FighterActionType.ATTACK, actionResolved.payload.actionType)
        assertTrue(actionResolved.payload.effects.any { it.effectType == ActionEffectType.DAMAGE })
        assertEquals("2", actionResolved.payload.effects.first { it.effectType == ActionEffectType.DAMAGE }.metadata["range"])
    }

    @Test
    fun `cover applies metadata on attack`() {
        val state = baseState(
            activeFighterId = FighterProfiles.GlassCannon.id,
            fighterAPos = Coordinate(1, 1),
            fighterBPos = Coordinate(1, 2)
        ).copy(
            random = object : Random(1) {
                override fun nextDouble(): Double = 0.10
            }
        )
        state.coverEntities["cover-a"] = CoverEntity("cover-a", Coordinate(2, 1))
        state.pendingAction = FighterActionType.ATTACK

        val result = TurnResolver(turnDurationMs = 1000).resolveTurn(state, Instant.now())
        val actionResolved = result.matchEvents.filterIsInstance<ActionResolvedEvent>().first()
        val damage = actionResolved.payload.effects.firstOrNull { it.effectType == ActionEffectType.DAMAGE }

        assertEquals("true", damage?.metadata?.get("coverApplied"))
    }

    @Test
    fun `regen emits heal effect and hp change`() {
        val state = baseState(
            activeFighterId = FighterProfiles.Balanced.id,
            fighterAHp = 80,
            fighterBHp = 70
        )
        state.pendingAction = FighterActionType.WAIT

        val result = TurnResolver(turnDurationMs = 1000).resolveTurn(state, Instant.now())
        val actionResolved = result.matchEvents.filterIsInstance<ActionResolvedEvent>().first()
        val regenEffect = actionResolved.payload.effects.firstOrNull { it.effectType == ActionEffectType.HEAL && it.metadata["source"] == "regen" }

        assertTrue(regenEffect != null)
        assertTrue(result.feedbackEvents.any { feedback -> feedback.payload.entityChanges.any { it.attributes["hp"] != null } })
    }

    @Test
    fun `pickup consumption emits despawn and status effects`() {
        val state = baseState(activeFighterId = FighterProfiles.Balanced.id, fighterAPos = Coordinate(1, 1))
        state.pickupEntities["pickup-1"] = PickupEntity("pickup-1", PickupKind.ATTACK_BOOST_5, Coordinate(2, 1))
        state.pendingAction = FighterActionType.MOVE_RIGHT

        val result = TurnResolver(turnDurationMs = 1000).resolveTurn(state, Instant.now())
        val actionResolved = result.matchEvents.filterIsInstance<ActionResolvedEvent>().first()

        assertTrue(actionResolved.payload.effects.any { it.effectType == ActionEffectType.APPLIED_STATUS })
        assertTrue(actionResolved.payload.effects.any { it.effectType == ActionEffectType.DESPAWNED })
    }

    @Test
    fun `non-terminal turn emits turn closed followed by next turn opened`() {
        val state = baseState(activeFighterId = FighterProfiles.Balanced.id)
        state.pendingAction = FighterActionType.WAIT

        val result = TurnResolver(turnDurationMs = 1000).resolveTurn(state, Instant.now())

        assertTrue(result.lifecycleEvents.first() is TurnClosedEvent)
        assertTrue(result.lifecycleEvents.last() is TurnOpenedEvent)
        assertEquals(2, result.lifecycleEvents.size)
        assertEquals(false, result.matchEnded)
    }

    @Test
    fun `terminal turn emits match ended and no next turn opened`() {
        val state = baseState(
            activeFighterId = FighterProfiles.GlassCannon.id,
            fighterAPos = Coordinate(1, 1),
            fighterBPos = Coordinate(1, 2),
            fighterAHp = 1
        ).copy(
            random = object : Random(1) {
                override fun nextDouble(): Double = 0.0
            }
        )
        state.pendingAction = FighterActionType.ATTACK

        val result = TurnResolver(turnDurationMs = 1000).resolveTurn(state, Instant.now())

        assertTrue(result.matchEvents.any { it is MatchEndedEvent })
        assertTrue(result.lifecycleEvents.any { it is TurnClosedEvent })
        assertTrue(result.lifecycleEvents.none { it is TurnOpenedEvent })
        assertEquals(true, result.matchEnded)
    }

    private fun baseState(
        activeFighterId: String,
        fighterAPos: Coordinate = Coordinate(0, 0),
        fighterBPos: Coordinate = Coordinate(6, 4),
        fighterAHp: Int = 100,
        fighterBHp: Int = 80
    ): MatchState {
        val a = FighterState(FighterProfiles.Balanced, fighterAHp, fighterAPos)
        val b = FighterState(FighterProfiles.GlassCannon, fighterBHp, fighterBPos)
        return MatchState(
            matchId = "m1",
            traceId = "t1",
            fighterA = a,
            fighterB = b,
            random = Random(42),
            actingFighterId = activeFighterId,
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
