# Game Rules

## Board

- Board is a 2D grid.
- Default size is `7 x 5` (`arena.board.width`, `arena.board.height`).
- Coordinates are zero-based: `(x, y)`.
- Default spawn points:
  - Fighter A: `(0, 0)`
  - Fighter B: `(width - 1, height - 1)`

## Turn Model

- Turns alternate between fighters.
- Each turn has a fixed action window (`arena.turn.duration-ms`, default `1000ms`).
- Engine accepts commands only for the active `(matchId, turn, fighterId)`.
- Latest valid command for the turn is executed when the window closes.
- If no valid command is present, engine executes `WAIT`.

## Validations

Engine rejects command with feedback status when:

- wrong match -> `INVALID_MOVE` with `MATCH_NOT_FOUND`
- wrong turn number -> `WRONG_TURN` with `TURN_MISMATCH`
- wrong fighter for active turn -> `WRONG_TURN` with `NOT_ACTIVE_FIGHTER`
- command received after deadline -> `TOO_LATE` with `TURN_CLOSED` or `MATCH_ENDED`
- move outside board -> `INVALID_MOVE` with `OUT_OF_BOUNDS`
- move into occupied cell -> `INVALID_MOVE` with `CELL_OCCUPIED`
- move into cover cell -> `INVALID_MOVE` with `CELL_BLOCKED_BY_COVER`
- attack outside fighter range -> `INVALID_MOVE` with `OUT_OF_RANGE`

## Movement

- Allowed movement actions: `MOVE_UP`, `MOVE_DOWN`, `MOVE_LEFT`, `MOVE_RIGHT`.
- Movement changes exactly one cell in the selected direction.
- Movement into invalid target is ignored at execution time.
- Moving onto an item cell consumes the pickup immediately.

## Combat

- Attack range uses Manhattan distance:
  - `distance = |x1 - x2| + |y1 - y2|`
  - in range when `distance <= fighter.attackRange`
- Hit chance: `90%` when in range, reduced by cover (`-20%`).
- Critical chance: fighter profile `critChance`.
- Defender in cover takes reduced damage (`25%` reduction).
- Attack buffs from pickups add to attack stat for the match.
- Damage formula:

```text
base = attack - defense / 2
roll = random(0.85..1.15)
critMultiplier = 2.0 when crit else 1.0
damage = max(1, round(base * roll * critMultiplier))
```

## Pickups

- Engine may spawn a random pickup at turn boundaries.
- Pickup types:
  - `HEAL_20`: restore up to `20` HP (capped by max HP)
  - `ATTACK_BOOST_5`: adds `+5` attack for rest of match
- Spawned pickups appear in `TurnOpened.payload.visibleEntities` as `entityType=ITEM`.
- Pickup lifecycle is emitted in match events as `EntitySpawned` and `EntityRemoved` facts.

## Cover

- Cover cells are static entities (`entityType=COVER`).
- Cover entities are emitted as first-class `EntitySpawned` events at match start.
- Fighters cannot move into cover cells.
- A defender adjacent to cover receives hit chance and damage mitigation.

## Health Regeneration

- Regeneration is profile-based (`regenPerTurn`).
- Applied to fighters at end of turn.
- Regeneration is emitted as structured effects and attribute changes in events.

## Match End

- `KNOCKOUT`: a fighter reaches `hp <= 0`.
- `TIMEOUT`: `maxTurns` reached (default `50`); higher remaining HP wins.
