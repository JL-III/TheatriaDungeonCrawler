package com.jliii.theatriadungeoncrawler.managers;

import com.jliii.theatriadungeoncrawler.challenge.ChallengeState;
import com.jliii.theatriadungeoncrawler.challenge.RoomChallenge;
import com.jliii.theatriadungeoncrawler.challenge.RoomContext;
import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.factories.DungeonLayoutGenerator;
import com.jliii.theatriadungeoncrawler.factories.WorldFactory;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.DungeonGrid;
import com.jliii.theatriadungeoncrawler.objects.RoomNode;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.runnables.DungeonBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the lifecycle of dungeon instances: one void world per run, into which an
 * infinite, segment-by-segment dungeon is generated and grown.
 *
 * <p>Players enter empty-handed (so nothing brought in can be lost); the dungeon
 * is protected from breaking/placing by event cancellation, so no game-mode
 * changes are needed. Players are returned to the main world on leaving, dying,
 * disconnecting, or wandering out. Instance state is kept entirely in memory: a
 * crash simply leaves orphaned worlds, which are purged on the next startup.</p>
 */
public class DungeonManager {

    /** Y level of the dungeon floor (the build origin) in every instance world. */
    private static final int ORIGIN_Y = 64;

    // Run-score weights. Depth dominates so lingering can never out-earn progress:
    // one new room (DEPTH_WEIGHT) is worth far more than the time spent reaching it.
    private static final long DEPTH_WEIGHT = 100;
    private static final long TIME_WEIGHT = 1;
    private static final long TIME_UNIT_SECONDS = 10;

    private final Plugin plugin;
    private final Map<UUID, Dungeon> instancesById = new HashMap<>();
    private final Map<UUID, Dungeon> instanceByPlayer = new HashMap<>();
    /** The room each player is currently standing in (for enter/leave events). */
    private final Map<UUID, RoomNode> currentRoom = new HashMap<>();

    public DungeonManager(Plugin plugin) {
        this.plugin = plugin;
        // Poll a few times per second: advance checkpoints and detach stragglers.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickInstances, 20L, 5L);
    }

    /**
     * Starts a new endless dungeon for the player. Requires an empty inventory
     * and armor, and remembers where they came from. If they are already in a
     * dungeon they are removed from it first.
     *
     * @param fixedSegmentLength forces the rooms-per-segment count; pass a value
     *                           &lt;= 0 to use a random 7-15 per segment
     */
    public void startDungeon(Player player, int fixedSegmentLength, DungeonTemplate.DungeonType theme) {
        if (instanceByPlayer.containsKey(player.getUniqueId())) {
            leaveDungeon(player);
        }

        if (!isInventoryEmpty(player)) {
            player.sendMessage("Empty your inventory and armor before entering the dungeon.");
            return;
        }

        Dungeon dungeon = createInstance(fixedSegmentLength, theme);
        if (dungeon == null) {
            player.sendMessage("Failed to create the dungeon world. Try again.");
            return;
        }

        addPlayer(dungeon, player, player.getLocation());
        player.sendMessage("Entering an endless dungeon. Reach the emerald checkpoint at the end of each stretch of rooms.");
    }

    /**
     * Removes the player from their current dungeon and returns them, in
     * survival mode, to where they entered from. Disposes the instance once it
     * is empty.
     */
    public void leaveDungeon(Player player) {
        Dungeon dungeon = instanceByPlayer.get(player.getUniqueId());
        if (dungeon == null) {
            player.sendMessage("You are not in a dungeon.");
            return;
        }
        Location returnTo = detachPlayer(dungeon, player);
        sendToSafety(player, returnTo);
        player.sendMessage("You have left the dungeon.");
        disposeIfEmpty(dungeon);
    }

    /**
     * Handles a player disconnecting while inside a dungeon: drop them from the
     * instance and dispose it (next tick, once they have fully left the world).
     */
    public void handleQuit(Player player) {
        Dungeon dungeon = instanceByPlayer.get(player.getUniqueId());
        if (dungeon == null) {
            return;
        }
        detachPlayer(dungeon, player);
        Bukkit.getScheduler().runTask(plugin, () -> disposeIfEmpty(dungeon));
    }

