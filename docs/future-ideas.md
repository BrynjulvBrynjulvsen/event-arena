# Future Ideas

Optional extension ideas for upcoming workshops. This is an inspiration backlog, not a delivery roadmap.

## Now (High Leverage)

- **Turn phase clarity**
  - Add explicit phase markers (`TURN_START`, `ACTION_RESOLVED`, `TURN_END`) to lifecycle events.
  - Helps consumer debugging and deterministic replay.

- **Use command targeting more directly**
  - `targetEntityId` already exists in command payloads.
  - Start using it in engine attack resolution and fighter bots.

- **Additional read-model examples**
  - Add sample consumers for leaderboard, win-rates, and damage per turn.
  - Gives participants concrete projection patterns beyond replay logging.

## Next

- **Multi-fighter matches**
  - Extend from 1v1 to 2v2 or free-for-all.
  - Exercises entity/faction reasoning and richer strategy.

- **Status effects**
  - Add typed effects such as `STUNNED` or `BLEED` with durations.
  - Reinforces event-driven state transitions.

- **Snapshot stream for beginners**
  - Optional `arena.match-state.v1` turn-end snapshots.
  - Makes UI and analytics onboarding faster while preserving event-sourced core.

## Later

- **Fog-of-war / scoped visibility**
  - Publish per-fighter partial views while keeping engine events authoritative.

- **Human pilot UI**
  - Add authenticated command gateway + browser UI using existing command contracts.

- **LLM pilot track**
  - Use `TurnOpened` context and match events for model-driven strategy/commentary exercises.
