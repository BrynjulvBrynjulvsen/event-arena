# Hackathon Menu

Suggested exercise ideas using Event Arena.

## Pilot Experiences

- **AI-piloted fighter**
  - Build a fighter service that decides moves from `TurnOpened` context and feedback.

- **Human-piloted fighter + UI**
  - Build a UI to issue actions to a fighter topic and display turn outcomes.

- **Coach assistant**
  - Suggest actions for a human pilot based on current board/entity state.

## Visualisation and Replay

- **TUI visualisation**
  - Render live matches and replay timelines from event streams.

- **Web frontend via WebSockets**
  - Stream events to browser clients and animate the board in real time.

- **Replay artifact exporter**
  - Persist event timelines into a shareable replay format.

## Analytics and Games

- **Odds calculator / casino game**
  - Estimate win probability from historical and live state features.

- **Leaderboard and ladder**
  - Run tournaments and maintain Elo or ranking tables.

- **Combat analytics service**
  - Compute damage rates, pickup value, cover usage, and survival stats.

## Narrative and UX

- **Narrated commentary engine**
  - Generate play-by-play commentary from lifecycle and action events.

- **Highlight generator**
  - Detect key moments (critical hits, comeback turns, decisive pickups).

## Platform and Reliability

- **Observer API / dashboard backend**
  - Build a query API from projections for live dashboards.

- **Referee/anomaly detector**
  - Validate event flow and flag suspicious or malformed command patterns.

- **Chaos and latency simulator**
  - Inject delays/duplicates and verify resilience of fighters/consumers.

## Rules and Domain Extensions

- **New game mode plugin**
  - Add objective modes (king of the hill, survival zone, capture point).

- **Status effects**
  - Add stun/bleed/shield and event-driven expiry logic.

- **Fog-of-war**
  - Publish scoped visibility views and compare strategy quality.

## Contract and Engineering Practices

- **Schema evolution workshop**
  - Practice additive schema changes and compatibility discipline.

- **ACL/security lab**
  - Enforce fighter-scoped topic permissions and test access boundaries.

- **Property-based testing challenge**
  - Add invariant tests for resolver correctness under randomized scenarios.
