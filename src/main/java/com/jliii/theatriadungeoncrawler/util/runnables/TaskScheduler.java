package com.jliii.theatriadungeoncrawler.util.runnables;

import org.bukkit.scheduler.BukkitTask;

public interface TaskScheduler {
    BukkitTask scheduleTask(Runnable runnable, long delay, long period);
}
