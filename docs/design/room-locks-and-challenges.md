# Design: Room Locks & Procedural Challenges

Status: **proposal** (no implementation yet)
Branch: `claude/room-locks-challenges-a3k9qz`

This document is the load-bearing design for the next phase: turning rooms from
pure traversal space into **challenges that gate progression**. Everything below
is meant to be agreed on before code is written.

## Decisions (already made)

- **Multiplayer: co-op gated.** Friends share one run/instance. A locked room
  opens for everyone the moment its objective is met. Advancing past a checkpoint
  requires *all living players* to be in the checkpoint room; stragglers are
  teleported forward before the old segment is torn down.
- **Lock scope: mix of gated + free rooms.** Most rooms are *free* (traversal,
  effect, loot — forward door open by default). A subset are *gated* (forward
  door sealed until the objective is solved). The emerald checkpoint remains the
  save-point cadence; per-room locks are the moment-to-moment layer on top.

## Goals

1. Introduce a `RoomChallenge` abstraction with a clear lifecycle, owned per room.
2. Make teardown leak-proof: entities and scheduled tasks a room creates are
   tracked and disposed when the room is removed (or the instance is disposed).
3. Seal/open forward doors at runtime based on objective completion.
4. Detect room enter/leave (needed for effect rooms, lock activation, and co-op
   presence gating).
5. Make co-op advancement safe with the sliding-window teardown.
6. Ship a thin vertical slice (2–3 challenge types) on top of the abstraction.

