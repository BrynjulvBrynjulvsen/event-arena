# Arena v1 Event Schemas

These JSON Schemas document the v1 event contract for the arena topic.

- Topic: `arena.match-events.v1`
- Key: `matchId`
- Subject strategy: `RecordNameStrategy`
- Compatibility target: `BACKWARD_TRANSITIVE`

In this skeleton, schemas are automatically registered by the producer in local development.
For CI/prod, prefer explicit registration and set `auto.register.schemas=false`.
