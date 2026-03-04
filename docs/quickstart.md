# Quickstart

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

- Kafka UI: `http://localhost:8085`
