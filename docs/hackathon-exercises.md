# Hackathon Exercises

Use this as the single exercise menu and execution guide for Event Arena.

## Working Agreement

- Commands are requests, engine events are facts.
- Keep authoritative truth in `arena.match-lifecycle.v1` and `arena.match-events.v1`.
- Use fighter topics for intent/feedback only.
- Pick one main exercise and one hardening angle.
- Recommended timebox: 2-4 hours.

## Exercise Selection Matrix

| Exercise | Level | Typical Time | Complexity | Delivery Risk |
| --- | --- | --- | --- | --- |
| 1) Human-Piloted Fighter + UI | Beginner | 2-4h | Medium | Medium |
| 2) Smarter Bot Strategies | Beginner | 2-3h | Medium | Low |
| 3) Scalable Spectator Streaming | Intermediate | 3-4h | High | Medium |
| 4) Robust Local State Projections | Beginner | 2-4h | Medium | Medium |
| 5) Observability Starter Track | Intermediate | 2-3h | Medium | Low |
| 6) Event Traceability + Correlation IDs | Intermediate | 2-4h | High | Medium |
| 7) Schema Evolution Safety | Intermediate | 2-3h | Medium | Low |
| 8) Turn Phase Clarity | Advanced | 3-4h | High | Medium |
| 9) Targeted Commands | Advanced | 3-4h | High | High |
| 10) Snapshot Stream Onboarding | Advanced | 3-4h | High | Medium |

## 1) Human-Piloted Fighter + UI

- **Level**: Beginner
- **Estimated time**: 2-4 hours
- **Prerequisites**: `docs/event-model.md`, running engine + at least one fighter
- **What**: Build a UI that submits fighter commands and renders turn outcomes.
- **How**: Publish intent to `<fighterId>.match-actions.v1`; render from lifecycle + match events only.
- **Why**: Reinforces command-vs-event boundaries in a user-facing path.
- **Where to start**: `arena-observer-gateway/`, `arena-fighter/`, `docs/event-model.md`
- **Minimum scope**: Send one command type (`MOVE_*` or `ATTACK`) and show latest turn outcome.
- **Stretch scope**: Full action set, command history, and fighter cooldown/feedback hints.
- **Done when**: A user can control a fighter in a live match and see authoritative turn-by-turn updates.
- **Demo checklist**:
  - Start one match.
  - Submit at least 3 manual commands.
  - Show accepted/rejected feedback and resulting engine events.

## 2) Smarter Bot Strategies (ML/LLM or Hybrid)

- **Level**: Beginner
- **Estimated time**: 2-3 hours
- **Prerequisites**: `docs/event-model.md`, existing fighter bot run locally
- **What**: Replace deterministic heuristics with an adaptive strategy.
- **How**: Keep command contract unchanged; plug in a strategy module driven by turn context + feedback.
- **Why**: Practices behavior upgrades without contract changes.
- **Where to start**: `arena-fighter/src/main/kotlin/io/practicegroup/arena/fighter/FighterBot.kt`
- **Minimum scope**: Feature-flag old vs new strategy and run both in repeated matches.
- **Stretch scope**: Add online learning or confidence-gated fallbacks.
- **Done when**: The new strategy is toggleable and produces visibly different behavior over multiple matches.
- **Demo checklist**:
  - Run baseline strategy for 3 matches.
  - Run new strategy for 3 matches.
  - Compare observable action patterns.

## 3) Scalable Spectator Streaming (Kafka -> WebSocket)

- **Level**: Intermediate
- **Estimated time**: 3-4 hours
- **Prerequisites**: `arena-observer-gateway` running, browser client connected
- **What**: Stream live match events to many browser clients without direct Kafka access.
- **How**: Evolve gateway with replay-on-connect and explicit backpressure/drop policy.
- **Why**: Practices adapter design for high fan-out stream delivery.
- **Where to start**: `arena-observer-gateway/src/main/kotlin/io/practicegroup/arena/observer/KafkaEventStreamConsumer.kt`, `arena-observer-gateway/src/main/kotlin/io/practicegroup/arena/observer/MatchWebSocketHandler.kt`
- **Minimum scope**: Multiple clients can join the same match mid-stream and remain stable.
- **Stretch scope**: Bounded replay window and per-session lag telemetry.
- **Done when**: At least 5 concurrent clients stay connected for one full match without gateway errors.
- **Demo checklist**:
  - Connect 5 browser sessions.
  - Start match and join a client mid-match.
  - Show all clients receiving consistent event flow.

