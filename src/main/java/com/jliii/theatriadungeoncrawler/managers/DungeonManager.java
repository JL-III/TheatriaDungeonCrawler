package com.jliii.theatriadungeoncrawler.managers;

import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.factories.DungeonLayoutGenerator;
import com.jliii.theatriadungeoncrawler.factories.WorldFactory;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.DungeonGrid;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Owns the lifecycle of dungeon instances: one void world per run, into which an
 * infinite sliding-window dungeon is generated and grown.
 *
 * <p>Each player gets their own instance world (Phase 1). Stepping on the
 * emerald checkpoint <em>advances</em> the same world — building the next room
 * and trimming the oldest — rather than creating a new world, so the server is
 * not churning worlds as the player progresses.</p>
 */
public class DungeonManager {

    /** Y level of the dungeon floor (the build origin) in every instance world. */
    private static final int ORIGIN_Y = 64;
    /** Default window (active room count) if the player doesn't specify one. */
    private static final int DEFAULT_WINDOW = 6;

    private final Plugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Dungeon> instancesById = new HashMap<>();
    private final Map<UUID, Dungeon> instanceByPlayer = new HashMap<>();

    public DungeonManager(Plugin plugin) {
        this.plugin = plugin;
        // Poll a few times per second for a player standing on the emerald.
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkCheckpoints, 20L, 5L);
    }

    /**
     * Starts a new endless dungeon for the player, remembering where they came
     * from. If they are already in a dungeon they are removed from it first.
     *
     * @param window number of rooms kept alive at once (clamped to at least 2);
     *               pass a value &lt;= 0 to use the default
     */
    public void startDungeon(Player player, int window, DungeonTemplate.DungeonType theme) {
        if (instanceByPlayer.containsKey(player.getUniqueId())) {
            leaveDungeon(player);
        }

        int windowSize = window <= 0 ? DEFAULT_WINDOW : Math.max(2, window);
        Dungeon dungeon = createInstance(windowSize, theme);
        if (dungeon == null) {
            player.sendMessage("Failed to create the dungeon world. Try again.");
            return;
        }

        addPlayer(dungeon, player, player.getLocation());
        player.sendMessage("Entering an endless dungeon. Step on the emerald block to open the next room!");
    }

    /**
     * Removes the player from their current dungeon and returns them to where
     * they entered from. Disposes the instance once it is empty.
     */
    public void leaveDungeon(Player player) {
        Dungeon dungeon = instanceByPlayer.remove(player.getUniqueId());
        if (dungeon == null) {
            player.sendMessage("You are not in a dungeon.");
            return;
        }
        Location returnTo = dungeon.removePlayer(player.getUniqueId());
        teleportToSafety(player, returnTo);
        player.sendMessage("You have left the dungeon.");
        if (dungeon.isEmpty()) {
            disposeInstance(dungeon);
        }
    }

    /**
     * Handles a player disconnecting while inside a dungeon: drop them from the
     * instance (disposing it if empty) so the world can be cleaned up.
     */
    public void handleQuit(UUID playerId) {
        Dungeon dungeon = instanceByPlayer.remove(playerId);
        if (dungeon == null) {
            return;
        }
        dungeon.removePlayer(playerId);
        if (dungeon.isEmpty()) {
            disposeInstance(dungeon);
        }
    }

    /**
     * @return {@code true} if the world is a live dungeon instance world.
     */
    public boolean isDungeonWorld(World world) {
        for (Dungeon dungeon : instancesById.values()) {
            if (dungeon.getWorld().equals(world)) {
                return true;
            }
        }
        return false;
    }

    /** Disposes every active instance, returning players to safety. Call on disable. */
    public void disposeAll() {
        for (Dungeon dungeon : new ArrayList<>(instancesById.values())) {
            disposeInstance(dungeon);
        }
    }

    // --- internals ---------------------------------------------------------

    /**
     * Creates the world, schedules its build queue, generates the initial path,
     * and registers the instance. Does not add any players.
     */
    private Dungeon createInstance(int windowSize, DungeonTemplate.DungeonType theme) {
        World world = WorldFactory.createInstanceWorld();
        if (world == null) {
            return null;
        }

        Dungeon dungeon = new Dungeon(world, windowSize, theme);

        // Each instance builds on its own queue so instances never block each other.
        int buildTaskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, dungeon.getWorkloadRunnable(), 1L, 1L)
                .getTaskId();
        dungeon.setBuildTaskId(buildTaskId);

        Location origin = new Location(world, 0, ORIGIN_Y, 0);
        DungeonLayoutGenerator generator = new DungeonLayoutGenerator(world, dungeon.getWorkloadRunnable());
        DungeonGrid grid = generator.generateInitial(origin, windowSize, theme, random);
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

    /** Advances any instance whose player is standing on the emerald checkpoint. */
    private void checkCheckpoints() {
        for (Dungeon dungeon : new ArrayList<>(instancesById.values())) {
            if (dungeon.getState() != State.ACTIVE || dungeon.getGrid() == null || dungeon.isExtending()) {
                continue;
            }
            for (UUID playerId : dungeon.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && dungeon.getGrid().isOnEmerald(player.getLocation())) {
                    advanceDungeon(dungeon);
                    break;
                }
            }
        }
    }

    /** Extends the dungeon one room past the checkpoint the player just reached. */
    private void advanceDungeon(Dungeon dungeon) {
        dungeon.setExtending(true);
        try {
            DungeonGrid grid = dungeon.getGrid();
            DungeonLayoutGenerator generator =
                    new DungeonLayoutGenerator(grid.getWorld(), dungeon.getWorkloadRunnable());
            boolean extended = generator.advance(grid, random);
            String message = extended
                    ? "Checkpoint! Sealing the way back and opening the path ahead..."
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

    /**
     * Returns any remaining players to safety, then deletes the world.
     */
    private void disposeInstance(Dungeon dungeon) {
        for (UUID playerId : dungeon.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            Location returnTo = dungeon.getReturnLocation(playerId);
            instanceByPlayer.remove(playerId);
            if (player != null) {
                teleportToSafety(player, returnTo);
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

    private void teleportToSafety(Player player, Location returnTo) {
        if (returnTo != null && returnTo.getWorld() != null
                && !returnTo.getWorld().getName().startsWith(WorldFactory.INSTANCE_WORLD_PREFIX)) {
            player.teleport(returnTo);
        } else {
            player.teleport(mainWorldSpawn());
        }
    }

    private Location mainWorldSpawn() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
