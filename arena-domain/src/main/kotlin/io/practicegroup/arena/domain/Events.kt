package io.practicegroup.arena.domain

import java.time.Instant
import java.util.UUID

interface ArenaEvent {
    val eventId: String
    val eventType: String
    val schemaVersion: Int
    val occurredAt: Instant
    val matchId: String
    val turn: Int
    val traceId: String
    val causationId: String?
}

data class MatchScheduledEvent(
    override val eventId: String,
    override val eventType: String = "MatchScheduled",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int = 0,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: MatchScheduledPayload
) : ArenaEvent

data class MatchStartedEvent(
    override val eventId: String,
    override val eventType: String = "MatchStarted",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int = 0,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: MatchStartedPayload
) : ArenaEvent

data class TurnStartedEvent(
    override val eventId: String,
    override val eventType: String = "TurnStarted",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: TurnStartedPayload
) : ArenaEvent

data class FighterMovedEvent(
    override val eventId: String,
    override val eventType: String = "FighterMoved",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: FighterMovedPayload
) : ArenaEvent

data class AttackResolvedEvent(
    override val eventId: String,
    override val eventType: String = "AttackResolved",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: AttackResolvedPayload
) : ArenaEvent

data class DamageAppliedEvent(
    override val eventId: String,
    override val eventType: String = "DamageApplied",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: DamageAppliedPayload
) : ArenaEvent

data class MatchEndedEvent(
    override val eventId: String,
    override val eventType: String = "MatchEnded",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: MatchEndedPayload
) : ArenaEvent

data class TurnOpenedEvent(
    override val eventId: String,
    override val eventType: String = "TurnOpened",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: TurnOpenedPayload
) : ArenaEvent

data class TurnClosedEvent(
    override val eventId: String,
    override val eventType: String = "TurnClosed",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: TurnClosedPayload
) : ArenaEvent

data class ActionResolvedEvent(
    override val eventId: String,
    override val eventType: String = "ActionResolved",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: ActionResolvedPayload
) : ArenaEvent

data class FighterFeedbackEvent(
    override val eventId: String,
    override val eventType: String = "FighterFeedback",
    override val schemaVersion: Int = 1,
    override val occurredAt: Instant,
    override val matchId: String,
    override val turn: Int,
    override val traceId: String,
    override val causationId: String? = null,
    val payload: FighterFeedbackPayload
) : ArenaEvent

data class MatchScheduledPayload(
    val fighterAId: String,
    val fighterBId: String,
    val seed: Long,
    val rulesetVersion: String = "v1"
)

data class MatchStartedPayload(
    val fighterAId: String,
    val fighterBId: String,
    val fighterAStartHp: Int,
    val fighterBStartHp: Int,
    val fighterAStartPosition: Coordinate,
    val fighterBStartPosition: Coordinate,
    val startsWithFighterId: String,
    val seed: Long,
    val rulesetVersion: String = "v1"
)

data class TurnStartedPayload(
    val actingFighterId: String,
    val actingPosition: Coordinate,
    val targetFighterId: String,
    val targetPosition: Coordinate
)

data class FighterMovedPayload(
    val fighterId: String,
    val fromPosition: Coordinate,
    val toPosition: Coordinate
)

data class AttackResolvedPayload(
    val attackerId: String,
    val defenderId: String,
    val hit: Boolean,
    val criticalHit: Boolean,
    val damage: Int
)

data class DamageAppliedPayload(
    val fighterId: String,
    val hpBefore: Int,
    val hpAfter: Int
)

data class MatchEndedPayload(
    val winnerFighterId: String,
    val loserFighterId: String,
    val reason: String,
    val winnerHpRemaining: Int,
    val loserHpRemaining: Int
)

data class TurnOpenedPayload(
    val actingFighterId: String,
    val targetFighterId: String,
    val actingPosition: Coordinate,
    val targetPosition: Coordinate,
    val turnDurationMs: Long,
    val boardWidth: Int,
    val boardHeight: Int,
    val actorAttackRange: Int,
    val visibleEntities: List<EntitySnapshot> = emptyList()
)

data class TurnClosedPayload(
    val actingFighterId: String,
    val selectedAction: FighterActionType,
    val actionSource: String
)

data class EntitySnapshot(
    val entityId: String,
    val entityType: ArenaEntityType,
    val faction: String? = null,
    val position: Coordinate,
    val attributes: Map<String, String> = emptyMap()
)

enum class ArenaEntityType {
    FIGHTER,
    PROJECTILE,
    ITEM,
    COVER,
    TERRAIN,
    OBSTACLE
}

enum class ActionOutcome {
    SUCCESS,
    FAILED,
    NO_OP
}

enum class ActionEffectType {
    MOVED,
    DAMAGE,
    HEAL,
    APPLIED_STATUS,
    REMOVED_STATUS,
    SPAWNED,
    DESPAWNED
}

data class ActionEffect(
    val effectType: ActionEffectType,
    val targetEntityId: String? = null,
    val targetEntityType: ArenaEntityType? = null,
    val targetFaction: String? = null,
    val amount: Int? = null,
    val critical: Boolean? = null,
    val fromPosition: Coordinate? = null,
    val toPosition: Coordinate? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ActionResolvedPayload(
    val actorEntityId: String,
    val actorEntityType: ArenaEntityType,
    val actorFaction: String? = null,
    val actionType: FighterActionType,
    val outcome: ActionOutcome,
    val effects: List<ActionEffect> = emptyList()
)

enum class FighterFeedbackStatus {
    MOVE_ACCEPTED,
    INVALID_MOVE,
    TOO_LATE,
    WRONG_TURN,
    TURN_RESULTS
}

enum class EntityChangeType {
    MOVED,
    ATTRIBUTE,
    STATE,
    SPAWNED,
    REMOVED
}

data class EntityChange(
    val entityId: String,
    val entityType: ArenaEntityType,
    val faction: String? = null,
    val changeType: EntityChangeType,
    val position: Coordinate? = null,
    val attributes: Map<String, String> = emptyMap()
)

data class FighterFeedbackPayload(
    val fighterId: String,
    val status: FighterFeedbackStatus,
    val actionType: FighterActionType? = null,
    val reasonCode: String? = null,
    val actorEntityId: String? = null,
    val worldVersion: Long? = null,
    val entityChanges: List<EntityChange> = emptyList()
)

object EventFactory {
    fun eventId(): String = UUID.randomUUID().toString()
}