## 4) Robust Local State Projections

- **Level**: Beginner
- **Estimated time**: 2-4 hours
- **Prerequisites**: replay consumer basics, local file/db storage
- **What**: Build a projection that survives restart, duplicate delivery, and replay.
- **How**: Persist offsets/checkpoints and make writes idempotent by stable event identity.
- **Why**: Practices at-least-once correctness.
- **Where to start**: `arena-replay-cli/src/main/kotlin/io/practicegroup/arena/replay/ReplayConsumer.kt`
- **Minimum scope**: Checkpoint offsets and deduplicate projection writes by `eventId`.
- **Stretch scope**: Rebuild from earliest into a clean projection store and validate row counts.
- **Done when**: Replay after restart produces no duplicate materialized rows for the same event history.
- **Demo checklist**:
  - Run consumer and process one match.
  - Restart consumer from last checkpoint.
  - Re-run from earliest and show identical final projection state.

## 5) Observability Starter Track

- **Level**: Intermediate
- **Estimated time**: 2-3 hours
- **Prerequisites**: Docker, engine + observer gateway running
- **What**: Add a usable observability baseline, then extend it.
- **How**: Use Actuator + Prometheus metrics with Grafana dashboarding.
- **Why**: Builds operational debugging habits for event-driven systems.
- **Where to start**: `docker-compose.observability.yml`, `observability/prometheus/prometheus.yml`, `observability/grafana/dashboards/event-arena-observability.json`, `arena-engine/src/main/resources/application.yml`, `arena-observer-gateway/src/main/resources/application.yml`
- **Minimum scope**:
  - Enable `/actuator/health` and `/actuator/prometheus` on `arena-engine` and `arena-observer-gateway`.
  - Run Prometheus + Grafana via optional compose overlay.
  - Show one end-to-end demo where triggering a match changes turn latency and observer consumer activity panels.
- **Stretch scope**:
  - Extend metrics exposure to fighter and replay processes.
  - Add domain counters (accepted/rejected commands, publish failures).
  - Add one alert rule and one troubleshooting runbook snippet.
- **Done when**: A new team can follow this guide and reach dashboard evidence in <= 30 minutes.
- **Demo checklist**:
  - `docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d`
  - Confirm Prometheus target status is `UP` for engine and gateway.
  - Trigger a match and show dashboard panel movement.
  - Stop one instrumented service and show target degrades to `DOWN`.

### Observability Runbook (Minimum Path)

1. Start infra and overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

2. Start instrumented services:

```bash
./gradlew :arena-engine:bootRun
./gradlew :arena-observer-gateway:bootRun
```

