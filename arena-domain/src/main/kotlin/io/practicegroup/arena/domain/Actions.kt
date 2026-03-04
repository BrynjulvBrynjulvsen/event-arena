package io.practicegroup.arena.domain

import java.time.Instant

enum class FighterActionType {
    MOVE_LEFT,
    MOVE_RIGHT,
    ATTACK,
    WAIT
}

data class FighterActionCommand(
    val matchId: String,
    val turn: Int,
    val fighterId: String,
    val actionType: FighterActionType,
    val sentAt: Instant = Instant.now()
)
