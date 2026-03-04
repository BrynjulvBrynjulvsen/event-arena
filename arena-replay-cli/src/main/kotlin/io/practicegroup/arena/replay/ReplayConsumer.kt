package io.practicegroup.arena.replay

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class ReplayConsumer(
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val matchHp = ConcurrentHashMap<String, MutableMap<String, Int>>()

    @KafkaListener(topics = ["\${arena.kafka.topic}"])
    fun onEvent(record: ConsumerRecord<String, Any>) {
        runCatching {
            val key = record.key() ?: "no-key"
            val event = toJsonNode(record.value())
            val eventType = event.path("eventType").asString("unknown")
            val turn = event.path("turn").asInt(-1)
            val payload = event.path("payload")

            when (eventType) {
                "DamageApplied" -> {
                    val fighterId = payload.path("fighterId").asString()
                    val hpAfter = payload.path("hpAfter").asInt(Int.MIN_VALUE)
                    if (fighterId.isBlank() || hpAfter == Int.MIN_VALUE) return
                    val state = matchHp.computeIfAbsent(key) { ConcurrentHashMap() }
                    state[fighterId] = hpAfter
                    log.info("[{}] turn={} damage -> {} hp={}", key, turn, fighterId, hpAfter)
                }

                "FighterMoved" -> {
                    val fighterId = payload.path("fighterId").asString("unknown")
                    val from = payload.path("fromPosition")
                    val to = payload.path("toPosition")
                    log.info(
                        "[{}] turn={} move -> {} from=({}, {}) to=({}, {})",
                        key,
                        turn,
                        fighterId,
                        from.path("x").asInt(-1),
                        from.path("y").asInt(-1),
                        to.path("x").asInt(-1),
                        to.path("y").asInt(-1)
                    )
                }

                "AttackResolved" -> {
                    val attacker = payload.path("attackerId").asString("unknown")
                    val defender = payload.path("defenderId").asString("unknown")
                    val damage = payload.path("damage").asInt(0)
                    val critical = payload.path("criticalHit").asBoolean(false)
                    log.info("[{}] turn={} attack {} -> {} damage={} crit={}", key, turn, attacker, defender, damage, critical)
                }

                "MatchEnded" -> {
                    val winner = payload.path("winnerFighterId").asString("unknown")
                    val reason = payload.path("reason").asString("unknown")
                    log.info("[{}] finished winner={} reason={} hp={}", key, winner, reason, matchHp[key])
                }

                "TurnClosed" -> {
                    val acting = payload.path("actingFighterId").asString("unknown")
                    val action = payload.path("selectedAction").asString("unknown")
                    val source = payload.path("actionSource").asString("unknown")
                    log.info("[{}] turn={} closed acting={} action={} source={}", key, turn, acting, action, source)
                }

                "ActionResolved" -> {
                    val actor = payload.path("actorEntityId").asString("unknown")
                    val actorType = payload.path("actorEntityType").asString("unknown")
                    val action = payload.path("actionType").asString("unknown")
                    val outcome = payload.path("outcome").asString("unknown")
                    val effects = payload.path("effects")
                    val typedTargets =
                        if (effects.isArray) {
                            effects.map {
                                "${it.path("targetEntityType").asString("none")}:${it.path("targetEntityId").asString("none")}"
                            }
                        } else {
                            emptyList()
                        }
                    log.info(
                        "[{}] turn={} resolved actor={}({}) action={} outcome={} effects={} effectsRaw={}",
                        key,
                        turn,
                        actor,
                        actorType,
                        action,
                        outcome,
                        typedTargets,
                        effects
                    )
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
