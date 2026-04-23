package io.practicegroup.arena.tui.mordant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class EventParserTest {
    private val parser = ArenaEventParser(ObjectMapper())

    @Test
    fun `parser tolerates current payload shapes and string attributes`() {
        val event = parser.parse(
            "match-1",
            """
            {
              "eventType": "EntitySpawned",
              "turn": 0,
              "occurredAt": "2026-01-01T00:00:00Z",
              "payload": {
                "entityId": "balanced",
                "entityType": "FIGHTER",
                "faction": "BLUE",
                "position": { "x": 1, "y": 2 },
                "attributes": { "hp": "20", "maxHp": "20", "range": "2" }
              }
            }
            """.trimIndent()
        ) as ParsedArenaEvent.EntitySpawned

        assertEquals("balanced", event.entity.entityId)
        assertEquals("20", event.entity.attributes["hp"])
    }

    @Test
    fun `parser ignores malformed records`() {
        assertNull(parser.parse(null, """{"eventType":"EntitySpawned"}"""))
        assertNull(parser.parse("match-1", """{"eventType":"EntitySpawned","payload":{}}"""))
    }
}
