# Event Arena v1 Skeleton

Minimal Kotlin/Spring Kafka arena core for workshop exercises.

## What is included

- `arena-domain`: deterministic turn-based arena engine and event types
- `arena-engine`: Spring Boot producer app with `POST /matches`
- `arena-fighter`: Spring Boot bot app; each instance controls one fighter
- `arena-replay-cli`: Spring Boot consumer app that replays events from Kafka
- `docker-compose.yml`: Kafka (KRaft) + Schema Registry + Kafka UI
- `schemas/`: JSON schema documentation for v1 event contract

## Eventing choices

- Topic: `arena.match-events.v1`
- Lifecycle topic: `arena.match-lifecycle.v1`
- Key: `matchId`
- Serializer: Confluent JSON Schema serializer
- Subject strategy: `RecordNameStrategy`
- Schema Registry: required in local setup
- Kafka wiring uses Spring Boot property-based auto-configuration (`spring.kafka.*`)
- `MatchOrchestrator` publishes via `ArenaEventPublisher` to keep orchestration decoupled from Spring Kafka APIs

## Build/dependency conventions

- Versions are centralized in `gradle/libs.versions.toml`
- Spring dependencies use Gradle's native BOM support via `platform(org.springframework.boot:spring-boot-dependencies)`
- `io.spring.dependency-management` is intentionally not used

## Run locally

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

Kafka UI is available on `http://localhost:8085`.

## Notes for workshop progression

- This skeleton auto-registers schemas in local development.
- For CI/prod, set `auto.register.schemas=false` and register schemas explicitly.
- Keep schema changes additive to maintain backward compatibility.
