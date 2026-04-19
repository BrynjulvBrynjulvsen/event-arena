# Schemas

Schema Registry is used for all Kafka value payloads.

- Subject strategy: `RecordNameStrategy`
- Compatibility target: `BACKWARD_TRANSITIVE`

Current functional event documentation is in:

- `docs/event-model.md`

Current schema set includes:

- command schema: `FighterActionCommand.schema.json`
- lifecycle schemas: `MatchScheduledEvent.schema.json`, `MatchStartedEvent.schema.json`, `TurnOpenedEvent.schema.json`, `TurnClosedEvent.schema.json`
- combat schemas: `EntitySpawnedEvent.schema.json`, `EntityRemovedEvent.schema.json`, `ActionResolvedEvent.schema.json`, `FighterMovedEvent.schema.json`, `AttackResolvedEvent.schema.json`, `DamageAppliedEvent.schema.json`, `MatchEndedEvent.schema.json`
- feedback schema: `FighterFeedbackEvent.schema.json`

In local development, producers auto-register schemas.
For CI/prod, prefer explicit registration and set `auto.register.schemas=false`.
