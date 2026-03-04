package io.practicegroup.arena.fighter

import io.practicegroup.arena.domain.FighterActionCommand
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.Coordinate
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class FighterBot(
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    @Value("\${arena.fighter.id}") private val fighterId: String,
    @Value("\${arena.kafka.lifecycle-topic}") private val lifecycleTopic: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${arena.kafka.lifecycle-topic}"])
    fun onLifecycle(record: ConsumerRecord<String, Any>) {
        val event = toJsonNode(record.value())
        if (event.path("eventType").asString() != "TurnOpened") {
            return
        }

        val payload = event.path("payload")
        val actingFighterId = payload.path("actingFighterId").asString()
        if (actingFighterId != fighterId) {
            return
        }

        val matchId = event.path("matchId").asString()
        val turn = event.path("turn").asInt()
        val actingPosition = readCoordinate(payload.path("actingPosition"))
        val targetPosition = readCoordinate(payload.path("targetPosition"))
        if (actingPosition == null || targetPosition == null) {
            log.warn("fighter={} missing coordinate data for matchId={} turn={}", fighterId, matchId, turn)
            return
        }
        val actorAttackRange = payload.path("actorAttackRange").asInt(1)
        val turnDurationMs = payload.path("turnDurationMs").asLong(1000)

        val action = decideAction(actingPosition, targetPosition, actorAttackRange)
        val command = FighterActionCommand(
            matchId = matchId,
            turn = turn,
            fighterId = fighterId,
            actionType = action
        )

        sendAction(command)
        Thread.startVirtualThread {
            Thread.sleep((turnDurationMs / 3).coerceIn(100, 400))
            sendAction(command)
        }
        log.info("fighter={} lifecycle={} matchId={} turn={} -> action={}", fighterId, lifecycleTopic, matchId, turn, action)
    }

    @KafkaListener(topics = ["\${arena.fighter.feedback-topic}"])
    fun onFeedback(record: ConsumerRecord<String, Any>) {
        val event = toJsonNode(record.value())
        val payload = event.path("payload")
        val entityChanges = payload.path("entityChanges")
        log.info(
            "fighter={} feedback status={} reason={} action={} turn={} actorEntityId={} worldVersion={} entityChanges={}",
            fighterId,
            payload.path("status").asString("unknown"),
            payload.path("reasonCode").asString("none"),
            payload.path("actionType").asString("none"),
            event.path("turn").asInt(-1),
            payload.path("actorEntityId").asString("none"),
            payload.path("worldVersion").asLong(-1),
            if (entityChanges.isArray) entityChanges.size() else 0
        )
    }

    private fun decideAction(actingPosition: Coordinate, targetPosition: Coordinate, attackRange: Int): FighterActionType {
        val distance =
            kotlin.math.abs(actingPosition.x - targetPosition.x) + kotlin.math.abs(actingPosition.y - targetPosition.y)
        if (distance <= attackRange) {
            return FighterActionType.ATTACK
        }

        val dx = targetPosition.x - actingPosition.x
        val dy = targetPosition.y - actingPosition.y
        return if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
            if (dx > 0) FighterActionType.MOVE_RIGHT else FighterActionType.MOVE_LEFT
        } else {
            if (dy > 0) FighterActionType.MOVE_DOWN else FighterActionType.MOVE_UP
        }
    }

    private fun toJsonNode(value: Any): JsonNode {
        return when (value) {
            is JsonNode -> value
            is String -> objectMapper.readTree(value)
            else -> objectMapper.valueToTree(value)
        }
    }

    private fun sendAction(command: FighterActionCommand) {
        kafkaTemplate.send("$fighterId.match-actions.v1", command.matchId, command)
    }

    private fun readCoordinate(node: JsonNode): Coordinate? {
        if (node.isMissingNode || node.isNull) {
            return null
        }
        return Coordinate(
            x = node.path("x").asInt(),
            y = node.path("y").asInt()
        )
    }
}
