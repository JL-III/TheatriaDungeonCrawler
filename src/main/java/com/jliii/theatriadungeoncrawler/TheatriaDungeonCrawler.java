package com.jliii.theatriadungeoncrawler;

import com.jliii.theatriadungeoncrawler.commands.AdminCommands;
import com.jliii.theatriadungeoncrawler.commands.Box;
import com.jliii.theatriadungeoncrawler.factories.DungeonFactory;
import com.jliii.theatriadungeoncrawler.listeners.PlayerListener;
import com.jliii.theatriadungeoncrawler.listeners.Signs;
import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.runnables.WorkloadRunnable;
import com.jliii.theatriadungeoncrawler.util.BukkitTaskScheduler;
import com.jliii.theatriadungeoncrawler.util.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class TheatriaDungeonCrawler extends JavaPlugin {

    private final WorkloadRunnable workloadRunnable = new WorkloadRunnable();


    @Override
    public void onEnable() {
        // Plugin startup logic
        //TODO remove the workload runnable that is here
        saveDefaultConfig();
        TaskScheduler taskScheduler = new BukkitTaskScheduler(this);
        DungeonFactory dungeonFactory = new DungeonFactory(taskScheduler);
        workloadRunnable.setManualExecution(true);
        Bukkit.getScheduler().runTaskTimer(this, this.workloadRunnable, 1, 1);
        DungeonMaster dungeonMaster = new DungeonMaster(this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(dungeonMaster), this);
        Bukkit.getPluginManager().registerEvents(new Signs(this, dungeonMaster), this);
        Objects.requireNonNull(Bukkit.getPluginCommand("dungeons")).setExecutor(new AdminCommands(this, dungeonMaster));
        Objects.requireNonNull(Bukkit.getPluginCommand("box")).setExecutor(new Box(this, workloadRunnable));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
