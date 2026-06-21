# Design: Room Locks & Procedural Challenges

Status: **proposal** (no implementation yet)
Branch: `claude/room-locks-challenges-a3k9qz`

This document is the load-bearing design for the next phase: turning rooms from
pure traversal space into **challenges that gate progression**. Everything below
is meant to be agreed on before code is written.

## Decisions (made)

- **Multiplayer: co-op gated.** Friends share one run/instance. A locked room
  opens for everyone the moment its objective is met. Advancing past a checkpoint
  requires *all living players* to be in the checkpoint room; stragglers are
  teleported forward before the old segment is torn down.
- **Lock scope: mix of gated + free rooms.** Most rooms are *free* (traversal,
  effect, loot — forward door open by default). A *gated* minority seal the
  forward door until solved. Target density **~1 in 4** non-checkpoint rooms.
- **Death: respawn at the last checkpoint, 3 lives per run.** Death no longer
  ejects to the main world. The player respawns at the current checkpoint room
  (the snake head, which still exists) and the run continues. Keep-inventory still
  protects items and XP. Each run grants **3 lives**; the death that spends the
  last one ends the run (eject to main world). Movement soft-resets cost no life.
- **Movement failure: soft reset at the room entrance.** Falling in lava / missing
  a parkour jump does *not* kill — the player is caught, teleported to the room's
  entrance, and the challenge re-arms. (Combat death still goes through the death
  model above.)
