package com.jliii.theatriadungeoncrawler.util.runnables;


import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class BukkitTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    public BukkitTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public BukkitTask scheduleTask(Runnable runnable, long delay, long period) {
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
    }
}
