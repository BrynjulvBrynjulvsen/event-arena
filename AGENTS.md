# AGENTS.md

This document captures the current engineering conventions and design choices for Event Arena.

## Purpose

- Keep contributors aligned on architecture, event semantics, and coding standards.
- Preserve workshop-friendly decisions: clear contracts, minimal framework plumbing, and strong event-driven patterns.

## System Shape

- `arena-domain`: shared domain types (fighters, actions, events).
- `arena-engine`: authoritative turn coordinator and combat executor.
- `arena-fighter`: pilot service; each instance controls one fighter.
- `arena-replay-cli`: example read model / replay consumer.

## Core Domain Decisions

- Board is 2D (`Coordinate(x, y)`), default `7x5`.
- Movement is cardinal: `MOVE_UP`, `MOVE_DOWN`, `MOVE_LEFT`, `MOVE_RIGHT`.
- Attack range uses Manhattan distance (`<= fighter.attackRange`).
- Cover entities exist on the board and apply hit/damage modifiers.
- Pickup entities can spawn and apply typed effects when collected.
- Regeneration can apply at turn end via fighter profile (`regenPerTurn`).
- Turns are time-boxed (`arena.turn.duration-ms`, default 1000ms).
- Latest valid command within a turn window is executed.
- If no valid command is available when a turn closes, engine executes `WAIT`.

## Event Model Semantics

- Commands are requests; events are facts.
- Engine events are authoritative match history.
- Fighter command topics are intent input only.
- Do not reconstruct match truth from commands alone.

Authoritative streams:

- `arena.match-lifecycle.v1` (match/turn phase facts)
- `arena.match-events.v1` (combat/domain facts)

Intent/feedback streams:

- `<fighterId>.match-actions.v1` (fighter commands)
- `<fighterId>.feedback.v1` (accept/reject/turn result feedback)

## Kafka Conventions

- Message key: always `matchId`.
- Subject strategy: `RecordNameStrategy`.
- Schema Registry is required.
- Compatibility target: `BACKWARD_TRANSITIVE`.
- Prefer additive schema evolution.
- In local dev, auto-registration is acceptable; in CI/prod, prefer explicit registration.

## Spring/Kotlin Conventions

- Use Spring Boot property-based Kafka auto-configuration by default (`spring.kafka.*`).
- Avoid custom factory/config classes unless a behavior cannot be expressed by properties.
- Keep orchestration logic framework-light; use adapters for infra concerns.
- Use Spring Boot 4 native Jackson 3 types (`tools.jackson.*`), not `com.fasterxml.*` imports.

## Build and Dependency Conventions

- Kotlin + Gradle KTS.
- Versions centralized in `gradle/libs.versions.toml`.
- Use Gradle BOM (`platform(org.springframework.boot:spring-boot-dependencies)`).
- Do not use `io.spring.dependency-management` plugin in modules.
- Keep Confluent serializer dependency aligned, but exclude transitive `kafka-clients` to honor Spring Boot managed Kafka client version.

## Coding Practices

- Keep domain events explicit and stable.
- Prefer small, composable classes over hidden framework behavior.
- Log meaningful decision points (accepted/rejected commands, selected action per turn).
- Handle consumer parse failures defensively; avoid poisoning retry loops for malformed records.

## Consumer Expectations

- Consumers should treat engine events as source of truth.
- Assume at-least-once delivery; write idempotent consumers where possible.
- Use lifecycle and feedback topics for coordination, not for final match truth.

## Documentation Expectations

- Keep `docs/event-model.md` aligned with actual behavior.
- Keep `docs/game-rules.md` aligned with engine rules.
- Keep `docs/quickstart.md` runnable and treat it as the canonical local runbook.
- Keep `docs/hackathon-exercises.md` as the primary workshop execution guide (flow + exercise scorecards).
- Keep `docs/future-ideas.md` as an optional inspiration backlog, not a committed roadmap.
- Update docs in the same PR when changing topics, payloads, or turn semantics.

## Demo/Workshop Operations

- Use helper scripts in `scripts/` for repeatable demos:
  - `start-demo-fighters.sh`
  - `stop-demo-fighters.sh`
  - `trigger-demo-matches.sh`
- Keep observability infrastructure opt-in via compose overlay (do not bloat default local startup path).
- Prefer deterministic service-stop instructions in docs (for example, stop the owning terminal/session) over broad process-matching kill commands.
- Keep the local workflow simple and explicit for participants.