    /**
     * Handles a player dying inside a dungeon. A death spends one of the run's
     * shared lives. While lives remain the player respawns at the last checkpoint
     * and the run continues; when the pool is exhausted the run ends and everyone
     * is returned to the main world (keeping what they found, via keep-inventory).
     *
     * @return where the dying player should respawn, or {@code null} if they were
     *         not in a dungeon (leave the vanilla respawn location alone)
     */
    public Location handleDeath(Player player) {
        Dungeon dungeon = instanceByPlayer.get(player.getUniqueId());
        if (dungeon == null) {
            return null;
        }
        // The run already ended this tick (e.g. a second player died at 0 lives);
        // just eject this player without re-running the end-of-run handling.
        if (dungeon.getState() != State.ACTIVE) {
            return safeLocation(detachPlayer(dungeon, player));
        }

        int livesLeft = dungeon.decrementLife();
        if (livesLeft <= 0) {
            return endRun(dungeon, player);
        }

        // Respawn at the checkpoint and stay in the run. Re-detect their room next
        // tick so entering the checkpoint room fires cleanly.
        currentRoom.remove(player.getUniqueId());
        announce(dungeon, player.getName() + " died — " + livesLeft
                + (livesLeft == 1 ? " life" : " lives") + " remaining.");
        DungeonGrid grid = dungeon.getGrid();
        Location anchor = grid != null ? grid.getCheckpointSpawn() : null;
        return anchor != null ? withFacing(anchor, player) : null;
    }

    /**
     * Ends a run because the shared life pool is empty: announces the final
     * score, detaches the dying player (handled by the respawn event), and
     * disposes the instance next tick so the rest of the party is ejected too.
     *
     * @return the dying player's main-world respawn location
     */
    private Location endRun(Dungeon dungeon, Player dyingPlayer) {
        // Mark the run ended immediately so a same-tick second death (or the next
        // tickInstances pass) cannot re-enter end-of-run handling or advance it.
        dungeon.setState(State.OFF);
        long score = computeScore(dungeon);
        announce(dungeon, "Out of lives! Run over — final score " + score
                + " (depth " + dungeon.getDepth() + ").");
        Location returnTo = detachPlayer(dungeon, dyingPlayer);
        Bukkit.getScheduler().runTask(plugin, () -> disposeInstance(dungeon));
        return safeLocation(returnTo);
    }

    /** @return {@code true} if the player is currently inside a dungeon instance. */
    public boolean isParticipant(Player player) {
        return instanceByPlayer.containsKey(player.getUniqueId());
    }

    /** Disposes every active instance, returning players to safety. Call on disable. */
    public void disposeAll() {
        for (Dungeon dungeon : new ArrayList<>(instancesById.values())) {
            disposeInstance(dungeon);
        }
    }

    // --- internals ---------------------------------------------------------

    private Dungeon createInstance(int fixedSegmentLength, DungeonTemplate.DungeonType theme) {
        World world = WorldFactory.createInstanceWorld();
        if (world == null) {
            return null;
        }

        Dungeon dungeon = new Dungeon(world, fixedSegmentLength, theme);

        // Each instance builds on its own queue so instances never block each other.
        int buildTaskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, dungeon.getWorkloadQueue(), 1L, 1L)
                .getTaskId();
        dungeon.setBuildTaskId(buildTaskId);

        Location origin = new Location(world, 0, ORIGIN_Y, 0);
        DungeonLayoutGenerator generator = new DungeonLayoutGenerator(plugin, dungeon);
        DungeonGrid grid = generator.generateInitial(origin, fixedSegmentLength, theme, dungeon.getRandom());
        dungeon.setGrid(grid);
        dungeon.setState(State.ACTIVE);

        // Immediate safe floor under the spawn so the player doesn't fall while
        // the asynchronous build reaches the start-room floor.
        placeSpawnPlatform(grid.getSpawn());

