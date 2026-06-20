package com.jliii.theatriadungeoncrawler.managers;

import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.factories.DungeonLayoutGenerator;
import com.jliii.theatriadungeoncrawler.factories.WorldFactory;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.DungeonGrid;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
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
import java.util.Random;
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

    private final Plugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Dungeon> instancesById = new HashMap<>();
    private final Map<UUID, Dungeon> instanceByPlayer = new HashMap<>();

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
     * Handles a player dying inside a dungeon: the run ends, they keep what they
     * found (via the world's keep-inventory rule), and they respawn back in the
     * main world.
     *
     * @return the location the player should respawn at, or {@code null} if they
     *         were not in a dungeon
     */
    public Location handleDeath(Player player) {
        Dungeon dungeon = instanceByPlayer.get(player.getUniqueId());
        if (dungeon == null) {
            return null;
        }
        Location returnTo = detachPlayer(dungeon, player);
        // Dispose next tick, after the respawn has moved the player out.
        Bukkit.getScheduler().runTask(plugin, () -> disposeIfEmpty(dungeon));
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
                .runTaskTimer(plugin, dungeon.getWorkloadRunnable(), 1L, 1L)
                .getTaskId();
        dungeon.setBuildTaskId(buildTaskId);

        Location origin = new Location(world, 0, ORIGIN_Y, 0);
        DungeonLayoutGenerator generator = new DungeonLayoutGenerator(world, dungeon.getWorkloadRunnable());
        DungeonGrid grid = generator.generateInitial(origin, fixedSegmentLength, theme, random);
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
            if (dungeon.getWorkloadRunnable().isBusy()) {
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
            DungeonLayoutGenerator generator =
                    new DungeonLayoutGenerator(grid.getWorld(), dungeon.getWorkloadRunnable());
            boolean extended = generator.advance(grid, random);
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

    /** Returns any remaining players to safety, then deletes the world. */
    private void disposeInstance(Dungeon dungeon) {
        for (UUID playerId : dungeon.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            Location returnTo = dungeon.getReturnLocation(playerId);
            instanceByPlayer.remove(playerId);
            if (player != null) {
                sendToSafety(player, returnTo);
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
