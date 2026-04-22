# Event Arena Docs

This folder documents the arena as it works today.

## Read Order

1. `docs/quickstart.md` - run the full demo locally.
2. `docs/hackathon-exercises.md` - workshop flow + exercise selection and scorecards.
3. `docs/event-model.md` - topics, message shapes, and turn flow.
4. `docs/game-rules.md` - board geometry, actions, and resolution rules.

Optional inspiration backlog:

- `docs/future-ideas.md` - non-committed ideas for future workshop tracks.

## Module Intent

Core (authoritative):

- `arena-engine`: owns match state, validates commands, resolves turns.
- `arena-fighter`: publishes fighter actions based on turn updates.

Reference (facilitator/starter):

- `arena-replay-cli`: consumes match events and prints replay logs.
- `arena-tui-cli`: consumes match events and renders a live terminal board.
- `arena-observer-gateway`: consumes match/lifecycle events and streams them to websocket clients.
