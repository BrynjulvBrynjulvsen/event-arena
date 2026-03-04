package io.practicegroup.arena.fighter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.practicegroup.arena.domain.FighterActionCommand
import io.practicegroup.arena.domain.FighterActionType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

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
        if (event.path("eventType").asText() != "TurnOpened") {
            return
        }

        val payload = event.path("payload")
        val actingFighterId = payload.path("actingFighterId").asText()
        if (actingFighterId != fighterId) {
            return
        }

        val matchId = event.path("matchId").asText()
        val turn = event.path("turn").asInt()
        val actingPosition = payload.path("actingPosition").asInt()
        val targetPosition = payload.path("targetPosition").asInt()

        val action = decideAction(actingPosition, targetPosition)
        val command = FighterActionCommand(
            matchId = matchId,
            turn = turn,
            fighterId = fighterId,
            actionType = action
        )

        kafkaTemplate.send("$fighterId.match-actions.v1", matchId, command)
        log.info("fighter={} lifecycle={} matchId={} turn={} -> action={}", fighterId, lifecycleTopic, matchId, turn, action)
    }

    @KafkaListener(topics = ["\${arena.fighter.feedback-topic}"])
    fun onFeedback(record: ConsumerRecord<String, Any>) {
        val event = toJsonNode(record.value())
        val payload = event.path("payload")
        log.info(
            "fighter={} feedback status={} reason={} action={} turn={}",
            fighterId,
            payload.path("status").asText("unknown"),
            payload.path("reasonCode").asText("none"),
            payload.path("actionType").asText("none"),
            event.path("turn").asInt(-1)
        )
    }

    private fun decideAction(actingPosition: Int, targetPosition: Int): FighterActionType {
        val distance = kotlin.math.abs(actingPosition - targetPosition)
        if (distance <= 1) {
            return FighterActionType.ATTACK
        }
        return if (targetPosition > actingPosition) FighterActionType.MOVE_RIGHT else FighterActionType.MOVE_LEFT
    }

    private fun toJsonNode(value: Any): JsonNode {
        return when (value) {
            is JsonNode -> value
            is String -> objectMapper.readTree(value)
            else -> objectMapper.valueToTree(value)
        }
    }
}