Non-goals for v1: multi-level doors (snake stays on one Y plane — see
[Vertical challenges](#vertical-challenges)), prefab/schematic rooms (designed
*for* later, not built now), persistence of in-progress runs across restarts.

---

## Current code, briefly

- `RoomNode` — geometry only: `cells`, `roomMin/Max`, incoming `door`,
  `corridor` (bridge) box, `entryDir`. **No behavior or state.**
- `DungeonGrid` — occupancy set, ordered `path` of `RoomNode` (the snake),
  spawn, emerald location, last exit direction.
- `DungeonLayoutGenerator` — builds the spawn room + segments; `buildRoom`
  carves every door open immediately; only the checkpoint door is retained for
  sealing.
- `DungeonManager.tickInstances` — every 5 ticks: detach stragglers, advance the
  instance when **any** player is on the emerald.
- `Dungeon` — holds the `World`, `WorkloadQueue`, `List<UUID> players`, return
  locations, `extending` flag.
- World rules already set: `DO_MOB_SPAWNING=false`, `DO_FIRE_TICK=false`,
  `KEEP_INVENTORY=true`, no daylight/weather cycle. Good — combat and lava rooms
  won't fight ambient spawns or spreading fire.

---

## 1. The `RoomChallenge` abstraction

A challenge is the *behavior* attached to a room. It is created at generation
time, built via the workload queue, activated when players enter, ticked while
active, and torn down with the room.

```java
public interface RoomChallenge {

    ChallengeType type();

    /** Free rooms return false (forward door opens at build time); gated rooms
     *  return true (forward door stays sealed until isComplete). */
    boolean isGated();

    /** Place challenge geometry/decor via the queue. No live entities yet —
     *  the room may be built far ahead of the player. */
    void build(RoomContext ctx);

    /** First player crosses into the room's bbox: activate (spawn mobs, start
     *  the lava timer, apply enter-effects, show the objective bar). Idempotent
     *  — called once per activation, not per player. */
    void activate(RoomContext ctx);

    /** Any player enters/leaves the bbox (for per-player effects and presence). */
    void onPlayerEnter(RoomContext ctx, Player player);
    void onPlayerLeave(RoomContext ctx, Player player);

    /** Periodic while active (driven by the manager tick). */
    void tick(RoomContext ctx);

    boolean isComplete(RoomContext ctx);

    /** Win: reward, sfx, and (for gated rooms) the manager opens the forward
     *  door. Called once. */
    void onComplete(RoomContext ctx);

    /** Despawn tracked entities, cancel tracked tasks, restore blocks. Always
     *  called on room teardown OR instance disposal, even if never activated. */
    void teardown(RoomContext ctx);
}
```

### `RoomContext` (what a challenge is allowed to touch)

```java
public final class RoomContext {
    World world();
    RoomNode room();              // geometry: bbox, doors
    Dungeon dungeon();            // players in the run
    WorkloadQueue queue();        // throttled block placement
    RoomScope scope();            // entity + task registry (see §2)
    Random random();              // per-instance seeded (see Open questions)
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

The generator picks a `ChallengeType` per room from a weighted table (mostly
free, a minority gated), instantiates the challenge, and stores it on the
`RoomNode`. Weights/look-up live in one place so balancing is a config change.

---

## 2. Entity & task lifecycle — `RoomScope` (the leak fix)

`removeTail` currently clears blocks only. The moment rooms spawn mobs or
schedule timers, sliding the window will orphan live entities and running
`BukkitRunnable`s. Every room gets a `RoomScope`:

```java
public final class RoomScope {
    Entity spawn(Location loc, EntityType type, Consumer<Entity> init); // tags + tracks
    int schedule(Runnable task, long delay, long period);              // tracks task id
    void dispose();  // remove all tracked entities, cancel all tracked tasks
}
```

- Every spawned entity is tagged with a `PersistentDataContainer` key
  `dungeon_room = <instanceUUID>:<roomId>` so a stray reload can still sweep
  leftovers (defense in depth on top of in-memory tracking).
- `teardown` calls `scope.dispose()` then restores blocks (via the queue).
- Instance disposal disposes every room's scope before deleting the world.

This also fixes a latent issue: instance disposal deletes the world (which clears
entities) but never cancels per-room timers — `RoomScope.dispose()` makes that
explicit and uniform.

---

## 3. Door model: gated vs free

The connector already produces a sealable door slice (`toDoor`) — the new room's
entrance wall. The "lock" on room **N** is simply room **N+1's entrance**, kept
filled until N is solved.

Changes to `buildRoom` / segment generation:

- Build the bridge + tunnel as today, **but** decide per the *current* room's
  challenge whether its forward door starts open or sealed:
  - Current room is **free** → carve the forward door open at build time (today's
    behavior).
  - Current room is **gated** → after carving the tunnel, immediately re-fill the
    forward door slice (seal it), and record that door box on the current room as
    its `lockDoor`.
- `RoomNode` gains a forward-door reference so the manager can open it:
  - add `Location lockDoorMin/Max` (the box to carve on completion), and
  - a transient `RoomChallenge challenge` + a `boolean unlocked` flag.
- On `onComplete`, the manager carves `lockDoor` open (queued) and sets
  `unlocked = true`.

Because the whole segment is still built ahead of time (FIFO queue guarantees a
door only opens once its room exists), the player simply meets sealed doors at
gated rooms and walks freely through free rooms.

Checkpoint interaction: the last room of a segment is the emerald checkpoint and
stays a checkpoint (not a normal gate). A gated room may sit anywhere in the
segment before it.

---

## 4. Room enter/leave detection

Needed for effect rooms, lock activation, and co-op presence. Cheap and
sufficient: a point-in-bbox test against `RoomNode.roomMin/Max` (inclusive of the
door region), evaluated for each player on the manager tick.

- The manager keeps `Map<UUID, RoomNode> currentRoom` per player.
- Each tick, recompute the room each player is in; diff against `currentRoom` to
  fire `onPlayerLeave(old)` / `onPlayerEnter(new)`, and `activate(new)` the first
  time any player enters an un-activated room.
- Multi-cell rooms are a single rectangle, so one bbox test covers them.

We already poll every 5 ticks; effect application and presence at that cadence is
fine. Lava-wave timing runs on its own scheduled task (finer granularity).

---

## 5. Co-op gated advancement (sliding-window safety)

The conflict today: `advance` deletes the segment behind the checkpoint when
**any** player touches the emerald — a friend mid-segment falls into the void.
New rules:

1. **Advance gate:** advance only when *every living player* in the instance is
   within the checkpoint room's bbox (and at least one is on the emerald). Until
   then, show a bossbar/action-bar: "Waiting for players (2/3)".
2. **Straggler pull:** on advance, before `removeTail`, teleport any player not
   yet in the checkpoint room into it (they're being swept; pull them to safety).
3. **Shared lock state:** a gated room's completion is shared — once solved, the
   door is open for all; players who join late or re-enter see it unlocked.
4. **Join API:** add `DungeonManager.joinDungeon(player, host)` to add a player
   to an existing instance (the data model already holds a player list; only the
   entry path is missing). Joiners must also enter empty-handed and are teleported
   to the host's current room (or spawn if mid-build).
5. **Leave/death/quit:** unchanged per-player, but disposal happens only when the
   instance is truly empty (already handled).

---

## 6. Manager tick loop, revised

Per active instance, each tick:

1. Detach players who left the world; dispose if empty (unchanged).
2. Recompute per-player current room; fire enter/leave/activate (§4).
3. `tick()` every *active, incomplete* room that has players in it; if it just
   became complete, run `onComplete` and open its `lockDoor` (§3).
4. If not `extending` and the advance gate is satisfied (§5.1), `advanceDungeon`.
5. "Generating the area ahead…" action bar while the queue is busy (unchanged).

Only rooms with players inside are ticked, so cost scales with party size, not
dungeon length.

---

## 7. Vertical challenges (height) — v1 compromise {#vertical-challenges}

Parkour-to-gold wants verticality, but multi-level doors would break the
single-Y-plane snake (the connector's door Y is fixed). For v1:

- Keep **all doors at floor level** on one plane.
- A "tall" room simply raises its **ceiling** (per-room height — `RoomNode`
  already stores `roomMax`, so this is a build-time choice, not a connector
  change). Parkour climbs to an objective (gold block / button) high in the room;
  hitting it opens the **floor-level** forward door. Player descends and exits.
- `FLOOR_IS_LAVA` needs no extra height — it's a floor mechanic on the standard
  room.

Multi-level doors and true vertical shafts are deferred (they require connector
work) and noted as a future enhancement.

---

## 8. Vertical slice to ship first

Implement the abstraction (§1–§6) plus **three** challenges that exercise every
moving part:

1. **`EFFECT_BUFF` (free):** enter → apply Speed/Jump; leave → strip. Exercises
   enter/leave + per-player effects, no entities/doors.
2. **`CLEAR_MOBS` (gated):** activate → `scope.spawn` a small wave targeting the
   room; complete when all tracked mobs are dead → open forward door. Exercises
   the entity registry, gating, and teardown leak-proofing.
3. **`FIND_KEY` (gated):** a tagged key item hidden in a chest/under a block;
   detected at the door (or used on a "keyhole" lever) → open forward door.
   Exercises tagged items + a non-combat gate.

These three prove: free vs gated doors, enter/leave, entity + task teardown, and
co-op shared completion. Everything else (`FLOOR_IS_LAVA`, parkour, button
sequence, sculk stealth, etc.) is then "just another `RoomChallenge`."

---

## 9. Build/performance notes

- Layout *planning* runs synchronously in `advanceDungeon`. Choosing challenge
  types + building richer rooms adds work; keep an eye on the per-advance tick
  cost and chunk the planning across ticks if it spikes.
- Challenge `build` must use the queue (never direct bulk placement) to stay
  within the per-tick budget.
- `tick()` runs only for occupied, active rooms — bounded by party size.

## 10. Open questions / suggested defaults

- **Per-instance seeded `Random`.** Today a single shared `Random` is used.
  Switching to a per-instance seed (stored on `Dungeon`) buys reproducible /
  shareable runs (daily seed) for free and isolates instances. *Suggest: do it
  as part of this phase.*
- **Failure semantics.** What happens when a gated challenge is failed (e.g.
  touch lava, fall in parkour)? Options: respawn at the room's entrance and
  reset the challenge, vs. end the run. *Suggest: reset-at-room-entrance for
  movement challenges; document per challenge.*
- **Rewards.** `onComplete` is the natural hook for the existing key/currency
  reward system. *Suggest: a small reward per gated room, larger at checkpoints.*
- **Gated-room density.** Weighting in the challenge table. *Suggest: ~1 in 4
  rooms gated to start; tune from playtests.*

---

## Touch list (when we implement)

- `objects/RoomNode` — add `challenge`, `lockDoorMin/Max`, `unlocked`, height.
- `objects/Dungeon` — per-instance `Random`, `currentRoom` map, scope ownership.
- New: `challenge/RoomChallenge`, `challenge/ChallengeType`,
  `challenge/RoomContext`, `challenge/RoomScope`, `challenge/ChallengeFactory`,
  and the three v1 challenge classes.
- `factories/DungeonLayoutGenerator` — pick + attach challenges; seal forward
  doors for gated rooms; per-room height.
- `managers/DungeonManager` — enter/leave detection, room ticking, co-op advance
  gate + straggler pull, `joinDungeon`, leak-proof disposal via scopes.
- `listeners/DungeonProtectionListener` — feed relevant events (entity death,
  player interact, move) to the active room's challenge.
