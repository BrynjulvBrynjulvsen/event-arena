# Event Arena Docs

This folder documents the arena as it works today.

## Read Order

1. `docs/quickstart.md` - run the full demo locally.
2. `docs/event-model.md` - topics, message shapes, and turn flow.
3. `docs/game-rules.md` - board geometry, actions, and resolution rules.
4. `docs/future-roadmap.md` - potential extension tracks for future workshops.
5. `docs/hackathon-menu.md` - exercise ideas for hackathons and workshop tracks.

## Core Components

- `arena-engine`: owns match state, validates commands, resolves turns.
- `arena-fighter`: publishes fighter actions based on turn updates.
- `arena-replay-cli`: consumes match events and prints replay logs.
- `arena-tui-cli`: consumes match events and renders a live terminal board.
