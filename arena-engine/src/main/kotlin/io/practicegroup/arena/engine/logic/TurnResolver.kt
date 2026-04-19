package io.practicegroup.arena.engine.logic

import io.practicegroup.arena.domain.ActionEffect
import io.practicegroup.arena.domain.ActionEffectType
import io.practicegroup.arena.domain.ActionOutcome
import io.practicegroup.arena.domain.ActionResolvedEvent
import io.practicegroup.arena.domain.ActionResolvedPayload
import io.practicegroup.arena.domain.ArenaEntityType
import io.practicegroup.arena.domain.ArenaEvent
import io.practicegroup.arena.domain.AttackResolvedEvent
import io.practicegroup.arena.domain.AttackResolvedPayload
import io.practicegroup.arena.domain.Coordinate
import io.practicegroup.arena.domain.DamageAppliedEvent
import io.practicegroup.arena.domain.DamageAppliedPayload
import io.practicegroup.arena.domain.EntityChange
import io.practicegroup.arena.domain.EntityChangeType
import io.practicegroup.arena.domain.EntityRemovedEvent
import io.practicegroup.arena.domain.EntityRemovedPayload
import io.practicegroup.arena.domain.EntitySnapshot
import io.practicegroup.arena.domain.EntitySpawnedEvent
import io.practicegroup.arena.domain.EntitySpawnedPayload
import io.practicegroup.arena.domain.EventFactory
import io.practicegroup.arena.domain.FighterActionType
import io.practicegroup.arena.domain.FighterFeedbackEvent
import io.practicegroup.arena.domain.FighterFeedbackPayload
import io.practicegroup.arena.domain.FighterFeedbackStatus
import io.practicegroup.arena.domain.FighterMovedEvent
import io.practicegroup.arena.domain.FighterMovedPayload
import io.practicegroup.arena.domain.FighterProfile
import io.practicegroup.arena.domain.MatchEndedEvent
import io.practicegroup.arena.domain.MatchEndedPayload
import io.practicegroup.arena.domain.TurnClosedEvent
import io.practicegroup.arena.domain.TurnClosedPayload
import io.practicegroup.arena.domain.TurnOpenedEvent
import io.practicegroup.arena.domain.TurnOpenedPayload
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

data class TurnResolution(
    val lifecycleEvents: List<ArenaEvent>,
    val matchEvents: List<ArenaEvent>,
    val feedbackEvents: List<FighterFeedbackEvent>,
    val matchEnded: Boolean
)

