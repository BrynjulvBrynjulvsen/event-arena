package io.practicegroup.arena.replay

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

@Component
class ReplayConsumer(
    private val mapper: ObjectMapper,
    @Value("\${arena.replay.checkpoint-file:.demo/replay-checkpoint.json}") checkpointFilePath: String,
    @Value("\${arena.replay.max-event-id-cache:20000}") private val maxEventIdCache: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stateLock = Any()
    private val checkpointFile = Path.of(checkpointFilePath)
    private val offsetsByPartition = ConcurrentHashMap<String, Long>()
    private val processedEventIds = LinkedHashSet<String>()
    private val matchHp = ConcurrentHashMap<String, MutableMap<String, Int>>()

    init {
        val checkpoint = loadCheckpointState()
        offsetsByPartition.putAll(checkpoint.offsetsByPartition)
        processedEventIds.addAll(checkpoint.processedEventIds.takeLast(maxEventIdCache))
        checkpoint.matchHp.forEach { (matchId, fighters) ->
            matchHp[matchId] = ConcurrentHashMap(fighters)
        }
        log.info(
            "Loaded replay checkpoint offsets={} cachedEventIds={} trackedMatches={} file={}",
            offsetsByPartition.size,
            processedEventIds.size,
            matchHp.size,
            checkpointFile
        )
    }

    @KafkaListener(topics = ["\${arena.kafka.topic}"])
    fun onEvent(record: ConsumerRecord<String, Any>) {
        runCatching {
            val key = record.key() ?: "no-key"
            val partitionKey = partitionKey(record)
            val event = toJsonNode(record.value())
            val eventId = event.path("eventId").asString("")
            val eventType = event.path("eventType").asString("unknown")
            val turn = event.path("turn").asInt(-1)
            val payload = event.path("payload")

            synchronized(stateLock) {
                val checkpointOffset = offsetsByPartition[partitionKey]
                if (checkpointOffset != null && record.offset() <= checkpointOffset) {
                    return
                }
                if (eventId.isNotBlank() && processedEventIds.contains(eventId)) {
                    offsetsByPartition[partitionKey] = record.offset()
                    saveCheckpointState()
                    log.debug("[{}] skip duplicate eventId={} turn={}", key, eventId, turn)
                    return
                }

                when (eventType) {
                    "DamageApplied" -> {
                        val fighterId = payload.path("fighterId").asString()
                        val hpAfter = payload.path("hpAfter").asInt(Int.MIN_VALUE)
                        if (fighterId.isNotBlank() && hpAfter != Int.MIN_VALUE) {
                            val state = matchHp.computeIfAbsent(key) { ConcurrentHashMap() }
                            state[fighterId] = hpAfter
                            log.info("[{}] turn={} damage -> {} hp={}", key, turn, fighterId, hpAfter)
                        }
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

                if (eventId.isNotBlank()) {
                    rememberEventId(eventId)
                }
                offsetsByPartition[partitionKey] = record.offset()
                saveCheckpointState()
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

    private fun partitionKey(record: ConsumerRecord<String, Any>): String {
        return "${record.topic()}:${record.partition()}"
    }

    private fun rememberEventId(eventId: String) {
        processedEventIds += eventId
        while (processedEventIds.size > maxEventIdCache) {
            val iterator = processedEventIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            } else {
                break
            }
        }
    }

    private fun loadCheckpointState(): ReplayCheckpointState {
        if (!Files.exists(checkpointFile)) {
            return ReplayCheckpointState()
        }
        return runCatching {
            mapper.readValue(Files.readString(checkpointFile), ReplayCheckpointState::class.java)
        }.onFailure {
            log.warn("Failed to load replay checkpoint file={} starting fresh", checkpointFile, it)
        }.getOrDefault(ReplayCheckpointState())
    }

    private fun saveCheckpointState() {
        val snapshot = ReplayCheckpointState(
            offsetsByPartition = offsetsByPartition.toMap(),
            processedEventIds = processedEventIds.toList(),
            matchHp = matchHp.mapValues { (_, fighters) -> fighters.toMap() }
        )
        val parent = checkpointFile.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val tempFile = checkpointFile.resolveSibling("${checkpointFile.fileName}.tmp")
        Files.writeString(tempFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot))
        Files.move(tempFile, checkpointFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

private data class ReplayCheckpointState(
    val offsetsByPartition: Map<String, Long> = emptyMap(),
    val processedEventIds: List<String> = emptyList(),
    val matchHp: Map<String, Map<String, Int>> = emptyMap()
)
