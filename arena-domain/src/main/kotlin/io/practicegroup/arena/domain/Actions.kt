package io.practicegroup.arena.domain

import java.time.Instant
import java.util.UUID

enum class FighterActionType {
    MOVE_UP,
    MOVE_DOWN,
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
    val targetEntityId: String? = null,
    val commandId: String = UUID.randomUUID().toString(),
    val sentAt: Instant = Instant.now()
)
