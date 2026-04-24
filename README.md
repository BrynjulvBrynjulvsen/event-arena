# Event Arena

Event Arena is a Kotlin + Spring Kafka workshop repository for practicing event-driven design.

The system simulates turn-based combat on a 2D board where fighters publish commands, the engine resolves turns, and consumers project match history from Kafka events.

## What Is In This Repository

Core modules (authoritative runtime):

- [`arena-domain`](arena-domain): shared domain model (fighters, actions, events)
- [`arena-engine`](arena-engine): match orchestration and combat resolution
- [`arena-fighter`](arena-fighter): bot service (one instance per fighter)

Reference modules (workshop helpers):

- [`arena-replay-cli`](arena-replay-cli): replay/logging consumer
- [`arena-observer-gateway`](arena-observer-gateway): websocket spectator gateway
- [`arena-tui-cli`](arena-tui-cli): simple Spring Boot terminal match visualization
- [`arena-tui-mordant`](arena-tui-mordant): richer standalone terminal dashboard with multi-match viewing

Run the Mordant dashboard in a normal terminal with:

```bash
./scripts/run-tui-mordant.sh
```

## Get Started

Prerequisites:

- Docker
- Docker Compose plugin (`docker compose`)

From repository root:

```bash
./install.sh
```

Then trigger a match:

```bash
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
```

Useful local URLs:

- Engine: `http://localhost:8080`
- Kafbat UI: `http://localhost:8085`
- Observer UI: `http://localhost:8090`

## Next Docs

- Canonical local runbook: [`docs/quickstart.md`](docs/quickstart.md)
- Workshop flow and exercises: [`docs/hackathon-exercises.md`](docs/hackathon-exercises.md)
- Event model/contracts: [`docs/event-model.md`](docs/event-model.md)
- Game rules: [`docs/game-rules.md`](docs/game-rules.md)
- Docs index: [`docs/README.md`](docs/README.md)
- Contributor conventions: [`AGENTS.md`](AGENTS.md)

## License

This project is licensed under the Apache License 2.0. See [`LICENSE`](LICENSE).
