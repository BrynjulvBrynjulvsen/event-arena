# Hackathon Foundation Plan

Maintainer-only plan for evolving the repository baseline used by hackathon participants.

## Progress Notes

Update this section whenever foundation work lands so maintainers can quickly see what is already scaffolded.

Current status (2026-04-19):

- [x] Phase 1 trace consistency pass (`traceId` and command `causationId` baseline)
- [x] Phase 1 event envelope/tracing documentation updates
- [x] Phase 1 schema compatibility helper script
- [x] Phase 2 observer gateway baseline (`arena-observer-gateway`)
- [x] Phase 2 projection checkpointing sample baseline (`arena-replay-cli`)
- [ ] Phase 3 observability stack starter
- [ ] Phase 3 shared client config standardization
- [ ] Phase 3 smarter bot strategy plug-in baseline
- [ ] Phase 3 schema evolution workshop end-to-end sample

## Phase 1 - Quick Wins (1-2 PRs)

Goal: improve traceability and schema workflow with minimal moving parts.

- **Trace consistency pass**
  - Ensure feedback events carry the same `traceId` as lifecycle/match events for the same match.
  - Outcome: easier end-to-end filtering in logs, Kafka UI, and future dashboards.

- **Event envelope guidance**
  - Document required envelope fields (`eventId`, `traceId`, `causationId`, `schemaVersion`) and intended semantics.
  - Outcome: participants share one mental model before building extensions.

- **Schema compatibility check script**
  - Add a helper script to verify candidate schemas against latest registered version.
  - Outcome: additive-change workflow is runnable locally and in CI.

Definition of done:

- Docs updated and linked from `README.md`.
- Compatibility check can be executed from terminal against local Schema Registry.

## Phase 2 - Lightweight New Module (2-4 PRs)

Goal: add one scalable spectator path and one robust consumer pattern.

- **Observer gateway (WebSocket fan-out)**
  - New module consumes authoritative topics and broadcasts match-scoped events to websocket clients.
  - Start with pass-through payloads before adding projections.

- **Projection checkpointing sample**
  - Extend a read model with offset checkpoint persistence and replay bootstrap behavior.
  - Demonstrate idempotent upsert keyed by `eventId`.

Definition of done:

- Browser or CLI websocket client can watch one match without direct Kafka access.
- Example projection can restart without duplicating materialized state.

## Phase 3 - Platform and Intelligence Tracks (later)

Goal: support advanced hackathon themes.

- **Observability stack**: Actuator metrics + Prometheus/Grafana starter dashboards.
- **Client config standardization**: shared client config module for common Kafka and registry settings.
- **Smarter bot track**: optional strategy plugin point for model-backed decisions.
- **Schema evolution workshop**: v1->v2 additive path, and explicit breaking-change migration path.

Definition of done:

- Teams can pick one advanced track independently with clear starter scaffolding.
