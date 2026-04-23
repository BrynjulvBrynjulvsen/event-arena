package io.practicegroup.arena.observer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class KafkaEventStreamConsumer(
    private val objectMapper: ObjectMapper,
    private val sessionRegistry: MatchSessionRegistry,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val broadcastedEventsCounter =
        Counter.builder("arena.observer.events.broadcasted")
            .description("Events successfully broadcasted to observer sessions")
            .register(meterRegistry)
    private val broadcastFailuresCounter =
        Counter.builder("arena.observer.events.broadcast.failures")
            .description("Observer event broadcast failures")
            .register(meterRegistry)
    private val missingKeyCounter =
        Counter.builder("arena.observer.events.missing.match.id")
            .description("Consumed records dropped because matchId key was missing")
            .register(meterRegistry)

    @KafkaListener(topics = [$$"${arena.kafka.match-events-topic}", $$"${arena.kafka.lifecycle-topic}"])
    fun onEvent(record: ConsumerRecord<String, Any>) {
        val topic = record.topic()
        meterRegistry.counter("arena.observer.events.consumed", "topic", topic).increment()
        val matchId = record.key()
        if (matchId == null) {
            missingKeyCounter.increment()
            return
        }
        runCatching {
            val streamEvent = objectMapper.createObjectNode().apply {
                put("topic", topic)
                put("partition", record.partition())
                put("offset", record.offset())
                put("timestamp", record.timestamp())
                set("event", toJsonNode(record.value()))
            }
            sessionRegistry.broadcast(matchId, objectMapper.writeValueAsString(streamEvent))
            broadcastedEventsCounter.increment()
        }.onFailure { ex ->
            broadcastFailuresCounter.increment()
            log.warn("Failed to broadcast observer stream event topic={} matchId={}", topic, matchId, ex)
        }
    }

    private fun toJsonNode(value: Any): JsonNode {
        return when (value) {
            is JsonNode -> value
            is String -> objectMapper.readTree(value)
            else -> objectMapper.valueToTree(value)
        }
    }
}
