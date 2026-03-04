package io.practicegroup.arena.engine.logic

import io.practicegroup.arena.domain.Coordinate
import io.practicegroup.arena.domain.FighterActionCommand
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.FighterFeedbackStatus
import java.time.Instant
import kotlin.math.abs

data class ValidationIssue(
    val status: FighterFeedbackStatus,
    val reasonCode: String
)

class ActionValidator {
    fun validateCommand(state: MatchState, command: FighterActionCommand, now: Instant): ValidationIssue? {
        if (state.ended) {
            return ValidationIssue(FighterFeedbackStatus.TOO_LATE, "MATCH_ENDED")
        }
        if (command.turn != state.turn) {
            return ValidationIssue(FighterFeedbackStatus.WRONG_TURN, "TURN_MISMATCH")
        }
        if (command.fighterId != state.actingFighterId) {
            return ValidationIssue(FighterFeedbackStatus.WRONG_TURN, "NOT_ACTIVE_FIGHTER")
        }
        if (now.isAfter(state.deadline)) {
            return ValidationIssue(FighterFeedbackStatus.TOO_LATE, "TURN_CLOSED")
        }

        val actor = state.activeFighter()
        val target = movementTarget(actor.position, command.actionType)
        return when (command.actionType) {
            FighterActionType.MOVE_UP,
            FighterActionType.MOVE_DOWN,
            FighterActionType.MOVE_LEFT,
            FighterActionType.MOVE_RIGHT -> {
                if (target == null) {
                    ValidationIssue(FighterFeedbackStatus.INVALID_MOVE, "INVALID_MOVE")
                } else if (!isInBounds(target, state)) {
                    ValidationIssue(FighterFeedbackStatus.INVALID_MOVE, "OUT_OF_BOUNDS")
                } else if (target == state.otherFighter().position) {
                    ValidationIssue(FighterFeedbackStatus.INVALID_MOVE, "CELL_OCCUPIED")
                } else if (isCoverAt(state, target)) {
                    ValidationIssue(FighterFeedbackStatus.INVALID_MOVE, "CELL_BLOCKED_BY_COVER")
                } else {
                    null
                }
            }

            FighterActionType.ATTACK -> {
                val defender = state.otherFighter()
                if (distance(actor.position, defender.position) > actor.profile.attackRange) {
                    ValidationIssue(FighterFeedbackStatus.INVALID_MOVE, "OUT_OF_RANGE")
                } else {
                    null
                }
            }

            FighterActionType.WAIT -> null
        }
    }

    private fun movementTarget(position: Coordinate, action: FighterActionType): Coordinate? {
        return when (action) {
            FighterActionType.MOVE_UP -> position.copy(y = position.y - 1)
            FighterActionType.MOVE_DOWN -> position.copy(y = position.y + 1)
            FighterActionType.MOVE_LEFT -> position.copy(x = position.x - 1)
            FighterActionType.MOVE_RIGHT -> position.copy(x = position.x + 1)
            else -> null
        }
    }

    private fun isInBounds(position: Coordinate, state: MatchState): Boolean {
        return position.x in 0..<state.boardWidth && position.y in 0..<state.boardHeight
    }

    private fun isCoverAt(state: MatchState, position: Coordinate): Boolean {
        return state.coverEntities.values.any { it.position == position }
    }

    private fun distance(a: Coordinate, b: Coordinate): Int = abs(a.x - b.x) + abs(a.y - b.y)
}
