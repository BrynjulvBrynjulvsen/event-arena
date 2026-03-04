package io.practicegroup.arena.engine

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.practicegroup.arena.domain.AttackResolvedEvent
import io.practicegroup.arena.domain.AttackResolvedPayload
import io.practicegroup.arena.domain.DamageAppliedEvent
import io.practicegroup.arena.domain.DamageAppliedPayload
import io.practicegroup.arena.domain.EventFactory
import io.practicegroup.arena.domain.FighterActionCommand
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.FighterFeedbackEvent
import io.practicegroup.arena.domain.FighterFeedbackPayload
import io.practicegroup.arena.domain.FighterFeedbackStatus
import io.practicegroup.arena.domain.FighterMovedEvent
import io.practicegroup.arena.domain.FighterMovedPayload
import io.practicegroup.arena.domain.FighterProfile
import io.practicegroup.arena.domain.FighterProfiles
import io.practicegroup.arena.domain.FighterState
import io.practicegroup.arena.domain.MatchEndedEvent
import io.practicegroup.arena.domain.MatchEndedPayload
import io.practicegroup.arena.domain.MatchScheduledEvent
import io.practicegroup.arena.domain.MatchScheduledPayload
import io.practicegroup.arena.domain.MatchStartedEvent
import io.practicegroup.arena.domain.MatchStartedPayload
import io.practicegroup.arena.domain.TurnClosedEvent
import io.practicegroup.arena.domain.TurnClosedPayload
import io.practicegroup.arena.domain.TurnOpenedEvent
import io.practicegroup.arena.domain.TurnOpenedPayload
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random as KotlinRandom

