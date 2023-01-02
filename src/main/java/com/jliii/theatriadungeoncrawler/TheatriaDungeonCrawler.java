package com.jliii.theatriadungeoncrawler;

import com.jliii.theatriadungeoncrawler.commands.AdminCommands;
import com.jliii.theatriadungeoncrawler.listeners.PlayerListener;
import com.jliii.theatriadungeoncrawler.listeners.Signs;
import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class TheatriaDungeonCrawler extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        DungeonMaster dungeonMaster = new DungeonMaster(this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(dungeonMaster), this);
        Bukkit.getPluginManager().registerEvents(new Signs(this, dungeonMaster), this);
        Bukkit.getPluginCommand("dungeon").setExecutor(new AdminCommands(this, dungeonMaster));

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
