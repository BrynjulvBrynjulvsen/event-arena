package io.practicegroup.arena.domain

import java.time.Clock
import java.time.Instant
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

class ArenaMatchEngine(
    private val clock: Clock = Clock.systemUTC()
) {
    fun run(spec: MatchSpec, traceId: String): List<ArenaEvent> {
        val random = Random(spec.seed)
        val events = mutableListOf<ArenaEvent>()

        var fighterA = FighterState(spec.fighterA, spec.fighterA.maxHp, spec.arenaMin)
        var fighterB = FighterState(spec.fighterB, spec.fighterB.maxHp, spec.arenaMax)

        val startsWithA = when {
            spec.fighterA.speed > spec.fighterB.speed -> true
            spec.fighterB.speed > spec.fighterA.speed -> false
            else -> random.nextBoolean()
        }

        events += MatchScheduledEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now(),
            matchId = spec.matchId,
            traceId = traceId,
            payload = MatchScheduledPayload(spec.fighterA.id, spec.fighterB.id, spec.seed)
        )

        events += MatchStartedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now(),
            matchId = spec.matchId,
            traceId = traceId,
            payload = MatchStartedPayload(
                fighterAId = spec.fighterA.id,
                fighterBId = spec.fighterB.id,
                fighterAStartHp = fighterA.hp,
                fighterBStartHp = fighterB.hp,
                fighterAStartPosition = fighterA.position,
                fighterBStartPosition = fighterB.position,
                startsWithFighterId = if (startsWithA) spec.fighterA.id else spec.fighterB.id,
                seed = spec.seed
            )
        )

        var fighterATurn = startsWithA
        var ended = false

        for (turn in 1..spec.maxTurns) {
            val actor = if (fighterATurn) fighterA else fighterB
            val defender = if (fighterATurn) fighterB else fighterA

            events += TurnStartedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = spec.matchId,
                turn = turn,
                traceId = traceId,
                payload = TurnStartedPayload(
                    actingFighterId = actor.profile.id,
                    actingPosition = actor.position,
                    targetFighterId = defender.profile.id,
                    targetPosition = defender.position
                )
            )

            if (distance(actor.position, defender.position) > 1) {
                val moved = moveTowards(actor, defender.position)
                events += FighterMovedEvent(
                    eventId = EventFactory.eventId(),
                    occurredAt = now(),
                    matchId = spec.matchId,
                    turn = turn,
                    traceId = traceId,
                    payload = FighterMovedPayload(actor.profile.id, actor.position, moved.position)
                )
                if (fighterATurn) {
                    fighterA = moved
                } else {
                    fighterB = moved
                }
            } else {
                val hit = random.nextDouble() >= 0.1
                val criticalHit = hit && random.nextDouble() < actor.profile.critChance
                val damage = if (hit) calculateDamage(actor.profile, defender.profile, criticalHit, random) else 0

                events += AttackResolvedEvent(
                    eventId = EventFactory.eventId(),
                    occurredAt = now(),
                    matchId = spec.matchId,
                    turn = turn,
                    traceId = traceId,
                    payload = AttackResolvedPayload(actor.profile.id, defender.profile.id, hit, criticalHit, damage)
                )

                if (damage > 0) {
                    val defenderAfter = defender.copy(hp = (defender.hp - damage).coerceAtLeast(0))
                    events += DamageAppliedEvent(
                        eventId = EventFactory.eventId(),
                        occurredAt = now(),
                        matchId = spec.matchId,
                        turn = turn,
                        traceId = traceId,
                        payload = DamageAppliedPayload(defender.profile.id, defender.hp, defenderAfter.hp)
                    )

                    if (fighterATurn) {
                        fighterB = defenderAfter
                    } else {
                        fighterA = defenderAfter
                    }

                    if (defenderAfter.hp <= 0) {
                        val winner = actor
                        val loser = defenderAfter
                        events += MatchEndedEvent(
                            eventId = EventFactory.eventId(),
                            occurredAt = now(),
                            matchId = spec.matchId,
                            turn = turn,
                            traceId = traceId,
                            payload = MatchEndedPayload(
                                winnerFighterId = winner.profile.id,
                                loserFighterId = loser.profile.id,
                                reason = "KNOCKOUT",
                                winnerHpRemaining = winner.hp,
                                loserHpRemaining = loser.hp
                            )
                        )
                        ended = true
                        break
                    }
                }
            }

            fighterATurn = !fighterATurn
        }

        if (!ended) {
            val winner = if (fighterA.hp >= fighterB.hp) fighterA else fighterB
            val loser = if (winner.profile.id == fighterA.profile.id) fighterB else fighterA
            events += MatchEndedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now(),
                matchId = spec.matchId,
                turn = spec.maxTurns,
                traceId = traceId,
                payload = MatchEndedPayload(
                    winnerFighterId = winner.profile.id,
                    loserFighterId = loser.profile.id,
                    reason = "TIMEOUT",
                    winnerHpRemaining = winner.hp,
                    loserHpRemaining = loser.hp
                )
            )
        }

        return events
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

    private fun moveTowards(actor: FighterState, targetPosition: Int): FighterState {
        val delta = if (targetPosition > actor.position) 1 else -1
        return actor.copy(position = actor.position + delta)
    }

    private fun distance(a: Int, b: Int): Int = abs(a - b)

    private fun now(): Instant = clock.instant()
}
