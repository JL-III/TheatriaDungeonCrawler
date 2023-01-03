package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class Signs implements Listener {

    private Plugin plugin;
    DungeonMaster dungeonMaster;

    public Signs(Plugin plugin, DungeonMaster dungeonMaster) {
        this.plugin = plugin;
        this.dungeonMaster = dungeonMaster;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (event.getPlayer().hasPermission("theatira.dungeons.admin.sign.create")) {
            if (event.line(0) != null  && PlainTextComponentSerializer.plainText().serialize(event.line(0)).equals("[Dungeons]")) {
                for (String key : dungeonMaster.getDungeonKeys()) {
                    if (event.line(1) != null && PlainTextComponentSerializer.plainText().serialize(event.line(1)).equals(key)) {
                        event.setLine(1, dungeonMaster.getDungeonByKey(key).getKey());
                        event.setLine(2, dungeonMaster.getDungeonByKey(key).getGameState().name());
                        event.setLine(3, "Players: " + dungeonMaster.getDungeonByKey(key).getAllPlayersInGame().size());
                        GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + key + ".dungeon-coords.join-sign-locations." + GeneralUtils.getLocationKey(event.getBlock().getLocation()), event.getBlock().getLocation());
                        plugin.saveConfig();
                        dungeonMaster.updateSignsAndLocations();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSignDestroy(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("theatria.dungeons.admin.sign.destroy") && event.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            if (event.getBlock().getType() == Material.OAK_WALL_SIGN) {
                Sign sign = (Sign) event.getBlock().getState();
                if (sign.line(0) != null && PlainTextComponentSerializer.plainText().serialize(sign.line(0)).equals("[Dungeons]")) {
                    for (String key : dungeonMaster.getDungeonKeys()) {
                        if (sign.line(1) != null && PlainTextComponentSerializer.plainText().serialize(sign.line(1)).equals(key)) {
                            plugin.getConfig().set("dungeons." + key + ".dungeon-coords.join-sign-locations." + GeneralUtils.getLocationKey(event.getBlock().getLocation()), null);
                            plugin.saveConfig();
                            dungeonMaster.updateSignsAndLocations();
                        }
                    }
                }
            }
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!event.getAction().isLeftClick()) return;
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.OAK_WALL_SIGN) {
            Sign sign = (Sign) event.getClickedBlock().getState();
            if (PlainTextComponentSerializer.plainText().serialize(sign.line(0)).equals("[Dungeons]") && dungeonMaster.getDungeonKeys().contains(PlainTextComponentSerializer.plainText().serialize(sign.line(1)))) {
                String dungeonKey = PlainTextComponentSerializer.plainText().serialize(sign.line(1));
                Dungeon dungeon = dungeonMaster.getDungeonByKey(dungeonKey);
                dungeon.addToPlayersInGame(player);
                sign.setLine(3, "Players: " + dungeon.getAllPlayersInGame().size());
                sign.update(true);
                if (dungeon.getGameState().name().equalsIgnoreCase("active")) {
                    player.getInventory().clear();
                    player.teleport(dungeon.getSpawnLocations().get(0));
                }
                dungeonMaster.updateSigns();
            }
        }
    }

}
