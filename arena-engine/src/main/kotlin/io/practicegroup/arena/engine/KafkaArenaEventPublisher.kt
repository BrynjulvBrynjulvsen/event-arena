package io.practicegroup.arena.engine

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaArenaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>
) : ArenaEventPublisher {
    override fun publish(topic: String, key: String, message: Any) {
        kafkaTemplate.send(topic, key, message).get()
    }
}