class TurnResolver(
    private val turnDurationMs: Long,
    private val pickupSpawnChance: Double = 0.25
) {
    fun resolveTurn(match: MatchState, now: Instant): TurnResolution {
        val lifecycleEvents = mutableListOf<ArenaEvent>()
        val matchEvents = mutableListOf<ArenaEvent>()
        val feedbackEvents = mutableListOf<FighterFeedbackEvent>()

        val actor = match.activeFighter()
        val defender = match.otherFighter()
        val action = match.pendingAction ?: FighterActionType.WAIT
        match.pendingAction = null

        val entityChanges = mutableListOf<EntityChange>()
        var actionOutcome = ActionOutcome.NO_OP
        val actionEffects = mutableListOf<ActionEffect>()

        when (action) {
            FighterActionType.MOVE_UP -> {
                val target = actor.position.copy(y = actor.position.y - 1)
                actionOutcome = resolveMoveAction(match, actor.profile.id, target, entityChanges, actionEffects, matchEvents, now)
            }

            FighterActionType.MOVE_DOWN -> {
                val target = actor.position.copy(y = actor.position.y + 1)
                actionOutcome = resolveMoveAction(match, actor.profile.id, target, entityChanges, actionEffects, matchEvents, now)
            }

            FighterActionType.MOVE_LEFT -> {
                val target = actor.position.copy(x = actor.position.x - 1)
                actionOutcome = resolveMoveAction(match, actor.profile.id, target, entityChanges, actionEffects, matchEvents, now)
            }

            FighterActionType.MOVE_RIGHT -> {
                val target = actor.position.copy(x = actor.position.x + 1)
                actionOutcome = resolveMoveAction(match, actor.profile.id, target, entityChanges, actionEffects, matchEvents, now)
            }

            FighterActionType.ATTACK -> {
                val inRange = distance(actor.position, defender.position) <= actor.profile.attackRange
                val coverPenalty = if (defenderInCover(match, defender.position)) 0.20 else 0.0
                val hitChance = (0.90 - coverPenalty).coerceIn(0.05, 0.95)
                val hit = inRange && match.random.nextDouble() < hitChance
                val criticalHit = hit && match.random.nextDouble() < actor.profile.critChance
                val damagePreCover =
                    if (hit) {
                        calculateDamage(
                            attacker = actor.profile,
                            defender = defender.profile,
                            criticalHit = criticalHit,
                            attackBuff = match.attackBuffByFighter[actor.profile.id] ?: 0,
                            randomRoll = match.random.nextDouble()
                        )
                    } else {
                        0
                    }
                val damage =
                    if (defenderInCover(match, defender.position) && damagePreCover > 0) {
                        (damagePreCover * 0.75).roundToInt().coerceAtLeast(1)
                    } else {
                        damagePreCover
                    }

                matchEvents += AttackResolvedEvent(
                    eventId = EventFactory.eventId(),
                    occurredAt = now,
                    matchId = match.matchId,
                    turn = match.turn,
                    traceId = match.traceId,
                    payload = AttackResolvedPayload(actor.profile.id, defender.profile.id, hit, criticalHit, damage)
                )

                if (damage > 0) {
                    val hpAfter = (defender.hp - damage).coerceAtLeast(0)
                    matchEvents += DamageAppliedEvent(
                        eventId = EventFactory.eventId(),
                        occurredAt = now,
                        matchId = match.matchId,
                        turn = match.turn,
                        traceId = match.traceId,
                        payload = DamageAppliedPayload(defender.profile.id, defender.hp, hpAfter)
                    )
                    match.setFighterState(defender.copy(hp = hpAfter))
                    actionOutcome = ActionOutcome.SUCCESS
                    actionEffects += ActionEffect(
                        effectType = ActionEffectType.DAMAGE,
                        targetEntityId = defender.profile.id,
                        targetEntityType = ArenaEntityType.FIGHTER,
                        targetFaction = match.factionForEntity(defender.profile.id),
                        amount = damage,
                        critical = criticalHit,
                        metadata = mapOf(
                            "coverApplied" to defenderInCover(match, defender.position).toString(),
                            "range" to actor.profile.attackRange.toString()
                        )
                    )
                    entityChanges += EntityChange(
                        entityId = defender.profile.id,
                        entityType = ArenaEntityType.FIGHTER,
                        faction = match.factionForEntity(defender.profile.id),
                        changeType = EntityChangeType.ATTRIBUTE,
                        attributes = mapOf("hp" to hpAfter.toString())
                    )
                }
                if (!hit) {
                    actionOutcome = ActionOutcome.FAILED
                }
            }

            FighterActionType.WAIT -> {
                actionOutcome = ActionOutcome.NO_OP
            }
        }

        val loserAfterAction = listOf(match.fighterA, match.fighterB).firstOrNull { it.hp <= 0 }

        if (loserAfterAction == null) {
            applyRegeneration(match, entityChanges, actionEffects)
        }
        match.worldVersion += 1

        matchEvents += ActionResolvedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = ActionResolvedPayload(
                actorEntityId = actor.profile.id,
                actorEntityType = ArenaEntityType.FIGHTER,
                actorFaction = match.factionForEntity(actor.profile.id),
                actionType = action,
                outcome = actionOutcome,
                effects = actionEffects
            )
        )

        lifecycleEvents += TurnClosedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = TurnClosedPayload(
                actingFighterId = actor.profile.id,
                selectedAction = action,
                actionSource = if (action == FighterActionType.WAIT) "DEFAULT" else "FIGHTER"
            )
        )

        val currentActor = match.activeFighter()
        val currentDefender = match.otherFighter()
        val turnResultChanges = ensureFighterSnapshots(match, entityChanges, currentActor.profile.id, currentDefender.profile.id)
        feedbackEvents += turnResultFeedback(match, currentActor.profile.id, actor.profile.id, action, turnResultChanges, now)
        feedbackEvents += turnResultFeedback(match, currentDefender.profile.id, actor.profile.id, null, turnResultChanges, now)

        val loser = listOf(match.fighterA, match.fighterB).firstOrNull { it.hp <= 0 }
        if (loser != null) {
            val winner = listOf(match.fighterA, match.fighterB).first { it.profile.id != loser.profile.id }
            match.ended = true
            matchEvents += MatchEndedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now,
                matchId = match.matchId,
                turn = match.turn,
                traceId = match.traceId,
                payload = MatchEndedPayload(
                    winnerFighterId = winner.profile.id,
                    loserFighterId = loser.profile.id,
                    reason = "KNOCKOUT",
                    winnerHpRemaining = winner.hp,
                    loserHpRemaining = loser.hp
                )
            )
            return TurnResolution(lifecycleEvents, matchEvents, feedbackEvents, matchEnded = true)
        }

        if (match.turn >= match.maxTurns) {
            val winner = if (match.fighterA.hp >= match.fighterB.hp) match.fighterA else match.fighterB
            val loserByHp = if (winner.profile.id == match.fighterA.profile.id) match.fighterB else match.fighterA
            match.ended = true
            matchEvents += MatchEndedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now,
                matchId = match.matchId,
                turn = match.turn,
                traceId = match.traceId,
                payload = MatchEndedPayload(
                    winnerFighterId = winner.profile.id,
                    loserFighterId = loserByHp.profile.id,
                    reason = "TIMEOUT",
                    winnerHpRemaining = winner.hp,
                    loserHpRemaining = loserByHp.hp
                )
            )
            return TurnResolution(lifecycleEvents, matchEvents, feedbackEvents, matchEnded = true)
        }

        match.turn += 1
        match.actingFighterId = if (match.actingFighterId == match.fighterA.profile.id) match.fighterB.profile.id else match.fighterA.profile.id
        match.deadline = now.plusMillis(turnDurationMs)
        maybeSpawnPickup(match, now)?.let { matchEvents += it }
        lifecycleEvents += createTurnOpenedEvent(match, now)

        return TurnResolution(lifecycleEvents, matchEvents, feedbackEvents, matchEnded = false)
    }

    fun createTurnOpenedEvent(match: MatchState, now: Instant): TurnOpenedEvent {
        val actor = match.activeFighter()
        val defender = match.otherFighter()
        return TurnOpenedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = TurnOpenedPayload(
                actingFighterId = actor.profile.id,
                targetFighterId = defender.profile.id,
                actingPosition = actor.position,
                targetPosition = defender.position,
                turnDurationMs = turnDurationMs,
                boardWidth = match.boardWidth,
                boardHeight = match.boardHeight,
                actorAttackRange = actor.profile.attackRange,
                visibleEntities = buildVisibleEntities(match)
            )
        )
    }

    fun createDefaultCoverEntities(width: Int, height: Int): MutableMap<String, CoverEntity> {
        val coverA = CoverEntity("cover-a", Coordinate((width / 2).coerceAtMost(width - 1), (height / 2).coerceAtMost(height - 1)))
        val coverB = CoverEntity("cover-b", Coordinate((width / 2 - 1).coerceAtLeast(0), (height / 2).coerceAtMost(height - 1)))
        return mutableMapOf(coverA.entityId to coverA, coverB.entityId to coverB)
    }

    fun createInitialEntityEvents(match: MatchState, now: Instant): List<ArenaEvent> {
        val fighters = listOf(
            EntitySpawnedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now,
                matchId = match.matchId,
                turn = 0,
                traceId = match.traceId,
                payload = EntitySpawnedPayload(
                    entityId = match.fighterA.profile.id,
                    entityType = ArenaEntityType.FIGHTER,
                    faction = match.factionForEntity(match.fighterA.profile.id),
                    position = match.fighterA.position,
                    attributes = mapOf(
                        "hp" to match.fighterA.hp.toString(),
                        "maxHp" to match.fighterA.profile.maxHp.toString(),
                        "range" to match.fighterA.profile.attackRange.toString(),
                        "attack" to match.fighterA.profile.attack.toString(),
                        "defense" to match.fighterA.profile.defense.toString(),
                        "speed" to match.fighterA.profile.speed.toString(),
                        "regenPerTurn" to match.fighterA.profile.regenPerTurn.toString()
                    )
                )
            ),
            EntitySpawnedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now,
                matchId = match.matchId,
                turn = 0,
                traceId = match.traceId,
                payload = EntitySpawnedPayload(
                    entityId = match.fighterB.profile.id,
                    entityType = ArenaEntityType.FIGHTER,
                    faction = match.factionForEntity(match.fighterB.profile.id),
                    position = match.fighterB.position,
                    attributes = mapOf(
                        "hp" to match.fighterB.hp.toString(),
                        "maxHp" to match.fighterB.profile.maxHp.toString(),
                        "range" to match.fighterB.profile.attackRange.toString(),
                        "attack" to match.fighterB.profile.attack.toString(),
                        "defense" to match.fighterB.profile.defense.toString(),
                        "speed" to match.fighterB.profile.speed.toString(),
                        "regenPerTurn" to match.fighterB.profile.regenPerTurn.toString()
                    )
                )
            )
        )

        val covers = match.coverEntities.values.map {
            EntitySpawnedEvent(
                eventId = EventFactory.eventId(),
                occurredAt = now,
                matchId = match.matchId,
                turn = 0,
                traceId = match.traceId,
                payload = EntitySpawnedPayload(
                    entityId = it.entityId,
                    entityType = ArenaEntityType.COVER,
                    faction = "NEUTRAL",
                    position = it.position,
                    attributes = mapOf("coverBonus" to "0.20")
                )
            )
        }

        return fighters + covers
    }

    private fun resolveMoveAction(
        match: MatchState,
        fighterId: String,
        target: Coordinate,
        entityChanges: MutableList<EntityChange>,
        actionEffects: MutableList<ActionEffect>,
        matchEvents: MutableList<ArenaEvent>,
        now: Instant
    ): ActionOutcome {
        val fighter = match.fighterById(fighterId)
        val movedTo = applyMovement(match, fighterId, target) ?: return ActionOutcome.FAILED
        matchEvents += FighterMovedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = FighterMovedPayload(fighterId, fighter.position, movedTo)
        )
        actionEffects += ActionEffect(
            effectType = ActionEffectType.MOVED,
            targetEntityId = fighterId,
            targetEntityType = ArenaEntityType.FIGHTER,
            targetFaction = match.factionForEntity(fighterId),
            fromPosition = fighter.position,
            toPosition = movedTo
        )
        entityChanges += EntityChange(
            entityId = fighterId,
            entityType = ArenaEntityType.FIGHTER,
            faction = match.factionForEntity(fighterId),
            changeType = EntityChangeType.MOVED,
            position = movedTo
        )
        applyPickupIfPresent(match, fighterId, movedTo, entityChanges, actionEffects, matchEvents, now)
        return ActionOutcome.SUCCESS
    }

    private fun applyMovement(match: MatchState, fighterId: String, target: Coordinate): Coordinate? {
        val fighter = match.fighterById(fighterId)
        if (!isInBounds(target, match) || target == match.otherFighter().position || isCoverAt(match, target)) {
            return null
        }
        if (target == fighter.position) {
            return null
        }
        match.setFighterState(fighter.copy(position = target))
        return target
    }

    private fun applyPickupIfPresent(
        match: MatchState,
        fighterId: String,
        position: Coordinate,
        entityChanges: MutableList<EntityChange>,
        actionEffects: MutableList<ActionEffect>,
        matchEvents: MutableList<ArenaEvent>,
        now: Instant
    ) {
        val pickup = match.pickupEntities.values.firstOrNull { it.position == position } ?: return
        match.pickupEntities.remove(pickup.entityId)

        when (pickup.kind) {
            PickupKind.HEAL_20 -> {
                val fighter = match.fighterById(fighterId)
                val hpAfter = (fighter.hp + 20).coerceAtMost(fighter.profile.maxHp)
                if (hpAfter > fighter.hp) {
                    match.setFighterState(fighter.copy(hp = hpAfter))
                    entityChanges += EntityChange(
                        entityId = fighterId,
                        entityType = ArenaEntityType.FIGHTER,
                        faction = match.factionForEntity(fighterId),
                        changeType = EntityChangeType.ATTRIBUTE,
                        attributes = mapOf("hp" to hpAfter.toString())
                    )
                    actionEffects += ActionEffect(
                        effectType = ActionEffectType.HEAL,
                        targetEntityId = fighterId,
                        targetEntityType = ArenaEntityType.FIGHTER,
                        targetFaction = match.factionForEntity(fighterId),
                        amount = hpAfter - fighter.hp,
                        metadata = mapOf("source" to "pickup")
                    )
                }
            }

            PickupKind.ATTACK_BOOST_5 -> {
                val updated = (match.attackBuffByFighter[fighterId] ?: 0) + 5
                match.attackBuffByFighter[fighterId] = updated
                entityChanges += EntityChange(
                    entityId = fighterId,
                    entityType = ArenaEntityType.FIGHTER,
                    faction = match.factionForEntity(fighterId),
                    changeType = EntityChangeType.ATTRIBUTE,
                    attributes = mapOf("attackBuff" to updated.toString())
                )
                actionEffects += ActionEffect(
                    effectType = ActionEffectType.APPLIED_STATUS,
                    targetEntityId = fighterId,
                    targetEntityType = ArenaEntityType.FIGHTER,
                    targetFaction = match.factionForEntity(fighterId),
                    metadata = mapOf("status" to "ATTACK_BOOST_5")
                )
            }
        }

        entityChanges += EntityChange(
            entityId = pickup.entityId,
            entityType = ArenaEntityType.ITEM,
            faction = "NEUTRAL",
            changeType = EntityChangeType.REMOVED,
            position = pickup.position,
            attributes = mapOf("kind" to pickup.kind.name)
        )
        actionEffects += ActionEffect(
            effectType = ActionEffectType.DESPAWNED,
            targetEntityId = pickup.entityId,
            targetEntityType = ArenaEntityType.ITEM,
            targetFaction = "NEUTRAL",
            metadata = mapOf("kind" to pickup.kind.name)
        )
        matchEvents += EntityRemovedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = EntityRemovedPayload(
                entityId = pickup.entityId,
                entityType = ArenaEntityType.ITEM,
                faction = "NEUTRAL",
                reason = "PICKUP_COLLECTED",
                position = pickup.position,
                attributes = mapOf("kind" to pickup.kind.name)
            )
        )
    }

    private fun applyRegeneration(
        match: MatchState,
        entityChanges: MutableList<EntityChange>,
        actionEffects: MutableList<ActionEffect>
    ) {
        listOf(match.fighterA, match.fighterB).forEach { fighter ->
            val regen = fighter.profile.regenPerTurn
            if (regen <= 0 || fighter.hp >= fighter.profile.maxHp) {
                return@forEach
            }
            val hpAfter = (fighter.hp + regen).coerceAtMost(fighter.profile.maxHp)
            match.setFighterState(fighter.copy(hp = hpAfter))
            entityChanges += EntityChange(
                entityId = fighter.profile.id,
                entityType = ArenaEntityType.FIGHTER,
                faction = match.factionForEntity(fighter.profile.id),
                changeType = EntityChangeType.ATTRIBUTE,
                attributes = mapOf("hp" to hpAfter.toString())
            )
            actionEffects += ActionEffect(
                effectType = ActionEffectType.HEAL,
                targetEntityId = fighter.profile.id,
                targetEntityType = ArenaEntityType.FIGHTER,
                targetFaction = match.factionForEntity(fighter.profile.id),
                amount = hpAfter - fighter.hp,
                metadata = mapOf("source" to "regen")
            )
        }
    }

    private fun maybeSpawnPickup(match: MatchState, now: Instant): ArenaEvent? {
        if (match.pickupEntities.isNotEmpty() || match.random.nextDouble() > pickupSpawnChance) {
            return null
        }
        val available = mutableListOf<Coordinate>()
        for (x in 0..<match.boardWidth) {
            for (y in 0..<match.boardHeight) {
                val pos = Coordinate(x, y)
                if (pos != match.fighterA.position && pos != match.fighterB.position && !isCoverAt(match, pos)) {
                    available += pos
                }
            }
        }
        if (available.isEmpty()) {
            return null
        }
        val spawn = available[match.random.nextInt(available.size)]
        val kind = if (match.random.nextBoolean()) PickupKind.HEAL_20 else PickupKind.ATTACK_BOOST_5
        val entityId = "pickup-${match.turn}-${EventFactory.eventId().take(6)}"
        match.pickupEntities[entityId] = PickupEntity(entityId, kind, spawn)
        return EntitySpawnedEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = EntitySpawnedPayload(
                entityId = entityId,
                entityType = ArenaEntityType.ITEM,
                faction = "NEUTRAL",
                position = spawn,
                attributes = mapOf("kind" to kind.name)
            )
        )
    }

    private fun buildVisibleEntities(match: MatchState): List<EntitySnapshot> {
        val fighterAAttrs = mapOf(
            "hp" to match.fighterA.hp.toString(),
            "maxHp" to match.fighterA.profile.maxHp.toString(),
            "range" to match.fighterA.profile.attackRange.toString(),
            "attackBuff" to (match.attackBuffByFighter[match.fighterA.profile.id] ?: 0).toString()
        )
        val fighterBAttrs = mapOf(
            "hp" to match.fighterB.hp.toString(),
            "maxHp" to match.fighterB.profile.maxHp.toString(),
            "range" to match.fighterB.profile.attackRange.toString(),
            "attackBuff" to (match.attackBuffByFighter[match.fighterB.profile.id] ?: 0).toString()
        )

        val fighters = listOf(
            EntitySnapshot(match.fighterA.profile.id, ArenaEntityType.FIGHTER, match.factionForEntity(match.fighterA.profile.id), match.fighterA.position, fighterAAttrs),
            EntitySnapshot(match.fighterB.profile.id, ArenaEntityType.FIGHTER, match.factionForEntity(match.fighterB.profile.id), match.fighterB.position, fighterBAttrs)
        )
        val covers = match.coverEntities.values.map {
            EntitySnapshot(it.entityId, ArenaEntityType.COVER, "NEUTRAL", it.position, mapOf("coverBonus" to "0.20"))
        }
        val pickups = match.pickupEntities.values.map {
            EntitySnapshot(it.entityId, ArenaEntityType.ITEM, "NEUTRAL", it.position, mapOf("kind" to it.kind.name))
        }
        return fighters + covers + pickups
    }

    private fun ensureFighterSnapshots(
        match: MatchState,
        existing: MutableList<EntityChange>,
        fighterAId: String,
        fighterBId: String
    ): List<EntityChange> {
        if (existing.none { it.entityId == fighterAId }) {
            val fighter = match.fighterById(fighterAId)
            existing += EntityChange(
                entityId = fighterAId,
                entityType = ArenaEntityType.FIGHTER,
                faction = match.factionForEntity(fighterAId),
                changeType = EntityChangeType.ATTRIBUTE,
                position = fighter.position,
                attributes = mapOf("hp" to fighter.hp.toString())
            )
        }
        if (existing.none { it.entityId == fighterBId }) {
            val fighter = match.fighterById(fighterBId)
            existing += EntityChange(
                entityId = fighterBId,
                entityType = ArenaEntityType.FIGHTER,
                faction = match.factionForEntity(fighterBId),
                changeType = EntityChangeType.ATTRIBUTE,
                position = fighter.position,
                attributes = mapOf("hp" to fighter.hp.toString())
            )
        }
        return existing
    }

    private fun turnResultFeedback(
        match: MatchState,
        recipientFighterId: String,
        actorEntityId: String,
        actionType: FighterActionType?,
        entityChanges: List<EntityChange>,
        now: Instant
    ): FighterFeedbackEvent {
        return FighterFeedbackEvent(
            eventId = EventFactory.eventId(),
            occurredAt = now,
            matchId = match.matchId,
            turn = match.turn,
            traceId = match.traceId,
            payload = FighterFeedbackPayload(
                fighterId = recipientFighterId,
                status = FighterFeedbackStatus.TURN_RESULTS,
                actionType = actionType,
                reasonCode = null,
                actorEntityId = actorEntityId,
                worldVersion = match.worldVersion,
                entityChanges = entityChanges
            )
        )
    }

    private fun isInBounds(position: Coordinate, match: MatchState): Boolean {
        return position.x in 0..<match.boardWidth && position.y in 0..<match.boardHeight
    }

    private fun isCoverAt(match: MatchState, position: Coordinate): Boolean {
        return match.coverEntities.values.any { it.position == position }
    }

    private fun defenderInCover(match: MatchState, defenderPosition: Coordinate): Boolean {
        return match.coverEntities.values.any { distance(it.position, defenderPosition) == 1 }
    }

    private fun distance(a: Coordinate, b: Coordinate): Int = abs(a.x - b.x) + abs(a.y - b.y)

    private fun calculateDamage(
        attacker: FighterProfile,
        defender: FighterProfile,
        criticalHit: Boolean,
        attackBuff: Int,
        randomRoll: Double
    ): Int {
        val base = ((attacker.attack + attackBuff) - defender.defense / 2.0).coerceAtLeast(1.0)
        val roll = 0.85 + (randomRoll * 0.30)
        val critMultiplier = if (criticalHit) 2.0 else 1.0
        return (base * roll * critMultiplier).roundToInt().coerceAtLeast(1)
    }
}
