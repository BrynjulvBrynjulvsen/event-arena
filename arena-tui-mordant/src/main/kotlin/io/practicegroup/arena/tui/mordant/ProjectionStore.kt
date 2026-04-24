package io.practicegroup.arena.tui.mordant

import java.time.Instant
import java.util.LinkedHashMap

class MatchProjectionStore(
    private val maxTrackedMatches: Int,
    private val maxBufferedEventsPerMatch: Int,
    private val defaultBoardWidth: Int = 7,
    private val defaultBoardHeight: Int = 5
) {
    private val records = LinkedHashMap<String, MatchRecord>()
    private var sequence = 0L

    fun ingest(event: ParsedArenaEvent, selectedMatchId: String?): MatchRecord {
        val current = records[event.matchId] ?: MatchRecord(
            summary = MatchSummary(matchId = event.matchId),
            liveDetail = MatchDetailState(
                matchId = event.matchId,
                boardWidth = defaultBoardWidth,
                boardHeight = defaultBoardHeight
            ),
            frames = emptyList()
        )

        val updatedDetail = reduce(current.liveDetail, event)
        val updatedSummary = buildSummary(updatedDetail)
        val frame = MatchFrame(sequence = ++sequence, detail = updatedDetail)
        val updatedRecord = MatchRecord(
            summary = updatedSummary,
            liveDetail = updatedDetail,
            frames = (current.frames + frame).takeLast(maxBufferedEventsPerMatch)
        )

        records[event.matchId] = updatedRecord
        evictIfNeeded(selectedMatchId)
        return records[event.matchId] ?: updatedRecord
    }

    fun snapshot(matchId: String?): MatchRecord? = matchId?.let(records::get)

    fun summaries(filter: String = ""): List<MatchSummary> {
        val query = filter.trim().lowercase()
        return records.values
            .map { it.summary }
            .filter { summary ->
                query.isBlank() ||
                    summary.matchId.lowercase().contains(query) ||
                    summary.fighters.any { it.lowercase().contains(query) } ||
                    summary.latestEvent.lowercase().contains(query)
            }
            .sortedByDescending { it.updatedAt }
    }

    private fun evictIfNeeded(selectedMatchId: String?) {
        while (records.size > maxTrackedMatches) {
            val evictable = records.values
                .filter { it.summary.status != MatchStatus.RUNNING && it.summary.matchId != selectedMatchId }
                .minByOrNull { it.summary.updatedAt }
                ?: records.values
                    .filter { it.summary.matchId != selectedMatchId }
                    .minByOrNull { it.summary.updatedAt }
                ?: break
            records.remove(evictable.summary.matchId)
        }
    }

    private fun buildSummary(detail: MatchDetailState): MatchSummary {
        val fighters = detail.entities.values
            .filter { it.type == "FIGHTER" }
            .map { it.id }
            .sorted()
        return MatchSummary(
            matchId = detail.matchId,
            status = detail.status,
            currentTurn = detail.currentTurn,
            fighters = fighters,
            latestEvent = detail.latestEvent,
            updatedAt = detail.updatedAt
        )
    }

    private fun reduce(current: MatchDetailState, event: ParsedArenaEvent): MatchDetailState {
        var next = current.copy(
            currentTurn = maxOf(current.currentTurn, event.turn),
            updatedAt = if (event.occurredAt == Instant.EPOCH) Instant.now() else event.occurredAt
        )

        fun appendLog(text: String) {
            next = next.copy(
                latestEvent = text,
                log = (next.log + MatchLogEntry(event.turn, text, next.updatedAt)).takeLast(maxBufferedEventsPerMatch)
            )
        }

        when (event) {
            is ParsedArenaEvent.MatchStarted -> {
                appendLog("turn ${event.turn}: match started")
                next = next.copy(status = MatchStatus.RUNNING)
            }

            is ParsedArenaEvent.TurnOpened -> {
                next = next.copy(
                    status = MatchStatus.RUNNING,
                    boardWidth = event.boardWidth ?: next.boardWidth,
                    boardHeight = event.boardHeight ?: next.boardHeight,
                    entities = if (event.visibleEntities.isEmpty()) next.entities else {
                        next.entities + event.visibleEntities.associate { entity ->
                            entity.entityId to entity.toEntityState()
                        }
                    }
                )
                appendLog("turn ${event.turn}: turn opened")
            }

            is ParsedArenaEvent.TurnClosed -> {
                appendLog("turn ${event.turn}: ${event.actingFighterId ?: "fighter"} chose ${event.selectedAction ?: "WAIT"}")
            }

            is ParsedArenaEvent.TurnStarted -> {
                appendLog("turn ${event.turn}: ${event.actingFighterId ?: "fighter"} acting")
            }

            is ParsedArenaEvent.EntitySpawned -> {
                next = next.copy(entities = next.entities + (event.entity.entityId to event.entity.toEntityState()))
                appendLog("turn ${event.turn}: spawn ${event.entity.entityType} ${event.entity.entityId}")
            }

            is ParsedArenaEvent.EntityRemoved -> {
                next = next.copy(entities = next.entities - event.entityId)
                appendLog("turn ${event.turn}: remove ${event.entityType ?: "entity"} ${event.entityId}")
            }

            is ParsedArenaEvent.FighterMoved -> {
                val entity = next.entities[event.fighterId] ?: EntityState(
                    id = event.fighterId,
                    type = "FIGHTER",
                    position = event.toPosition
                )
                next = next.copy(
                    entities = next.entities + (event.fighterId to entity.copy(position = event.toPosition))
                )
                appendLog("turn ${event.turn}: ${event.fighterId} moved to (${event.toPosition.x},${event.toPosition.y})")
            }

            is ParsedArenaEvent.DamageApplied -> {
                val entity = next.entities[event.fighterId]
                if (entity != null) {
                    next = next.copy(
                        entities = next.entities + (event.fighterId to entity.copy(hp = event.hpAfter))
                    )
                }
                appendLog("turn ${event.turn}: ${event.fighterId} hp ${event.hpAfter ?: "?"}")
            }

            is ParsedArenaEvent.ActionResolved -> {
                var entities = next.entities
                event.effects.forEach { effect ->
                    val targetId = effect.targetEntityId ?: return@forEach
                    val currentEntity = entities[targetId] ?: return@forEach
                    entities = when (effect.effectType) {
                        "HEAL" -> entities + (targetId to currentEntity.copy(
                            hp = listOfNotNull(currentEntity.hp, effect.amount).sum().takeIf { currentEntity.hp != null }
                        ))

                        "APPLIED_STATUS" -> entities + (targetId to currentEntity.copy(
                            statuses = currentEntity.statuses + (effect.metadata["status"] ?: "status")
                        ))

                        "REMOVED_STATUS" -> entities + (targetId to currentEntity.copy(
                            statuses = currentEntity.statuses - (effect.metadata["status"] ?: "")
                        ))

                        "MOVED" -> effect.toPosition?.let { pos ->
                            entities + (targetId to currentEntity.copy(position = pos))
                        } ?: entities

                        else -> entities
                    }
                }
                next = next.copy(entities = entities)
                appendLog("turn ${event.turn}: ${event.actorEntityId} -> ${event.actionType}")
            }

            is ParsedArenaEvent.MatchEnded -> {
                next = next.copy(
                    status = MatchStatus.ENDED,
                    winnerId = event.winnerFighterId,
                    loserId = event.loserFighterId
                )
                appendLog("turn ${event.turn}: ${event.winnerFighterId ?: "winner"} wins")
            }

            is ParsedArenaEvent.Unknown -> {
                appendLog("turn ${event.turn}: ${event.eventType}")
            }
        }

        return next
    }

    private fun SpawnedEntity.toEntityState(): EntityState {
        return EntityState(
            id = entityId,
            type = entityType,
            faction = faction,
            position = position,
            attributes = attributes,
            hp = attributes["hp"]?.toIntOrNull(),
            maxHp = attributes["maxHp"]?.toIntOrNull(),
            attackRange = attributes["range"]?.toIntOrNull(),
            attack = attributes["attack"]?.toIntOrNull(),
            defense = attributes["defense"]?.toIntOrNull(),
            speed = attributes["speed"]?.toIntOrNull(),
            regenPerTurn = attributes["regenPerTurn"]?.toIntOrNull()
        )
    }
}
