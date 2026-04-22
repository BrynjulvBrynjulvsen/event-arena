# Quickstart

This is the canonical workshop runbook for local setup and verification.

## Prerequisites

- Java 21
- Docker

## Start Infrastructure

```bash
docker compose up -d
./scripts/set-compatibility.sh
```

## Start Services

Open separate terminals for each process.

Optional (schema workflow): validate a local schema file before registration:

```bash
./scripts/check-schema-compatibility.sh http://localhost:8081 io.practicegroup.arena.domain.TurnOpenedEvent schemas/TurnOpenedEvent.schema.json
```

```bash
./gradlew :arena-engine:bootRun
```

```bash
./gradlew :arena-fighter:bootRun --args='--arena.fighter.id=balanced'
```

```bash
./gradlew :arena-fighter:bootRun --args='--arena.fighter.id=glass-cannon'
```

```bash
./gradlew :arena-replay-cli:bootRun
```

Replay consumer stores local checkpoint state in `.demo/replay-checkpoint.json`.
Delete that file to force a full local replay rebuild.

Optional (browser spectators):

```bash
./gradlew :arena-observer-gateway:bootRun
```

Optional (observability starter stack):

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

Then open:

- Prometheus targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000` (`admin` / `admin`)

Optional (visual demo): run terminal visualization instead:

```bash
./gradlew :arena-tui-cli:bootRun
```

## Trigger Matches

```bash
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
```

Or run a burst:

```bash
./scripts/trigger-demo-matches.sh http://localhost:8080 5 1000
```

## Run Bots In Background (Optional)

```bash
./scripts/start-demo-fighters.sh
./scripts/stop-demo-fighters.sh
```

## Local UI

- Kafbat UI: `http://localhost:8085`
- Observer UI: `http://localhost:8090`
