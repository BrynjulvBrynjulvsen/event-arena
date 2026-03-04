package io.practicegroup.arena.replay

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ReplayConsumer {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val matchHp = ConcurrentHashMap<String, MutableMap<String, Int>>()

    @KafkaListener(topics = ["\${arena.kafka.topic}"])
    fun onEvent(record: ConsumerRecord<String, Any>) {
        runCatching {
            val key = record.key() ?: "no-key"
            val event = toJsonNode(record.value())
            val eventType = event.path("eventType").asText("unknown")
            val turn = event.path("turn").asInt(-1)
            val payload = event.path("payload")

            when (eventType) {
                "DamageApplied" -> {
                    val fighterId = payload.path("fighterId").asText()
                    val hpAfter = payload.path("hpAfter").asInt(Int.MIN_VALUE)
                    if (fighterId.isBlank() || hpAfter == Int.MIN_VALUE) return
                    val state = matchHp.computeIfAbsent(key) { ConcurrentHashMap() }
                    state[fighterId] = hpAfter
                    log.info("[{}] turn={} damage -> {} hp={}", key, turn, fighterId, hpAfter)
                }

                "FighterMoved" -> {
                    val fighterId = payload.path("fighterId").asText("unknown")
                    val toPosition = payload.path("toPosition").asInt(-1)
                    log.info("[{}] turn={} move -> {} to={}", key, turn, fighterId, toPosition)
                }

                "AttackResolved" -> {
                    val attacker = payload.path("attackerId").asText("unknown")
                    val defender = payload.path("defenderId").asText("unknown")
                    val damage = payload.path("damage").asInt(0)
                    val critical = payload.path("criticalHit").asBoolean(false)
                    log.info("[{}] turn={} attack {} -> {} damage={} crit={}", key, turn, attacker, defender, damage, critical)
                }

                "MatchEnded" -> {
                    val winner = payload.path("winnerFighterId").asText("unknown")
                    val reason = payload.path("reason").asText("unknown")
                    log.info("[{}] finished winner={} reason={} hp={}", key, winner, reason, matchHp[key])
                }

                else -> log.info(
                    "[{}] turn={} event={} valueType={}",
                    key,
                    turn,
                    eventType,
                    record.value()::class.qualifiedName
                )
            }
        }.onFailure { ex ->
            log.error(
                "Failed to parse replay record topic={} partition={} offset={} key={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                ex
            )
        }
    }

    private fun toJsonNode(value: Any): JsonNode {
        return when (value) {
            is JsonNode -> value
            is String -> mapper.readTree(value)
            else -> mapper.valueToTree(value)
        }
    }
}
