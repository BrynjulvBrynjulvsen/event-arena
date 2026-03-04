# Event Arena Docs

This folder documents the arena as it works today.

## Read Order

1. `docs/quickstart.md` - run the full demo locally.
2. `docs/event-model.md` - topics, message shapes, and turn flow.
3. `docs/game-rules.md` - board geometry, actions, and resolution rules.

## Core Components

- `arena-engine`: owns match state, validates commands, resolves turns.
- `arena-fighter`: publishes fighter actions based on turn updates.
- `arena-replay-cli`: consumes match events and prints replay logs.
