package io.practicegroup.arena.tui

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class MatchTuiConsumerTest {

    @Test
    fun `auto-follow tui switches to a newly started match`() {
        val consumer = MatchTuiConsumer(
            objectMapper = ObjectMapper(),
            boardWidthDefault = 7,
            boardHeightDefault = 5,
            configuredMatchId = ""
        )

        consumer.onMatchEvent(
            record(
                topic = "arena.match-events.v1",
                matchId = "match-1",
                payload = """
                    {
                      "eventType": "EntitySpawned",
                      "turn": 0,
                      "payload": {
                        "entityId": "balanced",
                        "entityType": "FIGHTER",
                        "faction": "BLUE",
                        "position": { "x": 0, "y": 0 },
                        "attributes": { "hp": 20, "maxHp": 20 }
                      }
                    }
                """.trimIndent()
            )
        )

        consumer.onLifecycleEvent(
            record(
                topic = "arena.match-lifecycle.v1",
                matchId = "match-2",
                payload = """
                    {
                      "eventType": "MatchStarted",
                      "turn": 0,
                      "payload": {}
                    }
                """.trimIndent()
            )
        )

        consumer.onMatchEvent(
            record(
                topic = "arena.match-events.v1",
                matchId = "match-1",
                payload = """
                    {
                      "eventType": "EntitySpawned",
                      "turn": 0,
                      "payload": {
                        "entityId": "glass-cannon",
                        "entityType": "FIGHTER",
                        "faction": "RED",
                        "position": { "x": 6, "y": 4 },
                        "attributes": { "hp": 14, "maxHp": 14 }
                      }
                    }
                """.trimIndent()
            )
        )

        consumer.onMatchEvent(
            record(
                topic = "arena.match-events.v1",
                matchId = "match-2",
                payload = """
                    {
                      "eventType": "EntitySpawned",
                      "turn": 0,
                      "payload": {
                        "entityId": "balanced",
                        "entityType": "FIGHTER",
                        "faction": "BLUE",
                        "position": { "x": 0, "y": 0 },
                        "attributes": { "hp": 20, "maxHp": 20 }
                      }
                    }
                """.trimIndent()
            )
        )

        assertEquals("match-2", consumer.currentMatchId())
        assertEquals(setOf("match-2"), consumer.trackedMatchIds())
        assertEquals(setOf("balanced"), consumer.entityIds("match-2"))
        assertTrue(consumer.entityIds("match-1").isEmpty())
    }

    @Test
    fun `configured match id stays pinned when another match starts`() {
        val consumer = MatchTuiConsumer(
            objectMapper = ObjectMapper(),
            boardWidthDefault = 7,
            boardHeightDefault = 5,
            configuredMatchId = "match-1"
        )

        consumer.onLifecycleEvent(
            record(
                topic = "arena.match-lifecycle.v1",
                matchId = "match-2",
                payload = """
                    {
                      "eventType": "MatchStarted",
                      "turn": 0,
                      "payload": {}
                    }
                """.trimIndent()
            )
        )

        assertEquals("match-1", consumer.currentMatchId())
        assertTrue(consumer.trackedMatchIds().isEmpty())
    }

    private fun record(topic: String, matchId: String, payload: String): ConsumerRecord<String, Any> {
        return ConsumerRecord(topic, 0, 0L, matchId, payload)
    }
}
