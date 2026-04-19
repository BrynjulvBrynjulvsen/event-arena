package io.practicegroup.arena.tui

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MatchTuiConsumer(
    private val objectMapper: ObjectMapper,
    @Value("\${arena.tui.board-width:7}") private val boardWidthDefault: Int,
    @Value("\${arena.tui.board-height:5}") private val boardHeightDefault: Int,
    @Value("\${arena.tui.match-id:}") private val configuredMatchId: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stateByMatch = ConcurrentHashMap<String, RenderState>()
    @Volatile
    private var activeMatchId: String? = configuredMatchId.ifBlank { null }

    @KafkaListener(topics = ["\${arena.kafka.topic}"])
    fun onEvent(record: ConsumerRecord<String, Any>) {
        runCatching {
            val matchId = record.key() ?: return
            if (activeMatchId == null) {
                activeMatchId = matchId
            }
            if (activeMatchId != matchId) {
                return
            }

            val event = toJson(record.value())
            val eventType = event.path("eventType").asString("unknown")
            val turn = event.path("turn").asInt(-1)
            val payload = event.path("payload")

            val state = stateByMatch.computeIfAbsent(matchId) {
                RenderState(
                    width = boardWidthDefault,
                    height = boardHeightDefault
                )
            }

            when (eventType) {
                "EntitySpawned" -> {
                    val entityId = payload.path("entityId").asString()
                    val type = payload.path("entityType").asString()
                    val faction = payload.path("faction").asString(null)
                    val pos = readPos(payload.path("position")) ?: return
                    val hp = payload.path("attributes").path("hp").asInt(-1).takeIf { it >= 0 }
                    val maxHp = payload.path("attributes").path("maxHp").asInt(-1).takeIf { it >= 0 }
                    val range = payload.path("attributes").path("range").asInt(-1).takeIf { it >= 0 }
                    val attack = payload.path("attributes").path("attack").asInt(-1).takeIf { it >= 0 }
                    val defense = payload.path("attributes").path("defense").asInt(-1).takeIf { it >= 0 }
                    val speed = payload.path("attributes").path("speed").asInt(-1).takeIf { it >= 0 }
                    val regen = payload.path("attributes").path("regenPerTurn").asInt(-1).takeIf { it >= 0 }
                    state.entities[entityId] = RenderEntity(
                        id = entityId,
                        type = type,
                        faction = faction,
                        position = pos,
                        hp = hp,
                        maxHp = maxHp,
                        attackRange = range,
                        attack = attack,
                        defense = defense,
                        speed = speed,
                        regenPerTurn = regen
                    )
                    state.updateBounds(pos)
                    state.lastEvent = "Turn $turn: spawn $type $entityId"
                }

                "EntityRemoved" -> {
                    val entityId = payload.path("entityId").asString()
                    val type = payload.path("entityType").asString("unknown")
                    val reason = payload.path("reason").asString("removed")
                    state.entities.remove(entityId)
                    state.lastEvent = "Turn $turn: remove $type $entityId ($reason)"
                }

                "FighterMoved" -> {
                    val fighterId = payload.path("fighterId").asString()
                    val to = readPos(payload.path("toPosition")) ?: return
                    state.entities[fighterId]?.position = to
                    state.updateBounds(to)
                    state.lastEvent = "Turn $turn: $fighterId moved to (${to.x},${to.y})"
                }

                "DamageApplied" -> {
                    val fighterId = payload.path("fighterId").asString()
                    val hpAfter = payload.path("hpAfter").asInt(-1)
                    if (hpAfter >= 0) {
                        state.entities[fighterId]?.hp = hpAfter
                    }
                    state.lastEvent = "Turn $turn: $fighterId hp=$hpAfter"
                }

                "ActionResolved" -> {
                    val actor = payload.path("actorEntityId").asString("unknown")
                    val action = payload.path("actionType").asString("unknown")
                    val effects = payload.path("effects")
                    if (effects.isArray) {
                        effects.forEach {
                            val effectType = it.path("effectType").asString()
                            val targetId = it.path("targetEntityId").asString(null)
                            if (effectType == "DAMAGE") {
                                val target = state.entities[targetId]
                                if (target != null) {
                                    state.flashX = Flash(target.position, Instant.now().plusMillis(350))
                                }
                            } else if (effectType == "HEAL") {
                                val amount = it.path("amount").asInt(0)
                                if (targetId != null && amount > 0) {
                                    val target = state.entities[targetId]
                                    if (target != null && target.hp != null) {
                                        val max = target.maxHp ?: Int.MAX_VALUE
                                        target.hp = (target.hp!! + amount).coerceAtMost(max)
                                    }
                                }
                            } else if (effectType == "APPLIED_STATUS") {
                                val status = it.path("metadata").path("status").asString("")
                                if (targetId != null && status == "ATTACK_BOOST_5") {
                                    state.entities[targetId]?.attackBuff = (state.entities[targetId]?.attackBuff ?: 0) + 5
                                }
                            }
                        }
                    }
                    state.lastEvent = "Turn $turn: $actor -> $action"
                }

                "MatchEnded" -> {
                    val loser = payload.path("loserFighterId").asString()
                    val winner = payload.path("winnerFighterId").asString()
                    state.skullEntityId = loser
                    state.lastEvent = "Turn $turn: $winner wins, $loser KO"
                }

                else -> {
                    state.lastEvent = "Turn $turn: $eventType"
                }
            }

            render(matchId, state)
        }.onFailure { ex ->
            log.error("Failed to process TUI event", ex)
        }
    }

    private fun render(matchId: String, state: RenderState) {
        val width = state.width
        val height = state.height
        val flash = state.flashX?.takeIf { Instant.now().isBefore(it.until) }
        if (flash == null) {
            state.flashX = null
        }

        print("\u001B[H\u001B[2J")
        println("Event Arena TUI  |  match=$matchId")
        println("${state.lastEvent}")
        println()

        for (y in 0 until height) {
            val row = StringBuilder()
            for (x in 0 until width) {
                val pos = Pos(x, y)
                val glyph = when {
                    state.skullEntityId != null && state.entities[state.skullEntityId]?.position == pos -> "💀"
                    flash?.position == pos -> "\u001B[31m❌\u001B[0m"
                    else -> glyphAt(state, pos)
                }
                row.append(toCell(glyph))
            }
            println(row.toString())
        }

        println()
        renderFighterStats(state)
        println()
        println("Legend: 🔵 blue fighter  🔴 red fighter  🛡️ cover  🧪 item  ❌ hit  💀 KO")
        println("Press Ctrl+C to exit")
    }

    private fun renderFighterStats(state: RenderState) {
        println("Fighter Stats")
        val fighters = state.entities.values
            .filter { it.type == "FIGHTER" }
            .sortedWith(compareBy<RenderEntity>({ it.faction ?: "" }, { it.id }))

        if (fighters.isEmpty()) {
            println("  (waiting for fighters to spawn)")
            return
        }

        fighters.forEach { fighter ->
            val hpPart = if (fighter.hp != null && fighter.maxHp != null) {
                "HP ${fighter.hp}/${fighter.maxHp}"
            } else {
                "HP ?"
            }
            val statPart = listOfNotNull(
                fighter.attack?.let { "ATK $it" },
                fighter.defense?.let { "DEF $it" },
                fighter.speed?.let { "SPD $it" },
                fighter.attackRange?.let { "RNG $it" },
                fighter.regenPerTurn?.let { "REG $it" },
                fighter.attackBuff.takeIf { it != 0 }?.let { "BUFF +$it" }
            ).joinToString("  ")
            println("  ${fighterGlyph(fighter)} ${fighter.id} (${fighter.faction ?: "NEUTRAL"})  $hpPart  $statPart")
        }
    }

    private fun fighterGlyph(entity: RenderEntity): String {
        return if (entity.faction == "BLUE") "🔵" else "🔴"
    }

    private fun glyphAt(state: RenderState, pos: Pos): String {
        val entity = state.entities.values.firstOrNull { it.position == pos } ?: return "·"
        return when (entity.type) {
            "FIGHTER" -> if (entity.faction == "BLUE") "🔵" else "🔴"
            "COVER" -> "🛡️"
            "ITEM" -> "🧪"
            else -> "⬜"
        }
    }

    private fun toCell(glyph: String): String {
        val visible = stripAnsi(glyph)
        val wide = isWideGlyph(visible)
        return if (wide) glyph else "$glyph "
    }

    private fun stripAnsi(value: String): String {
        return value.replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }

    private fun isWideGlyph(glyph: String): Boolean {
        return glyph in setOf("🔵", "🔴", "🛡️", "🧪", "💀", "❌", "⬜")
    }

    private fun toJson(value: Any): JsonNode {
        return when (value) {
            is JsonNode -> value
            is String -> objectMapper.readTree(value)
            else -> objectMapper.valueToTree(value)
        }
    }

    private fun readPos(node: JsonNode): Pos? {
        if (node.isMissingNode || node.isNull) {
            return null
        }
        return Pos(node.path("x").asInt(), node.path("y").asInt())
    }
}

private data class Pos(
    val x: Int,
    val y: Int
)

private data class RenderEntity(
    val id: String,
    val type: String,
    val faction: String?,
    var position: Pos,
    var hp: Int?,
    var maxHp: Int? = null,
    var attackRange: Int? = null,
    var attack: Int? = null,
    var defense: Int? = null,
    var speed: Int? = null,
    var regenPerTurn: Int? = null,
    var attackBuff: Int = 0
)

private data class Flash(
    val position: Pos,
    val until: Instant
)

private data class RenderState(
    var width: Int,
    var height: Int,
    val entities: MutableMap<String, RenderEntity> = mutableMapOf(),
    var flashX: Flash? = null,
    var skullEntityId: String? = null,
    var lastEvent: String = "Waiting for events..."
) {
    fun updateBounds(pos: Pos) {
        width = maxOf(width, pos.x + 1)
        height = maxOf(height, pos.y + 1)
    }
}