        instancesById.put(dungeon.getUUID(), dungeon);
        return dungeon;
    }

    private void addPlayer(Dungeon dungeon, Player player, Location returnLocation) {
        dungeon.addPlayer(player.getUniqueId(), returnLocation);
        instanceByPlayer.put(player.getUniqueId(), dungeon);

        Location spawn = dungeon.getGrid().getSpawn();
        spawn.setYaw(player.getLocation().getYaw());
        spawn.setPitch(player.getLocation().getPitch());
        player.teleport(spawn);
    }

    /** Removes a player from an instance, returning their stored return location. */
    private Location detachPlayer(Dungeon dungeon, Player player) {
        instanceByPlayer.remove(player.getUniqueId());
        currentRoom.remove(player.getUniqueId());
        return dungeon.removePlayer(player.getUniqueId());
    }

    private void disposeIfEmpty(Dungeon dungeon) {
        if (dungeon.isEmpty()) {
            disposeInstance(dungeon);
        }
    }

    /**
     * Each tick: detach anyone who has left their instance's world by other
     * means, then advance any instance whose player is standing on the emerald.
     */
    private void tickInstances() {
        for (Dungeon dungeon : new ArrayList<>(instancesById.values())) {
            if (dungeon.getState() != State.ACTIVE || dungeon.getGrid() == null) {
                continue;
            }

            // A participant who is no longer in the instance world (e.g. /spawn)
            // has effectively left; drop them so the instance can be disposed.
            for (UUID playerId : dungeon.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && !player.getWorld().equals(dungeon.getWorld())) {
                    detachPlayer(dungeon, player);
                }
            }
            if (dungeon.isEmpty()) {
                disposeInstance(dungeon);
                continue;
            }

            // Let players know the area is still being built.
            if (dungeon.getWorkloadQueue().isBusy()) {
                for (UUID playerId : dungeon.getPlayers()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.sendActionBar(Component.text("Generating the area ahead..."));
                    }
                }
            }

            if (dungeon.isExtending()) {
                continue;
            }

            // Fire room enter/leave/activate, then update and complete challenges.
            processRoomTransitions(dungeon);
            tickActiveRooms(dungeon);
            for (UUID playerId : dungeon.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && dungeon.getGrid().isOnEmerald(player.getLocation())) {
                    advanceDungeon(dungeon);
                    break;
                }
            }
        }
    }

    /** Extends the dungeon a whole segment past the checkpoint the player reached. */
    private void advanceDungeon(Dungeon dungeon) {
        dungeon.setExtending(true);
        try {
            DungeonGrid grid = dungeon.getGrid();
            DungeonLayoutGenerator generator = new DungeonLayoutGenerator(plugin, dungeon);
            boolean extended = generator.advance(grid, dungeon.getRandom());
            String direction = grid.getLastExitDirection();
            String message = extended
                    ? "Checkpoint reached! The path opens to the "
                            + (direction != null ? direction : "unknown") + "."
                    : "The dungeon cannot extend any further from here.";
            for (UUID playerId : dungeon.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(message);
                }
            }
        } finally {
            dungeon.setExtending(false);
        }
    }

    // --- room challenge lifecycle -----------------------------------------

    /**
     * Detects each player crossing a room boundary and fires the corresponding
     * challenge callbacks: leave the old room, enter the new one, and activate it
     * the first time anyone steps into a not-yet-started gated room.
     */
    private void processRoomTransitions(Dungeon dungeon) {
        DungeonGrid grid = dungeon.getGrid();
        for (UUID playerId : dungeon.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomNode now = roomAt(grid, player.getLocation());
            RoomNode prev = currentRoom.get(playerId);
            if (now == prev) {
                continue;
            }

            // Left the previous room (only if it is still a live room).
            if (prev != null && prev.getChallenge() != null && grid.getPath().contains(prev)) {
                prev.getChallenge().onPlayerLeave(new RoomContext(dungeon, prev), player);
            }

            if (now == null) {
                currentRoom.put(playerId, null); // in a corridor / between rooms
                continue;
            }

            currentRoom.put(playerId, now);
            dungeon.reachRoom(now.getRoomId());
            RoomChallenge challenge = now.getChallenge();
            if (challenge == null) {
                continue;
            }
            RoomContext ctx = new RoomContext(dungeon, now);
            challenge.onPlayerEnter(ctx, player);
            if (now.getState() == ChallengeState.PENDING) {
                now.setState(ChallengeState.ACTIVE);
                challenge.activate(ctx);
            }
        }
    }

    /** Ticks every active challenge and completes any whose objective is met. */
    private void tickActiveRooms(Dungeon dungeon) {
        for (RoomNode room : dungeon.getGrid().getPath()) {
            RoomChallenge challenge = room.getChallenge();
            if (challenge == null || room.getState() != ChallengeState.ACTIVE) {
                continue;
            }
            RoomContext ctx = new RoomContext(dungeon, room);
            challenge.tick(ctx);
            if (challenge.isComplete(ctx)) {
                completeRoom(dungeon, room, challenge, ctx);
            }
        }
    }

    /** Runs a challenge's reward hook, opens its gated forward door, marks it done. */
    private void completeRoom(Dungeon dungeon, RoomNode room, RoomChallenge challenge, RoomContext ctx) {
        challenge.onComplete(ctx);
        if (room.hasLockDoor()) {
            new DungeonBuilder(dungeon.getWorkloadQueue())
                    .fillSolidBox(room.getLockDoorMin(), room.getLockDoorMax(), Material.AIR);
        }
        room.setState(ChallengeState.COMPLETE);
        announce(dungeon, "A path has opened.");
    }

    /** @return the room whose footprint contains {@code loc}, or {@code null}. */
    private RoomNode roomAt(DungeonGrid grid, Location loc) {
        for (RoomNode room : grid.getPath()) {
            if (footprintContains(room, loc)) {
                return room;
            }
        }
        return null;
    }

    /** Horizontal containment only — rooms never overlap in x/z, so it is exact. */
    private boolean footprintContains(RoomNode room, Location loc) {
        Location min = room.getRoomMin();
        Location max = room.getRoomMax();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= min.getBlockX() && x <= max.getBlockX()
                && z >= min.getBlockZ() && z <= max.getBlockZ();
    }

    private Location withFacing(Location base, Player player) {
        Location loc = base.clone();
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        return loc;
    }

    /** Sends a chat line to every player currently in the instance. */
    private void announce(Dungeon dungeon, String message) {
        for (UUID playerId : dungeon.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * The run score: depth dominates, survival time is a minor secondary term,
     * plus any reward bonuses. Weighted so idling can never out-earn progress.
     */
    private long computeScore(Dungeon dungeon) {
        long seconds = (System.currentTimeMillis() - dungeon.getRunStartMillis()) / 1000L;
        return dungeon.getDepth() * DEPTH_WEIGHT
                + (seconds / TIME_UNIT_SECONDS) * TIME_WEIGHT
                + dungeon.getScoreBonus();
    }

    /** Returns any remaining players to safety, then deletes the world. */
    private void disposeInstance(Dungeon dungeon) {
        for (UUID playerId : dungeon.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            Location returnTo = dungeon.getReturnLocation(playerId);
            instanceByPlayer.remove(playerId);
            currentRoom.remove(playerId);
            if (player != null) {
                sendToSafety(player, returnTo);
            }
        }
        // Tear down every room (challenge cleanup + scope disposal) before the
        // world is deleted, so nothing is left running. Mirrors removeTail's
        // teardown so both disposal paths run identical cleanup.
        if (dungeon.getGrid() != null) {
            for (RoomNode room : dungeon.getGrid().getPath()) {
                if (room.getChallenge() != null) {
                    room.getChallenge().teardown(new RoomContext(dungeon, room));
                }
                if (room.getScope() != null) {
                    room.getScope().dispose();
                }
            }
        }

        dungeon.setState(State.OFF);
        if (dungeon.getBuildTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(dungeon.getBuildTaskId());
        }
        instancesById.remove(dungeon.getUUID());
        WorldFactory.disposeWorld(dungeon.getWorld());
    }

    private void placeSpawnPlatform(Location spawn) {
        World world = spawn.getWorld();
        int cx = spawn.getBlockX();
        int cz = spawn.getBlockZ();
        int y = spawn.getBlockY() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, y, cz + dz).setType(Material.STONE);
            }
        }
    }

    private boolean isInventoryEmpty(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        return offHand == null || offHand.getType() == Material.AIR;
    }

    /** Teleports a player out of the dungeon to a safe location. */
    private void sendToSafety(Player player, Location returnTo) {
        player.teleport(safeLocation(returnTo));
    }

    private Location safeLocation(Location returnTo) {
        if (returnTo != null && returnTo.getWorld() != null
                && !returnTo.getWorld().getName().startsWith(WorldFactory.INSTANCE_WORLD_PREFIX)) {
            return returnTo;
        }
        return mainWorldSpawn();
    }

    private Location mainWorldSpawn() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
