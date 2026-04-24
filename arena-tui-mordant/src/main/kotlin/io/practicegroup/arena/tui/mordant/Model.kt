package io.practicegroup.arena.tui.mordant

import java.time.Instant

enum class MatchStatus {
    SCHEDULED,
    RUNNING,
    ENDED
}

enum class FocusPane {
    MATCH_LIST,
    BOARD,
    EVENT_LOG;

    fun next(): FocusPane = entries[(ordinal + 1) % entries.size]

    fun previous(): FocusPane = entries[(ordinal + entries.size - 1) % entries.size]
}

data class Point(
    val x: Int,
    val y: Int
)

data class BoardViewport(
    val x: Int = 0,
    val y: Int = 0
) {
    fun clamp(boardWidth: Int, boardHeight: Int, visibleWidth: Int, visibleHeight: Int): BoardViewport {
        val maxX = (boardWidth - visibleWidth).coerceAtLeast(0)
        val maxY = (boardHeight - visibleHeight).coerceAtLeast(0)
        return BoardViewport(
            x = x.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY)
        )
    }
}

data class EntityState(
    val id: String,
    val type: String,
    val faction: String? = null,
    val position: Point,
    val attributes: Map<String, String> = emptyMap(),
    val hp: Int? = null,
    val maxHp: Int? = null,
    val attackRange: Int? = null,
    val attack: Int? = null,
    val defense: Int? = null,
    val speed: Int? = null,
    val regenPerTurn: Int? = null,
    val statuses: Set<String> = emptySet()
)

data class MatchLogEntry(
    val turn: Int,
    val text: String,
    val occurredAt: Instant
)

data class MatchSummary(
    val matchId: String,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val currentTurn: Int = 0,
    val fighters: List<String> = emptyList(),
    val latestEvent: String = "waiting for events",
    val updatedAt: Instant = Instant.EPOCH
)

data class MatchDetailState(
    val matchId: String,
    val boardWidth: Int = 7,
    val boardHeight: Int = 5,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val currentTurn: Int = 0,
    val entities: Map<String, EntityState> = emptyMap(),
    val log: List<MatchLogEntry> = emptyList(),
    val winnerId: String? = null,
    val loserId: String? = null,
    val latestEvent: String = "waiting for events",
    val updatedAt: Instant = Instant.EPOCH
)

data class MatchFrame(
    val sequence: Long,
    val detail: MatchDetailState
)

data class MatchRecord(
    val summary: MatchSummary,
    val liveDetail: MatchDetailState,
    val frames: List<MatchFrame>
)

sealed interface ParsedArenaEvent {
    val matchId: String
    val turn: Int
    val occurredAt: Instant
    val eventType: String

    data class MatchStarted(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val fighterAId: String?,
        val fighterBId: String?
    ) : ParsedArenaEvent {
        override val eventType: String = "MatchStarted"
    }

    data class TurnOpened(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val boardWidth: Int?,
        val boardHeight: Int?,
        val visibleEntities: List<SpawnedEntity>
    ) : ParsedArenaEvent {
        override val eventType: String = "TurnOpened"
    }

    data class TurnClosed(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val actingFighterId: String?,
        val selectedAction: String?
    ) : ParsedArenaEvent {
        override val eventType: String = "TurnClosed"
    }

    data class TurnStarted(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val actingFighterId: String?
    ) : ParsedArenaEvent {
        override val eventType: String = "TurnStarted"
    }

    data class EntitySpawned(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val entity: SpawnedEntity
    ) : ParsedArenaEvent {
        override val eventType: String = "EntitySpawned"
    }

    data class EntityRemoved(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val entityId: String,
        val entityType: String?,
        val reason: String?,
        val position: Point?
    ) : ParsedArenaEvent {
        override val eventType: String = "EntityRemoved"
    }

    data class FighterMoved(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val fighterId: String,
        val toPosition: Point
    ) : ParsedArenaEvent {
        override val eventType: String = "FighterMoved"
    }

    data class DamageApplied(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val fighterId: String,
        val hpAfter: Int?
    ) : ParsedArenaEvent {
        override val eventType: String = "DamageApplied"
    }

    data class ActionResolved(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val actorEntityId: String,
        val actionType: String,
        val effects: List<ActionEffectView>
    ) : ParsedArenaEvent {
        override val eventType: String = "ActionResolved"
    }

    data class MatchEnded(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        val winnerFighterId: String?,
        val loserFighterId: String?
    ) : ParsedArenaEvent {
        override val eventType: String = "MatchEnded"
    }

    data class Unknown(
        override val matchId: String,
        override val turn: Int,
        override val occurredAt: Instant,
        override val eventType: String,
        val description: String
    ) : ParsedArenaEvent
}

data class SpawnedEntity(
    val entityId: String,
    val entityType: String,
    val faction: String?,
    val position: Point,
    val attributes: Map<String, String>
)

data class ActionEffectView(
    val effectType: String,
    val targetEntityId: String? = null,
    val amount: Int? = null,
    val fromPosition: Point? = null,
    val toPosition: Point? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ViewSnapshot(
    val summaries: List<MatchSummary>,
    val selectedMatchId: String?,
    val liveRecord: MatchRecord?,
    val visibleFrame: MatchFrame?,
    val replayControlsActive: Boolean,
    val visibleFrameIndex: Int?,
    val totalFrameCount: Int,
    val atFirstFrame: Boolean,
    val atLastFrame: Boolean,
    val focusPane: FocusPane,
    val autoFollow: Boolean,
    val paused: Boolean,
    val replaySpeed: Double,
    val filterText: String,
    val filterMode: Boolean,
    val helpVisible: Boolean,
    val viewport: BoardViewport
)