@Service
class MatchOrchestrator(
    private val arenaEventPublisher: ArenaEventPublisher,
    private val objectMapper: ObjectMapper,
    @Value("\${arena.kafka.topic}") private val matchEventsTopic: String,
    @Value("\${arena.kafka.lifecycle-topic}") private val lifecycleTopic: String,
    @Value("\${arena.turn.duration-ms:1000}") private val turnDurationMs: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clock: Clock = Clock.systemUTC()
    private val matches = ConcurrentHashMap<String, RunningMatch>()

    fun startMatch(request: StartMatchRequest): StartMatchResponse {
        val matchId = UUID.randomUUID().toString()
        val traceId = UUID.randomUUID().toString()
        val seed = request.seed ?: KotlinRandom.nextLong()
        val fighterA = request.fighterA ?: FighterProfiles.Balanced
        val fighterB = request.fighterB ?: FighterProfiles.GlassCannon
        val random = Random(seed)

        val fighterAState = FighterState(fighterA, fighterA.maxHp, 0)
        val fighterBState = FighterState(fighterB, fighterB.maxHp, 9)
        val startsWithA = when {
            fighterA.speed > fighterB.speed -> true
            fighterB.speed > fighterA.speed -> false
            else -> random.nextBoolean()
        }
        val firstFighter = if (startsWithA) fighterA.id else fighterB.id

        val match = RunningMatch(
            matchId = matchId,
            traceId = traceId,
            fighterA = fighterAState,
            fighterB = fighterBState,
            random = random,
            actingFighterId = firstFighter,
            turn = 1,
            deadline = now().plusMillis(turnDurationMs)
        )
        matches[matchId] = match

        publishLifecycleEvent(
            MatchScheduledEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = matchId,
                traceId = traceId,
                payload = MatchScheduledPayload(fighterA.id, fighterB.id, seed)
            )
        )

        publishLifecycleEvent(
            MatchStartedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = matchId,
                traceId = traceId,
                payload = MatchStartedPayload(
                    fighterAId = fighterA.id,
                    fighterBId = fighterB.id,
                    fighterAStartHp = fighterAState.hp,
                    fighterBStartHp = fighterBState.hp,
                    fighterAStartPosition = fighterAState.position,
                    fighterBStartPosition = fighterBState.position,
                    startsWithFighterId = firstFighter,
                    seed = seed
                )
            )
        )

        publishTurnOpened(match)

        return StartMatchResponse(
            matchId = matchId,
            status = "RUNNING",
            firstTurnFighterId = firstFighter,
            turnDurationMs = turnDurationMs
        )
    }

    @KafkaListener(topicPattern = "\${arena.kafka.action-topic-pattern}", groupId = "arena-engine")
    fun onFighterAction(record: ConsumerRecord<String, Any>) {
        val command = parseActionCommand(record.value()) ?: return
        val match = matches[command.matchId]
        if (match == null) {
            publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.INVALID_MOVE, command.actionType, "MATCH_NOT_FOUND")
            return
        }

        synchronized(match) {
            if (match.ended) {
                publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.TOO_LATE, command.actionType, "MATCH_ENDED")
                return
            }
            if (command.turn != match.turn) {
                publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.WRONG_TURN, command.actionType, "TURN_MISMATCH")
                return
            }
            if (command.fighterId != match.actingFighterId) {
                publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.WRONG_TURN, command.actionType, "NOT_ACTIVE_FIGHTER")
                return
            }
            if (now().isAfter(match.deadline)) {
                publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.TOO_LATE, command.actionType, "TURN_CLOSED")
                return
            }

            val reason = validateAction(match, command)
            if (reason != null) {
                publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.INVALID_MOVE, command.actionType, reason)
                return
            }

            match.pendingAction = command.actionType
            publishFeedback(command.matchId, command.turn, command.fighterId, FighterFeedbackStatus.MOVE_ACCEPTED, command.actionType, null)
        }
    }

    @Scheduled(fixedDelayString = "\${arena.turn.tick-check-ms:100}")
    fun resolveExpiredTurns() {
        val instant = now()
        matches.values.forEach { match ->
            synchronized(match) {
                if (!match.ended && !instant.isBefore(match.deadline)) {
                    executeTurn(match)
                }
            }
        }
    }

    private fun executeTurn(match: RunningMatch) {
        val actor = match.activeFighter()
        val defender = match.otherFighter()
        val action = match.pendingAction ?: FighterActionType.WAIT
        match.pendingAction = null

        when (action) {
            FighterActionType.MOVE_LEFT -> {
                val toPosition = (actor.position - 1).coerceAtLeast(match.arenaMin)
                if (toPosition != actor.position) {
                    setActorState(match, actor.copy(position = toPosition))
                    publishMatchEvent(
                        FighterMovedEvent(
                            eventId = EventFactory.eventId(),
                            occurredAt = now(),
                            matchId = match.matchId,
                            turn = match.turn,
                            traceId = match.traceId,
                            payload = FighterMovedPayload(actor.profile.id, actor.position, toPosition)
                        )
                    )
                }
            }

            FighterActionType.MOVE_RIGHT -> {
                val toPosition = (actor.position + 1).coerceAtMost(match.arenaMax)
                if (toPosition != actor.position) {
                    setActorState(match, actor.copy(position = toPosition))
                    publishMatchEvent(
                        FighterMovedEvent(
                            eventId = EventFactory.eventId(),
                            occurredAt = now(),
                            matchId = match.matchId,
                            turn = match.turn,
                            traceId = match.traceId,
                            payload = FighterMovedPayload(actor.profile.id, actor.position, toPosition)
                        )
                    )
                }
            }

            FighterActionType.ATTACK -> {
                val hit = distance(actor.position, defender.position) <= 1 && match.random.nextDouble() >= 0.1
                val criticalHit = hit && match.random.nextDouble() < actor.profile.critChance
                val damage = if (hit) calculateDamage(actor.profile, defender.profile, criticalHit, match.random) else 0

                publishMatchEvent(
                    AttackResolvedEvent(
                        eventId = EventFactory.eventId(),
                        occurredAt = now(),
                        matchId = match.matchId,
                        turn = match.turn,
                        traceId = match.traceId,
                        payload = AttackResolvedPayload(actor.profile.id, defender.profile.id, hit, criticalHit, damage)
                    )
                )

                if (damage > 0) {
                    val hpAfter = (defender.hp - damage).coerceAtLeast(0)
                    publishMatchEvent(
                        DamageAppliedEvent(
                            eventId = EventFactory.eventId(),
                            occurredAt = now(),
                            matchId = match.matchId,
                            turn = match.turn,
                            traceId = match.traceId,
                            payload = DamageAppliedPayload(defender.profile.id, defender.hp, hpAfter)
                        )
                    )
                    setDefenderState(match, defender.copy(hp = hpAfter))
                }
            }

            FighterActionType.WAIT -> {
            }
        }

        publishLifecycleEvent(
            TurnClosedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = match.matchId,
                turn = match.turn,
                traceId = match.traceId,
                payload = TurnClosedPayload(
                    actingFighterId = actor.profile.id,
                    selectedAction = action,
                    actionSource = if (action == FighterActionType.WAIT) "DEFAULT" else "FIGHTER"
                )
            )
        )

        val currentActor = match.activeFighter()
        val currentDefender = match.otherFighter()
        publishFeedback(
            match.matchId,
            match.turn,
            currentActor.profile.id,
            FighterFeedbackStatus.TURN_RESULTS,
            action,
            "HP=${currentActor.hp};POS=${currentActor.position}"
        )
        publishFeedback(
            match.matchId,
            match.turn,
            currentDefender.profile.id,
            FighterFeedbackStatus.TURN_RESULTS,
            null,
            "HP=${currentDefender.hp};POS=${currentDefender.position}"
        )

        val loser = listOf(match.fighterA, match.fighterB).firstOrNull { it.hp <= 0 }
        if (loser != null) {
            val winner = listOf(match.fighterA, match.fighterB).first { it.profile.id != loser.profile.id }
            endMatch(match, winner, loser, "KNOCKOUT")
            return
        }

        if (match.turn >= match.maxTurns) {
            val winner = if (match.fighterA.hp >= match.fighterB.hp) match.fighterA else match.fighterB
            val loserByHp = if (winner.profile.id == match.fighterA.profile.id) match.fighterB else match.fighterA
            endMatch(match, winner, loserByHp, "TIMEOUT")
            return
        }

        match.turn += 1
        match.actingFighterId = if (match.actingFighterId == match.fighterA.profile.id) match.fighterB.profile.id else match.fighterA.profile.id
        match.deadline = now().plusMillis(turnDurationMs)
        publishTurnOpened(match)
    }

    private fun endMatch(match: RunningMatch, winner: FighterState, loser: FighterState, reason: String) {
        match.ended = true
        publishMatchEvent(
            MatchEndedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = match.matchId,
                turn = match.turn,
                traceId = match.traceId,
                payload = MatchEndedPayload(
                    winnerFighterId = winner.profile.id,
                    loserFighterId = loser.profile.id,
                    reason = reason,
                    winnerHpRemaining = winner.hp,
                    loserHpRemaining = loser.hp
                )
            )
        )
        matches.remove(match.matchId)
    }

    private fun publishTurnOpened(match: RunningMatch) {
        val actor = match.activeFighter()
        val defender = match.otherFighter()
        publishLifecycleEvent(
            TurnOpenedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = match.matchId,
                turn = match.turn,
                traceId = match.traceId,
                payload = TurnOpenedPayload(
                    actingFighterId = actor.profile.id,
                    targetFighterId = defender.profile.id,
                    actingPosition = actor.position,
                    targetPosition = defender.position,
                    turnDurationMs = turnDurationMs
                )
            )
        )
    }

    private fun publishMatchEvent(event: io.practicegroup.arena.domain.ArenaEvent) {
        arenaEventPublisher.publish(matchEventsTopic, event.matchId, event)
        log.info("Published match event type={} matchId={} turn={}", event.eventType, event.matchId, event.turn)
    }

    private fun publishLifecycleEvent(event: io.practicegroup.arena.domain.ArenaEvent) {
        arenaEventPublisher.publish(lifecycleTopic, event.matchId, event)
        log.info("Published lifecycle event type={} matchId={} turn={}", event.eventType, event.matchId, event.turn)
    }

    private fun publishFeedback(
        matchId: String,
        turn: Int,
        fighterId: String,
        status: FighterFeedbackStatus,
        actionType: FighterActionType?,
        reasonCode: String?
    ) {
        val event = FighterFeedbackEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now(),
            matchId = matchId,
            turn = turn,
            traceId = matchId,
            payload = FighterFeedbackPayload(
                fighterId = fighterId,
                status = status,
                actionType = actionType,
                reasonCode = reasonCode,
                details = null
            )
        )
        arenaEventPublisher.publish("$fighterId.feedback.v1", matchId, event)
    }

    private fun parseActionCommand(raw: Any): FighterActionCommand? {
        return runCatching {
            val node = when (raw) {
                is JsonNode -> raw
                is String -> objectMapper.readTree(raw)
                else -> objectMapper.valueToTree(raw)
            }
            FighterActionCommand(
                matchId = node.path("matchId").asText(),
                turn = node.path("turn").asInt(),
                fighterId = node.path("fighterId").asText(),
                actionType = FighterActionType.valueOf(node.path("actionType").asText("WAIT"))
            )
        }.onFailure { ex ->
            log.warn("Ignoring action command due to parse error type={}", raw::class.qualifiedName, ex)
        }.getOrNull()?.takeIf { it.matchId.isNotBlank() && it.fighterId.isNotBlank() }
    }

    private fun validateAction(match: RunningMatch, command: FighterActionCommand): String? {
        val actor = match.activeFighter()
        return when (command.actionType) {
            FighterActionType.MOVE_LEFT -> if (actor.position <= match.arenaMin) "OUT_OF_BOUNDS" else null
            FighterActionType.MOVE_RIGHT -> if (actor.position >= match.arenaMax) "OUT_OF_BOUNDS" else null
            FighterActionType.ATTACK,
            FighterActionType.WAIT -> null
        }
    }

    private fun setActorState(match: RunningMatch, state: FighterState) {
        if (state.profile.id == match.fighterA.profile.id) {
            match.fighterA = state
        } else {
            match.fighterB = state
        }
    }

    private fun setDefenderState(match: RunningMatch, state: FighterState) {
        setActorState(match, state)
    }

    private fun calculateDamage(
        attacker: FighterProfile,
        defender: FighterProfile,
        criticalHit: Boolean,
        random: Random
    ): Int {
        val base = (attacker.attack - defender.defense / 2.0).coerceAtLeast(1.0)
        val roll = 0.85 + (random.nextDouble() * 0.30)
        val critMultiplier = if (criticalHit) 2.0 else 1.0
        return (base * roll * critMultiplier).roundToInt().coerceAtLeast(1)
    }

    private fun distance(a: Int, b: Int): Int = abs(a - b)

    private fun now(): Instant = clock.instant()

    private data class RunningMatch(
        val matchId: String,
        val traceId: String,
        var fighterA: FighterState,
        var fighterB: FighterState,
        val random: Random,
        var actingFighterId: String,
        var turn: Int,
        var deadline: Instant,
        var pendingAction: FighterActionType? = null,
        var ended: Boolean = false,
        val maxTurns: Int = 50,
        val arenaMin: Int = 0,
        val arenaMax: Int = 9
    ) {
        fun activeFighter(): FighterState = if (fighterA.profile.id == actingFighterId) fighterA else fighterB
        fun otherFighter(): FighterState = if (fighterA.profile.id == actingFighterId) fighterB else fighterA
    }
}
