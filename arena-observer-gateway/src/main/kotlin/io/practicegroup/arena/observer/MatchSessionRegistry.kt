package io.practicegroup.arena.observer

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Component
class MatchSessionRegistry {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sessionsByMatchId = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    fun register(matchId: String, session: WebSocketSession) {
        val sessions = sessionsByMatchId.computeIfAbsent(matchId) { ConcurrentHashMap.newKeySet() }
        sessions += session
        log.info("Observer session connected matchId={} openSessions={}", matchId, sessions.size)
    }

    fun unregister(matchId: String, session: WebSocketSession) {
        val sessions = sessionsByMatchId[matchId] ?: return
        sessions -= session
        if (sessions.isEmpty()) {
            sessionsByMatchId.remove(matchId)
        }
        log.info("Observer session disconnected matchId={} openSessions={}", matchId, sessions.size)
    }

    fun broadcast(matchId: String, payload: String) {
        val sessions = sessionsByMatchId[matchId] ?: return
        val closed = mutableListOf<WebSocketSession>()
        sessions.forEach { session ->
            if (!session.isOpen) {
                closed += session
                return@forEach
            }
            runCatching {
                session.sendMessage(TextMessage(payload))
            }.onFailure {
                closed += session
            }
        }
        closed.forEach { sessions -= it }
        if (sessions.isEmpty()) {
            sessionsByMatchId.remove(matchId)
        }
    }
}
