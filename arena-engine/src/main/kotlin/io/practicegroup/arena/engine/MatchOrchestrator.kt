package io.practicegroup.arena.engine

import io.practicegroup.arena.domain.ArenaEvent
import io.practicegroup.arena.domain.Coordinate
import io.practicegroup.arena.domain.EventFactory
import io.practicegroup.arena.domain.FighterActionCommand
import io.practicegroup.arena.domain.FighterFeedbackEvent
import io.practicegroup.arena.domain.FighterFeedbackPayload
import io.practicegroup.arena.domain.FighterFeedbackStatus
import io.practicegroup.arena.domain.FighterProfiles
import io.practicegroup.arena.domain.FighterState
import io.practicegroup.arena.domain.MatchScheduledEvent
import io.practicegroup.arena.domain.MatchScheduledPayload
import io.practicegroup.arena.domain.MatchStartedEvent
import io.practicegroup.arena.domain.MatchStartedPayload
import io.practicegroup.arena.engine.logic.ActionValidator
import io.practicegroup.arena.engine.logic.MatchState
import io.practicegroup.arena.engine.logic.TurnResolver
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random as KotlinRandom

@Service
class MatchOrchestrator(
    private val arenaEventPublisher: ArenaEventPublisher,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    @Value($$"${arena.kafka.topic}") private val matchEventsTopic: String,
    @Value($$"${arena.kafka.lifecycle-topic}") private val lifecycleTopic: String,
    @Value($$"${arena.turn.duration-ms:1000}") private val turnDurationMs: Long,
    @Value($$"${arena.board.width:7}") private val boardWidth: Int,
    @Value($$"${arena.board.height:5}") private val boardHeight: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clock: Clock = Clock.systemUTC()
    private val matches = ConcurrentHashMap<String, MatchState>()
    private val actionValidator = ActionValidator()
    private val turnResolver = TurnResolver(turnDurationMs)
    private val turnResolutionTimer =
        Timer.builder("arena.turn.resolution.latency")
            .description("Turn resolution duration in the engine")
            .register(meterRegistry)
    private val commandsAcceptedCounter =
        Counter.builder("arena.fighter.commands.accepted")
            .description("Accepted fighter commands")
            .register(meterRegistry)
    private val malformedCommandsCounter =
        Counter.builder("arena.fighter.commands.malformed")
            .description("Malformed fighter commands rejected before validation")
            .register(meterRegistry)

    init {
        Gauge.builder("arena.matches.active") { matches.size.toDouble() }
            .description("Active matches currently tracked by engine")
            .register(meterRegistry)
    }

    fun startMatch(request: StartMatchRequest): StartMatchResponse {
        val matchId = UUID.randomUUID().toString()
        val traceId = UUID.randomUUID().toString()
        val seed = request.seed ?: KotlinRandom.nextLong()
        val fighterA = request.fighterA ?: FighterProfiles.Balanced
        val fighterB = request.fighterB ?: FighterProfiles.GlassCannon
        val random = Random(seed)

        val fighterAState = FighterState(fighterA, fighterA.maxHp, Coordinate(0, 0))
        val fighterBState = FighterState(fighterB, fighterB.maxHp, Coordinate(boardWidth - 1, boardHeight - 1))
        val startsWithA = when {
            fighterA.speed > fighterB.speed -> true
            fighterB.speed > fighterA.speed -> false
            else -> random.nextBoolean()
        }
        val firstFighter = if (startsWithA) fighterA.id else fighterB.id

        val state = MatchState(
            matchId = matchId,
            traceId = traceId,
            fighterA = fighterAState,
            fighterB = fighterBState,
            random = random,
            actingFighterId = firstFighter,
            turn = 1,
            deadline = nowMillisPlus(turnDurationMs),
            worldVersion = 0,
            coverEntities = turnResolver.createDefaultCoverEntities(boardWidth, boardHeight),
            pickupEntities = mutableMapOf(),
            attackBuffByFighter = mutableMapOf(fighterA.id to 0, fighterB.id to 0),
            factionByFighter = mutableMapOf(fighterA.id to "BLUE", fighterB.id to "RED"),
            boardWidth = boardWidth,
            boardHeight = boardHeight
        )
        matches[matchId] = state

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

        turnResolver.createInitialEntityEvents(state, now()).forEach { publishMatchEvent(it) }

        publishLifecycleEvent(turnResolver.createTurnOpenedEvent(state, now()))

        return StartMatchResponse(
            matchId = matchId,
            status = "RUNNING",
            firstTurnFighterId = firstFighter,
            turnDurationMs = turnDurationMs
        )
    }

    @KafkaListener(topicPattern = $$"${arena.kafka.action-topic-pattern}", groupId = "arena-engine")
    fun onFighterAction(record: ConsumerRecord<String, Any>) {
        val command = parseActionCommand(record.value()) ?: return
        val state = matches[command.matchId]
        if (state == null) {
            incrementRejectedCommandCounter("MATCH_NOT_FOUND")
            publishFeedback(
                fighterId = command.fighterId,
                matchId = command.matchId,
                turn = command.turn,
                status = FighterFeedbackStatus.INVALID_MOVE,
                actionType = command.actionType.name,
                reasonCode = "MATCH_NOT_FOUND",
                actorEntityId = command.fighterId,
                causationId = command.commandId
            )
            return
        }

        synchronized(state) {
            val issue = actionValidator.validateCommand(state, command, now())
            if (issue != null) {
                incrementRejectedCommandCounter(issue.reasonCode)
                publishFeedback(
                    fighterId = command.fighterId,
                    matchId = command.matchId,
                    turn = command.turn,
                    status = issue.status,
                    actionType = command.actionType.name,
                    reasonCode = issue.reasonCode,
                    actorEntityId = command.fighterId,
                    traceId = state.traceId,
                    causationId = command.commandId
                )
                return
            }

            state.pendingAction = command.actionType
            commandsAcceptedCounter.increment()
            publishFeedback(
                fighterId = command.fighterId,
                matchId = command.matchId,
                turn = command.turn,
                status = FighterFeedbackStatus.MOVE_ACCEPTED,
                actionType = command.actionType.name,
                reasonCode = null,
                actorEntityId = command.fighterId,
                traceId = state.traceId,
                causationId = command.commandId
            )
        }
    }

    @Scheduled(fixedDelayString = $$"${arena.turn.tick-check-ms:100}")
    fun resolveExpiredTurns() {
        val now = now()
        matches.values.forEach { state ->
            synchronized(state) {
                if (!state.ended && !now.isBefore(state.deadline)) {
                    val timerSample = Timer.start(meterRegistry)
                    val resolution = turnResolver.resolveTurn(state, now)
                    timerSample.stop(turnResolutionTimer)
                    resolution.matchEvents.forEach { publishMatchEvent(it) }
                    resolution.lifecycleEvents.forEach { publishLifecycleEvent(it) }
                    resolution.feedbackEvents.forEach { publishFeedback(it) }
                    if (resolution.matchEnded) {
                        matches.remove(state.matchId)
                    }
                }
            }
        }
    }

    private fun publishFeedback(
        fighterId: String,
        matchId: String,
        turn: Int,
        status: FighterFeedbackStatus,
        actionType: String?,
        reasonCode: String?,
        actorEntityId: String?,
        traceId: String = matchId,
        causationId: String? = null
    ) {
        val event = FighterFeedbackEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now(),
            matchId = matchId,
            turn = turn,
            traceId = traceId,
            causationId = causationId,
            payload = FighterFeedbackPayload(
                fighterId = fighterId,
                status = status,
                actionType = actionType?.let { enumValueOf<io.practicegroup.arena.domain.FighterActionType>(it) },
                reasonCode = reasonCode,
                actorEntityId = actorEntityId,
                worldVersion = null,
                entityChanges = emptyList()
            )
        )
        publishFeedback(event)
    }

    private fun publishFeedback(event: FighterFeedbackEvent) {
        arenaEventPublisher.publish("${event.payload.fighterId}.feedback.v1", event.matchId, event)
        log.info("Published feedback event type={} matchId={} turn={}", event.eventType, event.matchId, event.turn)
    }

    private fun publishMatchEvent(event: ArenaEvent) {
        arenaEventPublisher.publish(matchEventsTopic, event.matchId, event)
        log.info("Published match event type={} matchId={} turn={}", event.eventType, event.matchId, event.turn)
    }

    private fun publishLifecycleEvent(event: ArenaEvent) {
        arenaEventPublisher.publish(lifecycleTopic, event.matchId, event)
        log.info("Published lifecycle event type={} matchId={} turn={}", event.eventType, event.matchId, event.turn)
    }

    private fun parseActionCommand(raw: Any): FighterActionCommand? {
        return runCatching {
            val node = when (raw) {
                is JsonNode -> raw
                is String -> objectMapper.readTree(raw)
                else -> objectMapper.valueToTree(raw)
            }
            FighterActionCommand(
                matchId = node.path("matchId").asString(),
                turn = node.path("turn").asInt(),
                fighterId = node.path("fighterId").asString(),
                actionType = enumValueOf(node.path("actionType").asString("WAIT")),
                targetEntityId = node.path("targetEntityId").asString(null),
                commandId = node.path("commandId").asString().takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString()
            )
        }.onFailure { ex ->
            malformedCommandsCounter.increment()
            log.warn("Ignoring action command due to parse error type={}", raw::class.qualifiedName, ex)
        }.getOrNull()?.takeIf { it.matchId.isNotBlank() && it.fighterId.isNotBlank() }
    }

    private fun incrementRejectedCommandCounter(reasonCode: String?) {
        meterRegistry.counter(
            "arena.fighter.commands.rejected",
            "reason",
            reasonCode ?: "UNKNOWN"
        ).increment()
    }

    private fun now() = clock.instant()

    private fun nowMillisPlus(ms: Long) = now().plusMillis(ms)
}
