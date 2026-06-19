package com.jliii.theatriadungeoncrawler.managers;

import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.factories.DungeonLayoutGenerator;
import com.jliii.theatriadungeoncrawler.factories.WorldFactory;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.DungeonLayout;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Owns the full lifecycle of dungeon instances: creating a fresh void world,
 * generating a layout into it, moving players in, detecting completion, then
 * disposing of the world and (optionally) starting the next run.
 *
 * <p>Phase 1 supports a single player per instance and an automatic
 * "play again" loop: reaching the exit room generates a brand-new dungeon and
 * tears the old one down.</p>
 */
public class DungeonManager {

    /** Y level of the dungeon floor (the build origin) in every instance world. */
    private static final int ORIGIN_Y = 64;
    /** Delay (ticks) between completing a dungeon and the next one generating. */
    private static final long RESTART_DELAY_TICKS = 100L; // 5 seconds

    private final Plugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Dungeon> instancesById = new HashMap<>();
    private final Map<UUID, Dungeon> instanceByPlayer = new HashMap<>();

    public DungeonManager(Plugin plugin) {
        this.plugin = plugin;
        // Poll for players reaching the exit room a couple of times per second.
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkCompletions, 20L, 10L);
    }

    /**
     * Starts a new dungeon for the player, remembering where they came from so
     * they can be returned later. If the player is already in a dungeon they
     * are first removed from it.
     */
    public void startDungeon(Player player, int roomCount, DungeonTemplate.DungeonType theme) {
        if (instanceByPlayer.containsKey(player.getUniqueId())) {
            leaveDungeon(player);
        }

        Dungeon dungeon = createInstance(roomCount, theme);
        if (dungeon == null) {
            player.sendMessage("Failed to create the dungeon world. Try again.");
            return;
        }

        addPlayer(dungeon, player, player.getLocation());
        player.sendMessage("Entering a " + roomCount + "-room dungeon. Reach the emerald block to complete it!");
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
     * Creates the world, schedules its build, generates the layout, and
     * registers the instance. Does not add any players.
     */
    private Dungeon createInstance(int roomCount, DungeonTemplate.DungeonType theme) {
        World world = WorldFactory.createInstanceWorld();
        if (world == null) {
            return null;
        }

        Dungeon dungeon = new Dungeon(world, roomCount, theme);

        // Each instance builds on its own queue so instances never block each other.
        int buildTaskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, dungeon.getWorkloadRunnable(), 1L, 1L)
                .getTaskId();
        dungeon.setBuildTaskId(buildTaskId);

        Location origin = new Location(world, 0, ORIGIN_Y, 0);
        placeSpawnPlatform(world, origin);

        DungeonLayoutGenerator generator = new DungeonLayoutGenerator(world, dungeon.getWorkloadRunnable());
        DungeonLayout layout = generator.generate(origin, roomCount, theme, random);
        dungeon.setLayout(layout);
        dungeon.setState(State.ACTIVE);

        instancesById.put(dungeon.getUUID(), dungeon);
        return dungeon;
    }

    private void addPlayer(Dungeon dungeon, Player player, Location returnLocation) {
        dungeon.addPlayer(player.getUniqueId(), returnLocation);
        instanceByPlayer.put(player.getUniqueId(), dungeon);

        Location spawn = dungeon.getLayout().getSpawn();
        spawn.setYaw(player.getLocation().getYaw());
        spawn.setPitch(player.getLocation().getPitch());
        player.teleport(spawn);
    }

    /** Iterates active instances and triggers a win when a player reaches the exit. */
    private void checkCompletions() {
        for (Dungeon dungeon : new ArrayList<>(instancesById.values())) {
            if (dungeon.getState() != State.ACTIVE || dungeon.getLayout() == null) {
                continue;
            }
            for (UUID playerId : dungeon.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && dungeon.getLayout().isInsideExit(player.getLocation())) {
                    handleWin(dungeon);
                    break;
                }
            }
        }
    }

    private void handleWin(Dungeon dungeon) {
        dungeon.setState(State.WON);
        for (UUID playerId : dungeon.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("Dungeon complete! Generating your next dungeon...");
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> restart(dungeon), RESTART_DELAY_TICKS);
    }

    /**
     * Builds a fresh instance with the same parameters, moves the players into
     * it (preserving their original return locations), then disposes the old one.
     */
    private void restart(Dungeon oldDungeon) {
        // The instance may have been torn down (e.g. everyone left) before this ran.
        if (!instancesById.containsKey(oldDungeon.getUUID())) {
            return;
        }

        Dungeon newDungeon = createInstance(oldDungeon.getRoomCount(), oldDungeon.getTheme());
        if (newDungeon == null) {
            // Could not make a new world; just tear the old one down.
            disposeInstance(oldDungeon);
            return;
        }

        for (UUID playerId : oldDungeon.getPlayers()) {
            Location originalReturn = oldDungeon.getReturnLocation(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                addPlayer(newDungeon, player, originalReturn);
                player.sendMessage("Reach the emerald block to complete it!");
            }
        }

        // Players have been moved to the new world; safe to delete the old one.
        disposeWorldAndTasks(oldDungeon);
    }

    /**
     * Returns any remaining players to safety, then deletes the world. Use for
     * abandoning an instance (as opposed to handing players to a new one).
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
        disposeWorldAndTasks(dungeon);
    }

    private void disposeWorldAndTasks(Dungeon dungeon) {
        dungeon.setState(State.OFF);
        if (dungeon.getBuildTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(dungeon.getBuildTaskId());
        }
        instancesById.remove(dungeon.getUUID());
        WorldFactory.disposeWorld(dungeon.getWorld());
    }

    private void placeSpawnPlatform(World world, Location origin) {
        int cx = origin.getBlockX() + 4;
        int cz = origin.getBlockZ() + 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, origin.getBlockY(), cz + dz).setType(Material.STONE);
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
