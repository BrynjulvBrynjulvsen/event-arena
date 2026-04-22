# Event Arena

Event Arena is a Kotlin + Spring Kafka workshop system for practicing event-driven design in a playful setting.

It simulates turn-based combat on a 2D board where autonomous fighters publish commands, the engine resolves outcomes, and consumers build projections from Kafka events.

## Purpose and Features

- Teach event-first thinking with clear command-vs-event semantics.
- Provide a realistic, extensible domain for exercises (bots, UI, analytics, commentary, betting).
- Demonstrate schema-governed messaging with Kafka + Schema Registry.
- Include richer mechanics out of the box: ranged attacks, cover, pickups, and regeneration.
- Keep architecture modular so participants can extend one service without needing to understand the entire stack.

## Repository Intent

- Keep the core arena small and authoritative so event semantics stay clear.
- Include thin reference adapters that accelerate hackathons without removing the challenge.
- Leave production hardening and advanced capabilities to participants as exercises.

Boundary rule:

- If a component is required to run valid matches and authoritative event history, it belongs in core.
- If a component mainly demonstrates a pattern (UI, gateway, projection style), it belongs in reference.

## Documentation

- Start here: `docs/README.md`
- Event model and topic contracts: `docs/event-model.md`
- Board and turn rules: `docs/game-rules.md`
- Local runbook: `docs/quickstart.md`
- Future extension ideas backlog: `docs/future-ideas.md`
- Hackathon/workshop guide (menu + exercises): `docs/hackathon-exercises.md`
- Contributor conventions and design principles: `AGENTS.md`

## License

This project is licensed under the Apache License 2.0. See `LICENSE`.

## Module Tiers

Core (authoritative runtime):

- `arena-domain`: shared domain model (fighters, actions, events)
- `arena-engine`: turn coordinator and combat executor
- `arena-fighter`: bot pilot service (one instance per fighter)

Reference adapters (hackathon facilitators):

- `arena-replay-cli`: sample read model / event timeline logger
- `arena-tui-cli`: terminal-based live match visualizer
- `arena-observer-gateway`: websocket fan-out gateway for match spectators

## Run Locally

Use `docs/quickstart.md` as the canonical runbook.

Primary path:

```bash
./install.sh
```

This builds local images, starts the core stack with Docker Compose, sets schema compatibility, and prompts for optional observability.

After install:

- Trigger a match:

```bash
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
```

- Kafbat UI: `http://localhost:8085`
- Observer UI: `http://localhost:8090`

If observability is enabled:

- Prometheus: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000` (`admin` / `admin`)

Manual `bootRun` fallback remains documented in `docs/quickstart.md`.

## Update Running Stack

Full stack refresh:

```bash
./scripts/rebuild-and-restart.sh
```

Fast targeted refresh:

```bash
./scripts/rebuild-and-restart.sh engine
./scripts/rebuild-and-restart.sh fighter-balanced
./scripts/rebuild-and-restart.sh replay
```

## Demo helper scripts

Start two bots in the background (defaults: `balanced` and `glass-cannon`):

```bash
./scripts/start-demo-fighters.sh
```

Trigger a series of matches (`<engine-url> <count> <interval-ms>`):

```bash
./scripts/trigger-demo-matches.sh http://localhost:8080 5 1000
```

Stop background fighter processes:

```bash
./scripts/stop-demo-fighters.sh
```
