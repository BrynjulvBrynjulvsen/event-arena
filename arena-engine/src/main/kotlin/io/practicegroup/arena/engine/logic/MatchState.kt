package io.practicegroup.arena.engine.logic

import io.practicegroup.arena.domain.Coordinate
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.FighterState
import java.time.Instant
import java.util.Random

data class MatchState(
    val matchId: String,
    val traceId: String,
    var fighterA: FighterState,
    var fighterB: FighterState,
    val random: Random,
    var actingFighterId: String,
    var turn: Int,
    var deadline: Instant,
    var pendingAction: FighterActionType? = null,
    var ended: Boolean = false,
    var worldVersion: Long,
    val coverEntities: MutableMap<String, CoverEntity>,
    val pickupEntities: MutableMap<String, PickupEntity>,
    val attackBuffByFighter: MutableMap<String, Int>,
    val factionByFighter: MutableMap<String, String>,
    val maxTurns: Int = 50,
    val boardWidth: Int,
    val boardHeight: Int
) {
    fun activeFighter(): FighterState = if (fighterA.profile.id == actingFighterId) fighterA else fighterB

    fun otherFighter(): FighterState = if (fighterA.profile.id == actingFighterId) fighterB else fighterA

    fun setFighterState(state: FighterState) {
        if (fighterA.profile.id == state.profile.id) {
            fighterA = state
        } else {
            fighterB = state
        }
    }

    fun fighterById(fighterId: String): FighterState = if (fighterA.profile.id == fighterId) fighterA else fighterB

    fun factionForEntity(entityId: String): String = factionByFighter[entityId] ?: "NEUTRAL"
}

data class CoverEntity(
    val entityId: String,
    val position: Coordinate
)

data class PickupEntity(
    val entityId: String,
    val kind: PickupKind,
    val position: Coordinate
)

enum class PickupKind {
    HEAL_20,
    ATTACK_BOOST_5
}
