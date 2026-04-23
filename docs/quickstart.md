# Quickstart

This is the canonical workshop runbook for local setup and verification.

## Prerequisites

- Docker
- Docker Compose plugin (`docker compose`)

## Docker Install Path (Primary)

From repository root:

```bash
./install.sh
```

The installer will:

- build all workshop service images,
- start infra + core workshop services,
- set Schema Registry compatibility to `BACKWARD_TRANSITIVE`,
- prompt whether to start observability overlay (Prometheus + Grafana).

## Verify The Stack

Trigger a match:

```bash
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
```

Expected result: a JSON payload with match metadata/state (running or terminal depending on lifecycle timing).

Open UIs:

- Kafbat UI: `http://localhost:8085`
- Observer UI: `http://localhost:8090`

If you enabled observability during install:

- Prometheus targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000` (`admin` / `admin`)

## Refresh After Code Changes

Full workshop refresh:

```bash
./scripts/rebuild-and-restart.sh
```

Fast single-service refresh examples:

```bash
./scripts/rebuild-and-restart.sh engine
./scripts/rebuild-and-restart.sh replay
./scripts/rebuild-and-restart.sh observer-gateway
```

Fighter refresh options:

```bash
./scripts/rebuild-and-restart.sh fighter-balanced
./scripts/rebuild-and-restart.sh fighter-glass-cannon
./scripts/rebuild-and-restart.sh fighter
```

`fighter` refreshes both fighter containers (they share the same image).

Accepted service names: `engine`, `fighter`, `fighter-balanced`, `fighter-glass-cannon`, `replay`, `observer-gateway`, `all`.

## Manual/Dev Fallback (bootRun)

Use this path if you need direct process-level debugging or local service execution outside containers.

1. Start infra only:

```bash
docker compose up -d kafka schema-registry kafka-ui
./scripts/set-compatibility.sh
```

2. Start services in separate terminals:

```bash
./gradlew :arena-engine:bootRun
./gradlew :arena-fighter:bootRun --args='--arena.fighter.id=balanced'
./gradlew :arena-fighter:bootRun --args='--arena.fighter.id=glass-cannon'
./gradlew :arena-replay-cli:bootRun
./gradlew :arena-observer-gateway:bootRun
```

Optional terminal visualization:

```bash
./gradlew :arena-tui-cli:bootRun
```

Optional richer multi-match terminal dashboard:

```bash
./scripts/run-tui-mordant.sh
```

Useful overrides for the Mordant dashboard:

```bash
./scripts/run-tui-mordant.sh --arena.tui.match-id=<matchId> --arena.tui.auto-follow=false
```

Optional schema validation before registration:

```bash
./scripts/check-schema-compatibility.sh http://localhost:8081 io.practicegroup.arena.domain.TurnOpenedEvent schemas/TurnOpenedEvent.schema.json
```

Replay consumer stores local checkpoint state in `.demo/replay-checkpoint.json`.
Delete that file to force a full local replay rebuild.

## Shutdown

Stop core stack:

```bash
docker compose down
```

If observability is running, stop both files:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml down
```
