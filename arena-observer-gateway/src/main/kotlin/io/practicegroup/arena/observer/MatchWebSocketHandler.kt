package io.practicegroup.arena.observer

import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class MatchWebSocketHandler(
    private val sessionRegistry: MatchSessionRegistry
) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val matchId = extractMatchId(session)
        if (matchId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Expected /ws/matches/{matchId}"))
            return
        }
        session.attributes["matchId"] = matchId
        sessionRegistry.register(matchId, session)
        session.sendMessage(TextMessage("{\"type\":\"connected\",\"matchId\":\"$matchId\"}"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        if (message.payload.equals("ping", ignoreCase = true)) {
            session.sendMessage(TextMessage("pong"))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val matchId = session.attributes["matchId"] as? String ?: return
        sessionRegistry.unregister(matchId, session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        val matchId = session.attributes["matchId"] as? String ?: return
        sessionRegistry.unregister(matchId, session)
    }

    private fun extractMatchId(session: WebSocketSession): String? {
        val path = session.uri?.path ?: return null
        return path.substringAfterLast('/').takeIf { it.isNotBlank() }
    }
}
