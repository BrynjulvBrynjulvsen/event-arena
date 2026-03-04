package io.practicegroup.arena.domain

data class FighterProfile(
    val id: String,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val critChance: Double,
    val attackRange: Int = 1,
    val regenPerTurn: Int = 0
)

data class Coordinate(
    val x: Int,
    val y: Int
)

data class FighterState(
    val profile: FighterProfile,
    val hp: Int,
    val position: Coordinate
)

data class MatchSpec(
    val matchId: String,
    val fighterA: FighterProfile,
    val fighterB: FighterProfile,
    val seed: Long,
    val boardWidth: Int = 7,
    val boardHeight: Int = 5,
    val maxTurns: Int = 50
)

object FighterProfiles {
    val Balanced = FighterProfile(
        id = "balanced",
        maxHp = 100,
        attack = 18,
        defense = 14,
        speed = 12,
        critChance = 0.10,
        attackRange = 1,
        regenPerTurn = 1
    )
    val Tank = FighterProfile(
        id = "tank",
        maxHp = 130,
        attack = 14,
        defense = 20,
        speed = 8,
        critChance = 0.05,
        attackRange = 1,
        regenPerTurn = 2
    )
    val GlassCannon = FighterProfile(
        id = "glass-cannon",
        maxHp = 80,
        attack = 26,
        defense = 8,
        speed = 15,
        critChance = 0.18,
        attackRange = 2,
        regenPerTurn = 0
    )
}
