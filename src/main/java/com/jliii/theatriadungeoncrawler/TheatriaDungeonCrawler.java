package com.jliii.theatriadungeoncrawler;

import com.jliii.theatriadungeoncrawler.commands.Box;
import com.jliii.theatriadungeoncrawler.commands.DungeonCommands;
import com.jliii.theatriadungeoncrawler.factories.WorldFactory;
import com.jliii.theatriadungeoncrawler.listeners.DungeonProtectionListener;
import com.jliii.theatriadungeoncrawler.listeners.PlayerConnectionListener;
import com.jliii.theatriadungeoncrawler.managers.DungeonManager;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadQueue;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class TheatriaDungeonCrawler extends JavaPlugin {

    private final WorkloadQueue workloadQueue = new WorkloadQueue();
    private DungeonManager dungeonManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Remove any instance worlds left behind by a previous run/crash.
        int purged = WorldFactory.purgeOrphanedWorlds();
        if (purged > 0) {
            getLogger().info("Removed " + purged + " orphaned dungeon world(s) from a previous session.");
        }

        dungeonManager = new DungeonManager(this);

        // Shared debug workload queue used by the /box command (manual stepping).
        workloadQueue.setManualExecution(true);
        Bukkit.getScheduler().runTaskTimer(this, this.workloadQueue, 1, 1);

        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(dungeonManager), this);
        Bukkit.getPluginManager().registerEvents(new DungeonProtectionListener(dungeonManager), this);
        Objects.requireNonNull(Bukkit.getPluginCommand("dungeons"))
                .setExecutor(new DungeonCommands(this, dungeonManager));
        Objects.requireNonNull(Bukkit.getPluginCommand("box"))
                .setExecutor(new Box(this, workloadQueue));
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            dungeonManager.disposeAll();
        }
    }
}
