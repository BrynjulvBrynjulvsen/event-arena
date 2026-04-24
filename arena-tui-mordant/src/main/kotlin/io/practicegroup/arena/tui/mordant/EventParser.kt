package io.practicegroup.arena.tui.mordant

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class ArenaEventParser(
    private val objectMapper: ObjectMapper
) {
    fun parse(matchId: String?, value: Any?): ParsedArenaEvent? {
        if (matchId.isNullOrBlank() || value == null) return null
        val event = toJsonNode(value)
        val eventType = event.path("eventType").asText("").ifBlank { "unknown" }
        val turn = event.path("turn").coerceInt() ?: 0
        val occurredAt = event.path("occurredAt").asText(null)?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        } ?: Instant.EPOCH
        val payload = event.path("payload")

        return when (eventType) {
            "MatchStarted" -> ParsedArenaEvent.MatchStarted(
                matchId = matchId,
                turn = turn,
                occurredAt = occurredAt,
                fighterAId = payload.path("fighterAId").asNullableText(),
                fighterBId = payload.path("fighterBId").asNullableText()
            )

            "TurnOpened" -> ParsedArenaEvent.TurnOpened(
                matchId = matchId,
                turn = turn,
                occurredAt = occurredAt,
                boardWidth = payload.path("boardWidth").coerceInt(),
                boardHeight = payload.path("boardHeight").coerceInt(),
                visibleEntities = payload.path("visibleEntities").mapArray(::readSpawnedEntity)
            )

            "TurnClosed" -> ParsedArenaEvent.TurnClosed(
                matchId = matchId,
                turn = turn,
                occurredAt = occurredAt,
                actingFighterId = payload.path("actingFighterId").asNullableText(),
                selectedAction = payload.path("selectedAction").asNullableText()
            )

            "TurnStarted" -> ParsedArenaEvent.TurnStarted(
                matchId = matchId,
                turn = turn,
                occurredAt = occurredAt,
                actingFighterId = payload.path("actingFighterId").asNullableText()
            )

            "EntitySpawned" -> {
                val entity = readSpawnedEntity(payload) ?: return null
                ParsedArenaEvent.EntitySpawned(matchId, turn, occurredAt, entity)
            }

            "EntityRemoved" -> {
                val entityId = payload.path("entityId").asNullableText() ?: return null
                ParsedArenaEvent.EntityRemoved(
                    matchId = matchId,
                    turn = turn,
                    occurredAt = occurredAt,
                    entityId = entityId,
                    entityType = payload.path("entityType").asNullableText(),
                    reason = payload.path("reason").asNullableText(),
                    position = readPoint(payload.path("position"))
                )
            }

            "FighterMoved" -> {
                val fighterId = payload.path("fighterId").asNullableText() ?: return null
                val toPosition = readPoint(payload.path("toPosition")) ?: return null
                ParsedArenaEvent.FighterMoved(matchId, turn, occurredAt, fighterId, toPosition)
            }

            "DamageApplied" -> {
                val fighterId = payload.path("fighterId").asNullableText() ?: return null
                ParsedArenaEvent.DamageApplied(matchId, turn, occurredAt, fighterId, payload.path("hpAfter").coerceInt())
            }

            "ActionResolved" -> {
                val actorEntityId = payload.path("actorEntityId").asNullableText() ?: return null
                ParsedArenaEvent.ActionResolved(
                    matchId = matchId,
                    turn = turn,
                    occurredAt = occurredAt,
                    actorEntityId = actorEntityId,
                    actionType = payload.path("actionType").asText("unknown"),
                    effects = payload.path("effects").mapArray { effect ->
                        ActionEffectView(
                            effectType = effect.path("effectType").asText("unknown"),
                            targetEntityId = effect.path("targetEntityId").asNullableText(),
                            amount = effect.path("amount").coerceInt(),
                            fromPosition = readPoint(effect.path("fromPosition")),
                            toPosition = readPoint(effect.path("toPosition")),
                            metadata = effect.path("metadata").toStringMap()
                        )
                    }
                )
            }

            "MatchEnded" -> ParsedArenaEvent.MatchEnded(
                matchId = matchId,
                turn = turn,
                occurredAt = occurredAt,
                winnerFighterId = payload.path("winnerFighterId").asNullableText(),
                loserFighterId = payload.path("loserFighterId").asNullableText()
            )

            else -> ParsedArenaEvent.Unknown(
                matchId = matchId,
                turn = turn,
                occurredAt = occurredAt,
                eventType = eventType,
                description = payload.toString()
            )
        }
    }

    private fun toJsonNode(value: Any): JsonNode {
        return when (value) {
            is JsonNode -> value
            is String -> objectMapper.readTree(value)
            else -> objectMapper.valueToTree(value)
        }
    }

    private fun readSpawnedEntity(node: JsonNode): SpawnedEntity? {
        val entityId = node.path("entityId").asNullableText() ?: return null
        val entityType = node.path("entityType").asNullableText() ?: return null
        val position = readPoint(node.path("position")) ?: return null
        return SpawnedEntity(
            entityId = entityId,
            entityType = entityType,
            faction = node.path("faction").asNullableText(),
            position = position,
            attributes = node.path("attributes").toStringMap()
        )
    }

    private fun readPoint(node: JsonNode): Point? {
        if (node.isMissingNode || node.isNull) return null
        val x = node.path("x").coerceInt() ?: return null
        val y = node.path("y").coerceInt() ?: return null
        return Point(x, y)
    }

    private fun JsonNode.asNullableText(): String? {
        if (isMissingNode || isNull) return null
        val value = asText("")
        return value.ifBlank { null }
    }

    private fun JsonNode.coerceInt(): Int? {
        return when {
            isInt || isLong -> asInt()
            isTextual -> asText().toIntOrNull()
            else -> null
        }
    }

    private fun JsonNode.toStringMap(): Map<String, String> {
        if (!isObject) return emptyMap()
        return properties().asSequence().associate { entry -> entry.key to entry.value.asText() }
    }

    private fun <T> JsonNode.mapArray(transform: (JsonNode) -> T?): List<T> {
        if (!isArray) return emptyList()
        return mapNotNull(transform)
    }
}
