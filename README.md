# Event Arena

Event Arena is a Kotlin + Spring Kafka workshop system for practicing event-driven design in a playful setting.

It simulates turn-based combat on a 2D board where autonomous fighters publish commands, the engine resolves outcomes, and consumers build projections from Kafka events.

## Purpose and Features

- Teach event-first thinking with clear command-vs-event semantics.
- Provide a realistic, extensible domain for exercises (bots, UI, analytics, commentary, betting).
- Demonstrate schema-governed messaging with Kafka + Schema Registry.
- Include richer mechanics out of the box: ranged attacks, cover, pickups, and regeneration.
- Keep architecture modular so participants can extend one service without needing to understand the entire stack.

## Documentation

- Start here: `docs/README.md`
- Event model and topic contracts: `docs/event-model.md`
- Board and turn rules: `docs/game-rules.md`
- Local runbook: `docs/quickstart.md`
- Contributor conventions and design principles: `AGENTS.md`

## License

This project is licensed under the Apache License 2.0. See `LICENSE`.

## Modules

- `arena-domain`: shared domain model (fighters, actions, events)
- `arena-engine`: turn coordinator and combat executor
- `arena-fighter`: bot pilot service (one instance per fighter)
- `arena-replay-cli`: sample read model / event timeline logger

## Run Locally

1. Start Kafka stack:

```bash
docker compose up -d
```

2. Set registry compatibility (recommended before first run):

```bash
./scripts/set-compatibility.sh
```

3. Start producer app:

```bash
./gradlew :arena-engine:bootRun
```

4. Start two fighter bots in separate terminals:

```bash
./gradlew :arena-fighter:bootRun --args='--arena.fighter.id=balanced'
./gradlew :arena-fighter:bootRun --args='--arena.fighter.id=glass-cannon'
```

5. Start replay consumer in another terminal:

```bash
./gradlew :arena-replay-cli:bootRun
```

6. Trigger a match:

```bash
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
```

Kafka UI is available on `http://localhost:8085`.

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
