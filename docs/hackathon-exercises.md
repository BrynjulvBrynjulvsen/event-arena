# Hackathon Exercises

Use this as the single exercise menu and execution guide for Event Arena.

## Working Agreement

- Commands are requests, engine events are facts.
- Keep authoritative truth in `arena.match-lifecycle.v1` and `arena.match-events.v1`.
- Use fighter topics for intent/feedback only.
- Pick one main exercise and one hardening angle.
- Recommended timebox: 2-4 hours.

## Scope Clarity

- Participant exercise work: modify whatever modules your exercise needs on your branch; optimize for learning and a clear demo.
- Workshop baseline evolution (maintainers): prefer additive changes, usually via new or reference modules first.
- For baseline changes, touch core modules (`arena-engine`, `arena-domain`, core contracts) only when the goal is an explicit authoritative behavior/contract change.

## Workshop Flow

1. **Bootstrap runtime**
   - Complete `docs/quickstart.md` and confirm engine + two fighters are running.
2. **Run sanity checks**
   - Trigger one match with `POST /matches`.
   - Confirm a running match response is returned.
   - Confirm fighters show at least one `feedback status=MOVE_ACCEPTED`.
3. **Pick one primary exercise**
   - Use the selection matrix below and choose a scope that fits your timebox.
4. **Implement minimum scope first**
   - Get `Minimum scope` working before taking on stretch goals.
5. **Demo with evidence**
   - Use each exercise `Demo checklist` to present result quality, not just code changes.

## Shared Baseline Prerequisites

- `docs/quickstart.md` completed.
- One match has been triggered successfully.
- Engine and two fighter processes are running and producing non-`WAIT` actions.

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
| 11) Engine Crash Recovery + Match Takeover | Advanced | 4-6h | Very High | High |

## 1) Human-Piloted Fighter + UI

- **Level**: Beginner
- **Estimated time**: 2-4 hours
- **Prerequisites**: shared baseline prerequisites, plus `docs/event-model.md`.
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
- **Prerequisites**: shared baseline prerequisites, plus `docs/event-model.md`.
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
- **Prerequisites**: shared baseline prerequisites, plus `arena-observer-gateway` running with a browser client connected.
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
- **Prerequisites**: shared baseline prerequisites, plus replay consumer basics and local file/db storage.
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
- **Prerequisites**: shared baseline prerequisites, plus observer gateway running.
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
# In the terminal running ./gradlew :arena-observer-gateway:bootRun, press Ctrl+C
```

Confirm Prometheus shows `arena-observer-gateway` as `DOWN`.

## 6) Event Traceability and Correlation IDs

- **Level**: Intermediate
- **Estimated time**: 2-4 hours
- **Prerequisites**: shared baseline prerequisites, plus comfort reading logs/events across services.
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
- **Prerequisites**: shared baseline prerequisites, plus Schema Registry compatibility workflow.
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
- **Prerequisites**: shared baseline prerequisites, plus lifecycle semantics and backward-compat evolution patterns.
- **What**: Add explicit phase markers around turn progression while preserving existing clients.
- **How**: Enrich lifecycle payloads additively and update docs in the same change.
- **Why**: Practices semantic evolution without regressions.
- **Where to start**: `arena-domain/src/main/kotlin/io/practicegroup/arena/domain/Events.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/TurnResolver.kt`, `docs/event-model.md`
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
- **Prerequisites**: shared baseline prerequisites, plus command validation + turn resolution flow familiarity.
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
- **Prerequisites**: shared baseline prerequisites, plus read model design and source-of-truth boundaries.
- **What**: Publish optional turn-end snapshots on `arena.match-state.v1` to speed onboarding.
- **How**: Emit derivative snapshots while keeping authoritative streams unchanged.
- **Why**: Practices read-optimized streams without corrupting event truth.
- **Where to start**: `docs/event-model.md`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/TurnResolver.kt`
- **Minimum scope**: Snapshot topic can reconstruct current board state for one match.
- **Stretch scope**: Snapshot compaction strategy + consumer bootstrap optimization.
- **Done when**: A new UI/replay consumer can bootstrap from snapshot topic and continue from authoritative events.
- **Demo checklist**:
  - Produce snapshots for one match.
  - Rebuild current state from snapshots.
  - Verify authoritative streams still define final truth.

## 11) Reliability Hardening: Engine Crash Recovery + Match Takeover

- **Level**: Advanced
- **Estimated time**: 4-6 hours
- **Prerequisites**: shared baseline prerequisites, plus strong understanding of lifecycle/event semantics and idempotent processing.
- **Current behavior to acknowledge explicitly**: Today, active matches are kept in memory inside the engine process. If the engine stops mid-match, that in-flight match does not continue automatically after restart.
- **What**: Make in-flight matches recoverable across engine restarts and allow safe takeover by a new engine instance.
- **How**: Introduce durable match state recovery and single-owner turn resolution per `matchId`.
- **Why**: Practices real event-driven reliability boundaries (durability, ownership, idempotency, replay safety).
- **Where to start**: `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/MatchOrchestrator.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/MatchState.kt`, `arena-engine/src/main/kotlin/io/practicegroup/arena/engine/logic/TurnResolver.kt`, `docs/event-model.md`
- **Minimum scope**:
  - Detect/recover previously active matches after engine restart.
  - Resume turn progression without creating duplicate turn outcomes for the same `matchId` + `turn`.
  - Show at least one match survives an engine restart during live execution.
- **Stretch scope**:
  - Multi-engine scenario with deterministic ownership/takeover (`lease`/`lock` per match).
  - Automatic stale-owner detection and reassignment.
  - Recovery latency metric and takeover audit events.
- **Done when**: Stopping engine mid-match and starting a new engine instance allows match completion with no duplicate authoritative turn facts.
- **Demo checklist**:
  - Start a match and capture `matchId`.
  - Stop engine while match is in progress.
  - Start engine again (same or new instance).
  - Show match continues and reaches `MatchEnded`.
  - Show no duplicate turn-close / action-resolved facts for the same turn.

### Hint Patterns (Choose One Or Combine)

1. **Snapshot + event replay hybrid**
   - Persist periodic match snapshots.
   - On restart, load latest snapshot and replay subsequent authoritative events.
2. **Compacted state topic**
   - Publish per-match state envelopes keyed by `matchId` to a compacted topic.
   - Rehydrate active state from latest compacted records at startup.
3. **External state store with ownership lease**
   - Keep active match state in a DB/kv store.
   - Use a lease/heartbeat field so only one engine resolves turns for each match.
4. **Idempotent turn resolution contract**
   - Treat `(matchId, turn)` as idempotency key.
   - Ensure repeated recovery attempts cannot emit conflicting authoritative facts.

Guardrails:

- Keep command topics as intent-only; do not treat commands as authoritative history.
- Keep `arena.match-lifecycle.v1` and `arena.match-events.v1` as source of truth.
- Preserve additive schema evolution if new recovery/ownership events are introduced.

## Optional Bonus Ideas

- Narrated commentary engine built from lifecycle + action events.
- Leaderboard and ladder service (Elo/ranking projection).
- Referee/anomaly detector for suspicious command patterns.
- Property-based testing challenge for turn resolver invariants.
