package io.practicegroup.arena.engine

import io.practicegroup.arena.domain.FighterProfile

data class StartMatchRequest(
    val seed: Long? = null,
    val fighterA: FighterProfile? = null,
    val fighterB: FighterProfile? = null
)

data class StartMatchResponse(
    val matchId: String,
    val status: String,
    val firstTurnFighterId: String,
    val turnDurationMs: Long
)
