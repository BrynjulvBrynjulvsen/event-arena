package io.practicegroup.arena.engine

interface ArenaEventPublisher {
    fun publish(topic: String, key: String, message: Any)
}