- **Rewards: deliberately sparse.** Completing a gated room may grant a *very*
  light progression aid (e.g. a stone tool), run **score**, and/or fire a
  configurable **list of console commands** — and always opens the door
  (progression). It must stay minimal and must **not** touch the external key
  currency (that's owned by a separate system).

## Goals

1. A `RoomChallenge` abstraction with a clear lifecycle, owned per room.
2. Leak-proof teardown: entities and scheduled tasks a room creates are tracked
   and disposed when the room is removed (or the instance is disposed).
3. Seal/open forward doors at runtime based on objective completion.
4. Detect room enter/leave (effect rooms, lock activation, co-op presence).
5. Co-op-safe advancement and checkpoint **respawn** under the sliding window.
6. Ship a thin vertical slice (3 challenge types) on the abstraction.

Non-goals for v1: multi-level doors (snake stays on one Y plane — §14),
prefab/schematic rooms (designed *for* later, not built now), persistence of
in-progress runs across server restarts.

---

## Current code, briefly

- `RoomNode` — geometry only: `cells`, `roomMin/Max`, incoming `door`,
  `corridor` (bridge) box, `entryDir`. **No behavior or state.**
- `DungeonGrid` — occupancy set, ordered `path` of `RoomNode` (the snake),
  spawn, emerald location, last exit direction.
- `DungeonLayoutGenerator` — builds spawn room + segments; `buildRoom` carves
  every door open immediately; only the checkpoint door is retained for sealing.
- `DungeonManager.tickInstances` — every 5 ticks: detach stragglers, advance the
  instance when **any** player is on the emerald.
- `Dungeon` — `World`, `WorkloadQueue`, `List<UUID> players`, return locations,
  `extending` flag.
- World rules already set: `DO_MOB_SPAWNING=false`, `DO_FIRE_TICK=false`,
  `KEEP_INVENTORY=true`, no daylight/weather cycle. Good — combat and lava rooms
  won't fight ambient spawns or spreading fire.
- `DungeonProtectionListener` — `@HIGHEST` death handler forces keep-inventory;
  respawn handler ejects to main world; break/place cancelled for participants.

---

## 1. The `RoomChallenge` abstraction

A challenge is the *behavior* attached to a room: created at generation, built via
the queue, activated when players enter, ticked while active, reset on failure,
and torn down with the room.

```java
public interface RoomChallenge {

    ChallengeType type();

    /** Free rooms return false (forward door opens at build time); gated rooms
     *  return true (forward door stays sealed until isComplete). */
    boolean isGated();

    /** Place challenge geometry/decor via the queue. No live entities yet —
     *  the room may be built far ahead of the player. */
    void build(RoomContext ctx);

    /** First player crosses into the bbox: spawn mobs, start the lava timer,
     *  show the objective bar. Idempotent — once per activation, not per player. */
    void activate(RoomContext ctx);

    /** Per-player bbox transitions (enter/leave effects, presence). */
    void onPlayerEnter(RoomContext ctx, Player player);
    void onPlayerLeave(RoomContext ctx, Player player);

    /** Periodic while ACTIVE (driven by the manager tick). */
    void tick(RoomContext ctx);

    /** Player failed a movement/skill check: re-arm to the activated state
     *  (refill removed blocks, reset lava, clear partial progress). The manager
     *  has already teleported the player to the room entrance. */
    void reset(RoomContext ctx);

    boolean isComplete(RoomContext ctx);

    /** Win, once: grant rewards (§10); the manager opens the forward door. */
    void onComplete(RoomContext ctx);

    /** Despawn tracked entities, cancel tracked tasks, restore blocks. Always
     *  called on teardown OR instance disposal, even if never activated. */
    void teardown(RoomContext ctx);
}
```

### Per-room state machine

`RoomNode` carries a `ChallengeState`:

```
PENDING ──activate()──▶ ACTIVE ──isComplete──▶ COMPLETE
                          ▲ │
                  reset() │ │ fail (movement)
                          └─┘
```

- Free rooms start `COMPLETE` for door purposes (door open at build) but may still
  `activate` for effect/loot behavior; they never gate.
- `COMPLETE` is sticky and shared across all players (co-op). Re-entering a solved
  room (e.g. after a checkpoint respawn) finds it already open — re-traversal is
  fast.

### `RoomContext` (what a challenge may touch)

```java
public final class RoomContext {
    World world();
    RoomNode room();              // geometry: bbox, doors, entrance point
    Dungeon dungeon();            // players, score, RNG
    WorkloadQueue queue();        // throttled block placement
    RoomScope scope();            // entity + task registry (§2)
    Random random();              // per-instance seeded (§8)
}
```

Challenges never call `Bukkit.getScheduler()` or `world.spawn()` directly — they
go through `RoomScope` so everything is tracked for teardown.

### `ChallengeType` + factory

```java
public enum ChallengeType {
    // free
    EMPTY, EFFECT_BUFF, EFFECT_DEBUFF, LOOT,
    // gated
    CLEAR_MOBS, FIND_KEY, PARKOUR_GOLD, FLOOR_IS_LAVA, BUTTON_SEQUENCE, ...
}
interface ChallengeFactory { RoomChallenge create(ChallengeType type); }
```

The generator picks a type per room from a weighted table — **~25% gated, ~75%
free**, with a guard against two gated rooms back-to-back — instantiates the
challenge, and stores it on the `RoomNode`. Weights live in one place for balance
tuning.

---

## 2. Entity & task lifecycle — `RoomScope` (the leak fix)

`removeTail` clears *blocks* only. Once rooms spawn mobs or schedule timers,
sliding the window will orphan live entities and running tasks. Every room gets a
`RoomScope`:

```java
public final class RoomScope {
    Entity spawn(Location loc, EntityType type, Consumer<Entity> init); // tags + tracks
    int schedule(Runnable task, long delay, long period);              // tracks task id
    void dispose();  // remove all tracked entities, cancel all tracked tasks
}
```

- Every spawned entity is tagged via `PersistentDataContainer`
  `dungeon_room = <instanceUUID>:<roomId>` so even a stray reload can sweep
  leftovers (defense in depth on top of in-memory tracking).
- `teardown` calls `scope.dispose()` then restores blocks via the queue.
- Instance disposal disposes every room's scope before deleting the world — this
  also closes today's latent gap where per-room timers would never be cancelled.

---

## 3. Door model: gated vs free

The connector already produces a sealable door slice (`toDoor`) — the new room's
entrance wall. The "lock" on room **N** is simply room **N+1's entrance**, kept
filled until N is solved.

Changes to `buildRoom` / segment generation:

- Build the bridge + tunnel as today, then per the *current* room's challenge:
  - current room **free** → carve the forward door open at build time (today's
    behavior).
  - current room **gated** → after carving the tunnel, immediately re-fill the
    forward door slice (seal it) and record that box on the current room as its
    `lockDoor`.
- `RoomNode` gains: `RoomChallenge challenge`, `ChallengeState state`,
  `Location lockDoorMin/Max`, `int roomId`, and an optional per-room ceiling
  height (§14).
- `onComplete` → the manager carves `lockDoor` open (queued) and sets state
  `COMPLETE`.

Because the whole segment is still built ahead of time (FIFO queue guarantees a
door only opens once its room exists), the player meets sealed doors at gated
rooms and walks freely through free rooms.

---

## 4. Room enter/leave detection

A point-in-bbox test against `RoomNode.roomMin/Max` (inclusive of the door
region), evaluated per player on the manager tick:

- The manager keeps `Map<UUID, RoomNode> currentRoom`.
- Each tick, recompute each player's room; diff against `currentRoom` to fire
  `onPlayerLeave(old)` / `onPlayerEnter(new)`, and `activate(new)` the first time
  any player enters a `PENDING` room.
- Multi-cell rooms are one rectangle, so a single bbox test covers them.

5-tick cadence is fine for effects and presence. Lava waves run on their own
scheduled task for finer timing (via `RoomScope`).

---

## 5. Death & checkpoint respawn

Today death ejects to the main world. New model:

- The **respawn anchor** is the box center of the snake **head** room
  (`path.peekFirst()`) — i.e. the last checkpoint the party reached. `DungeonGrid`
  caches `checkpointSpawn`, set in `generateInitial` (start room) and updated on
  every `advance` to the room the player checkpointed at (which becomes the new
  head).
- `DungeonManager.handleDeath` no longer detaches/ejects participants; it returns
  the `checkpointSpawn`. The respawn handler in `DungeonProtectionListener` sets
  the respawn location to it (instead of ejecting). Keep-inventory already
  preserves items/XP.
- Death does **not** reset challenges — combat rooms keep their remaining mobs;
  the player resumes mid-state on re-entry.
- Leaving (`/leave`), quitting, or walking out of the world still returns to the
  main world and disposes the instance when empty (unchanged).
- **Lives:** **3 per run** (`livesPerRun = 3`, tunable). `Dungeon` tracks
  `livesRemaining`; each combat death decrements it and respawns the player at the
  checkpoint anchor. When it hits zero the run ends — the player is ejected to the
  main world (keeping what they found) and the final score is recorded (§7.1).
  Movement soft-resets (§6) never decrement lives. In co-op, lives are **shared
  across the party** (a shared pool), so reckless play costs everyone — a dead
  player spectates until the next checkpoint respawn, and the run ends only when
  the shared pool is exhausted.

---

## 6. Movement failure & soft reset

Distinct from combat death. A movement challenge defines failure (touched lava,
fell below the room floor, ran out of disappearing floor):

- The manager (or the challenge) detects failure for a player and calls
  `resetPlayerToRoomEntrance(player, room)` — teleport to `room.entrancePoint()`
  (just inside the incoming door) — then `challenge.reset(ctx)` re-arms the room.
- `DungeonProtectionListener` cancels **lava, fall, and void** damage for
  participants standing in a movement room and routes them to the soft reset
  instead, so a fall never becomes a death. (A normal void plunge outside a
  movement room still falls through to the death model.)
- `reset` is challenge-specific: `FLOOR_IS_LAVA` restores the floor and restarts
  its timer; parkour refills any consumed blocks; etc.

---

## 7. Rewards (sparse, data-driven)

`onComplete` resolves a `RewardSpec` — intentionally minimal and configurable:

```java
public final class RewardSpec {
    List<ItemStack> items;     // e.g. a single stone tool, rarely
    List<String> commands;     // console commands with placeholders
    int score;                 // added to the run score
}
```

- **Items:** very light progression aids only (a stone pickaxe/sword, a torch
  handful). Granted sparingly — most gated rooms give nothing but the open door.
- **Commands:** a configured list run via `Bukkit.dispatchCommand(consoleSender,
  …)` with placeholders (`%player%`, `%room%`, `%depth%`, `%score%`). This is the
  clean integration seam for any *external* reward system without coupling — and
  explicitly **not** used to mint the key currency ourselves.
- **Score:** an optional small per-room bonus that feeds the run score (§7.1).
  Most gated rooms give `0` — the score that matters is how far and how long the
  run lasts, not grinding rooms.
- **Progression:** always — the door opens. That is the baseline reward.

Defaults ship near-empty (progression only); items/commands/score are opt-in per
challenge type via config.

### 7.1 Run scoring — depth + survival time

The run's score is primarily **how far** the party got and **how long** they
lasted, not per-room completion:

```
score = depth * DEPTH_WEIGHT
      + floor(secondsSurvived / TIME_UNIT) * TIME_WEIGHT
      + bonusFromRewards          // sum of any RewardSpec.score
```

- **Depth** = rooms entered (or segments cleared — pick one unit; rooms gives finer
  granularity). It is the dominant term, so pushing forward always beats stalling.
- **Survival time** = seconds from run start until the run ends (lives exhausted,
  leave, or quit). A secondary term so lasting longer is rewarded, but **depth
  outweighs idling** — `DEPTH_WEIGHT` is set so one new room is worth more than the
  time a careful player would spend reaching it. This avoids an exploit where
  standing still farms score.
- `Dungeon` tracks `depth`, `runStartMillis`, and the running `score`. The final
  score is computed when the run ends and handed off (leaderboard / a configured
  command) — it never mints the key currency.
- In co-op the run has a single shared score for the party.

All three weights are config constants for tuning.

---

## 8. Co-op gated advancement (sliding-window safety)

Today `advance` deletes the segment behind the checkpoint when **any** player
touches the emerald — a friend mid-segment falls into the void. New rules:

1. **Advance gate:** advance only when *every living player* is within the
   checkpoint room's bbox (and at least one is on the emerald). Until then show a
   bossbar/action-bar: "Waiting for players (2/3)".
2. **Straggler pull:** on advance, before `removeTail`, teleport any player not in
   the checkpoint room into it (they're about to be swept).
3. **Shared lock state:** gated completion is shared — solved once, open for all.
4. **Respawn anchor update:** `advance` updates `checkpointSpawn` (§5) to the new
   head room.
5. **Join API:** add `DungeonManager.joinDungeon(player, host)` to add a player to
   an existing instance (the player list exists; only the entry path is missing).
   Joiners enter empty-handed and spawn at the host's current room (or the
   checkpoint if mid-build).
6. **Per-instance RNG:** replace the shared `Random` with a per-`Dungeon` seeded
   `Random` (stored on `Dungeon`) — isolates instances and enables reproducible /
   shareable seeds (daily-seed feature) for free.

---

## 9. Event routing

Challenges react to Bukkit events for the player's *current* room. The listener
forwards to the manager, which dispatches to that room's challenge:

| Event | Used by | Routing |
|---|---|---|
| `EntityDeathEvent` | `CLEAR_MOBS` | match the dying entity's `dungeon_room` PDC tag → that room's challenge |
| `PlayerInteractEvent` | `FIND_KEY`, `BUTTON_SEQUENCE` | locate the player's current room → challenge |
| `ProjectileHitEvent` | shooting-gallery | match the hit block/target to a room |
| `EntityDamageEvent` (lava/fall/void) | movement rooms | participant in a movement room → soft reset (§6), cancel damage |
| poll (manager tick) | enter/leave, `FLOOR_IS_LAVA` timing, combat win-check | bbox + challenge `tick()` |

`PlayerMoveEvent` is avoided for hot paths — position-based checks ride the
existing 5-tick poll; only fine-timed mechanics (lava waves) use their own
scheduled task.

---

## 10. Manager tick loop, revised

Per active instance, each tick:

1. Detach players who left the world; dispose if empty (unchanged).
2. Recompute per-player current room; fire enter/leave/activate (§4).
3. `tick()` every `ACTIVE` room that has players in it; on a fresh `isComplete`,
   run `onComplete` → reward (§7) → open `lockDoor` (§3) → state `COMPLETE`.
4. If not `extending` and the co-op advance gate is satisfied (§8.1),
   `advanceDungeon` (with straggler pull + respawn-anchor update).
5. "Generating the area ahead…" action bar while the queue is busy (unchanged).

Only occupied rooms are ticked, so cost scales with party size, not dungeon
length.

---

## 11. Vertical challenges (height) — v1 compromise {#vertical-challenges}

Parkour wants verticality, but multi-level doors would break the single-Y-plane
snake (the connector's door Y is fixed). For v1:

- Keep **all doors at floor level** on one plane.
- A "tall" room raises only its **ceiling** (per-room height is a build-time
  choice on `RoomNode.roomMax` — no connector change). Parkour climbs to an
  objective (gold block / button) high in the room; reaching it opens the
  **floor-level** forward door; the player descends and exits.
- `FLOOR_IS_LAVA` needs no extra height — it's a floor mechanic on a standard room.

True vertical shafts / multi-level doors are deferred (they need connector work).

---

## 12. Vertical slice to ship first

Implement the abstraction (§1–§10) plus **three** challenges that exercise every
moving part:

1. **`EFFECT_BUFF` (free):** enter → apply Speed/Jump; leave → strip. Exercises
   enter/leave + per-player effects; no entities/doors.
2. **`CLEAR_MOBS` (gated):** activate → `scope.spawn` a small wave targeting the
   room; complete when all tracked mobs are dead → open forward door. Exercises
   the entity registry, gating, teardown, and `EntityDeathEvent` routing.
3. **`FIND_KEY` (gated):** a tagged key item hidden in a chest / under a block;
   used on a keyhole lever (or detected at the door) → open forward door.
   Exercises tagged items, `PlayerInteractEvent`, and a non-combat gate.

These prove free vs gated doors, enter/leave, entity + task teardown, co-op shared
completion, checkpoint respawn, and the reward seam. Everything else
(`FLOOR_IS_LAVA`, parkour, button sequence, sculk stealth, …) is then "just
another `RoomChallenge`."

---

## 13. Performance notes

- Layout *planning* runs synchronously in `advanceDungeon`. Choosing challenge
  types + richer rooms adds work; watch the per-advance tick cost and chunk the
  planning across ticks if it spikes.
- Challenge `build` must use the queue (never direct bulk placement).
- `tick()` runs only for occupied, active rooms — bounded by party size.

## 14. Open questions / defaults

- **Lives.** Decided: 3 per run, shared in co-op (§5).
- **Score weights.** `DEPTH_WEIGHT` / `TIME_WEIGHT` / `TIME_UNIT` need playtest
  tuning; depth must dominate so idling can't farm score (§7.1).
- **Failure penalty.** Soft reset is free in v1; could add a small score/time
  penalty later.
- **Gated guard.** No two gated rooms back-to-back (default on) — confirm desired.

---

## Touch list (when we implement)

- `objects/RoomNode` — add `roomId`, `challenge`, `state`, `lockDoorMin/Max`,
  ceiling height, `entrancePoint()`.
- `objects/DungeonGrid` — `checkpointSpawn` anchor (set/update on advance).
- `objects/Dungeon` — per-instance seeded `Random`, `livesRemaining`, `depth`,
  `runStartMillis`, running `score`, `currentRoom` map (or hold it on the
  manager), room-scope ownership.
- New package `challenge/` — `RoomChallenge`, `ChallengeType`, `ChallengeState`,
  `RoomContext`, `RoomScope`, `ChallengeFactory`, `RewardSpec`, and the three v1
  challenge classes.
- `factories/DungeonLayoutGenerator` — pick + attach challenges (weighted ~1/4
  gated, no back-to-back); seal forward doors for gated rooms; per-room height.
- `managers/DungeonManager` — enter/leave detection, room ticking, reward +
  door-open on complete, co-op advance gate + straggler pull, checkpoint respawn,
  soft reset, `joinDungeon`, leak-proof disposal via scopes.
- `listeners/DungeonProtectionListener` — route `EntityDeathEvent`,
  `PlayerInteractEvent`, `ProjectileHitEvent`, `EntityDamageEvent` to the active
  room's challenge; change respawn from eject → checkpoint anchor; cancel
  lava/fall/void damage in movement rooms → soft reset.