3. Verify endpoints:

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/prometheus | head
curl -s http://localhost:8090/actuator/health
curl -s http://localhost:8090/actuator/prometheus | head
```

4. Open UIs:
- Prometheus: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Dashboard: `Event Arena / Event Arena Observability`

5. Exercise signal flow:

```bash
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
```

6. Failure mode check:

```bash
pkill -f "arena-observer-gateway"
```

Confirm Prometheus shows `arena-observer-gateway` as `DOWN`.

## 6) Event Traceability and Correlation IDs

- **Level**: Intermediate
- **Estimated time**: 2-4 hours
- **Prerequisites**: comfort reading logs/events across services
- **What**: Trace one command end-to-end from submission to feedback and resulting domain events.
- **How**: Propagate `traceId`; set immediate feedback `causationId=commandId`; preserve IDs in logs.
- **Why**: Enables incident triage and causality debugging.
- **Where to start**: `arena-domain/src/main/kotlin/io/practicegroup/arena/domain/Actions.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/MatchOrchestrator.kt`, `docs/event-model.md`
- **Minimum scope**: One deterministic path can be traced with IDs only.
- **Stretch scope**: Add structured logging fields and trace summary view.
- **Done when**: Given one `commandId`, your team can retrieve all related feedback + domain events in under 2 minutes.
- **Demo checklist**:
  - Submit one command and capture `commandId`.
  - Show related feedback message.
  - Show related `ActionResolved`/`TurnClosed` path with matching IDs.

## 7) Schema Evolution Without Breaking Clients

- **Level**: Intermediate
- **Estimated time**: 2-3 hours
- **Prerequisites**: Schema Registry compatibility workflow
- **What**: Deliver one additive schema change safely and define controlled breaking-change path.
- **How**: Add optional field, run compatibility checks, document migration approach.
- **Why**: Practices contract governance in long-lived streams.
- **Where to start**: `schemas/`, `scripts/check-schema-compatibility.sh`, `scripts/register-schema.sh`, `scripts/set-compatibility.sh`
- **Minimum scope**: Backward-compatible schema update with unchanged consumer still functioning.
- **Stretch scope**: Draft `v2` topic/version playbook with dual-write or cutover steps.
- **Done when**: Compatibility check passes and at least one old consumer runs unchanged.
- **Demo checklist**:
  - Run compatibility check script.
  - Register updated schema.
  - Run an existing consumer without code changes.

## 8) Safe System Change: Turn Phase Clarity

- **Level**: Advanced
- **Estimated time**: 3-4 hours
- **Prerequisites**: lifecycle semantics and backward-compat evolution patterns
- **What**: Add explicit phase markers around turn progression while preserving existing clients.
- **How**: Enrich lifecycle payloads additively and update docs in the same change.
- **Why**: Practices semantic evolution without regressions.
- **Where to start**: `arena-domain/src/main/kotlin/io/practicegroup/arena/domain/Events.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/TurnResolver.kt`, `docs/future-roadmap.md`
- **Minimum scope**: Emit clear phase markers without breaking existing deserializers.
- **Stretch scope**: Add phase duration metrics and replay visualization.
- **Done when**: Existing consumers run without code updates and can ignore the new markers.
- **Demo checklist**:
  - Produce events with phase markers.
  - Run an unchanged consumer.
  - Show docs updated to match emitted events.

## 9) Safe System Change: Targeted Commands

- **Level**: Advanced
- **Estimated time**: 3-4 hours
- **Prerequisites**: command validation + turn resolution flow familiarity
- **What**: Honor `targetEntityId` for explicit target selection.
- **How**: Update validation and attack resolution; keep backward-compatible fallback when target missing.
- **Why**: Practices behavior extension through stable contracts.
- **Where to start**: `arena-domain/src/main/kotlin/io/practicegroup/arena/domain/Actions.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/ActionValidator.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/TurnResolver.kt`
- **Minimum scope**: Explicit targeting resolves correctly.
- **Stretch scope**: Multi-entity targeting rules and target-selection feedback hints.
- **Done when**: New clients can target explicitly and old clients still produce valid outcomes.
- **Demo checklist**:
  - Run one targeted attack flow.
  - Run one legacy command without target ID.
  - Show both paths remain valid.

## 10) Safe System Change: Snapshot Stream Onboarding

- **Level**: Advanced
- **Estimated time**: 3-4 hours
- **Prerequisites**: read model design and source-of-truth boundaries
- **What**: Publish optional turn-end snapshots on `arena.match-state.v1` to speed onboarding.
- **How**: Emit derivative snapshots while keeping authoritative streams unchanged.
- **Why**: Practices read-optimized streams without corrupting event truth.
- **Where to start**: `docs/event-model.md`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/TurnResolver.kt`, `docs/future-roadmap.md`
- **Minimum scope**: Snapshot topic can reconstruct current board state for one match.
- **Stretch scope**: Snapshot compaction strategy + consumer bootstrap optimization.
- **Done when**: A new UI/replay consumer can bootstrap from snapshot topic and continue from authoritative events.
- **Demo checklist**:
  - Produce snapshots for one match.
  - Rebuild current state from snapshots.
  - Verify authoritative streams still define final truth.

## Optional Bonus Ideas

- Narrated commentary engine built from lifecycle + action events.
- Leaderboard and ladder service (Elo/ranking projection).
- Referee/anomaly detector for suspicious command patterns.
- Property-based testing challenge for turn resolver invariants.
