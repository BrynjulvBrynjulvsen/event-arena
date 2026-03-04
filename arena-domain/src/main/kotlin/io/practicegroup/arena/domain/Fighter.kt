package io.practicegroup.arena.domain

data class FighterProfile(
    val id: String,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val critChance: Double
)

data class FighterState(
    val profile: FighterProfile,
    val hp: Int,
    val position: Int
)

data class MatchSpec(
    val matchId: String,
    val fighterA: FighterProfile,
    val fighterB: FighterProfile,
    val seed: Long,
    val arenaMin: Int = 0,
    val arenaMax: Int = 9,
    val maxTurns: Int = 50
)

object FighterProfiles {
    val Balanced = FighterProfile(id = "balanced", maxHp = 100, attack = 18, defense = 14, speed = 12, critChance = 0.10)
    val Tank = FighterProfile(id = "tank", maxHp = 130, attack = 14, defense = 20, speed = 8, critChance = 0.05)
    val GlassCannon = FighterProfile(id = "glass-cannon", maxHp = 80, attack = 26, defense = 8, speed = 15, critChance = 0.18)
}
