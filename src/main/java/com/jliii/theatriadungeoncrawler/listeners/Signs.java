package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
                    }
                }
            }
//            else if (event.line(0) != null  && PlainTextComponentSerializer.plainText().serialize(event.line(0)).equals("[Koth-Door]")) {
//                event.getPlayer().sendMessage("You just placed a door sign!");
//                for (String key : dungeonMaster.getDungeonKeys()) {
//                    if (event.line(1) != null && PlainTextComponentSerializer.plainText().serialize(event.line(1)).equals(key)) {
//                        event.setLine(1, dungeonMaster.getDungeonByKey(key).getKey());
//                        List<Location> doorSignLocations = (List<Location>) plugin.getConfig().get("arenas." + key + ".coords.doorsigns");
//                        doorSignLocations.add(event.getBlock().getLocation());
//                        plugin.getConfig().set("arenas." + key + ".coords.doorsigns", doorSignLocations);
//                        plugin.saveConfig();
//                    }
//                }
//            }
        }
    }

    @EventHandler
    public void onSignDestroy(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("theatria.dungeons.admin.sign.destroy")) {
            if (event.getBlock().getType() == Material.OAK_WALL_SIGN) {
                Sign sign = (Sign) event.getBlock().getState();
                if (sign.line(0) != null && PlainTextComponentSerializer.plainText().serialize(sign.line(0)).equals("[Dungeons]")) {
                    for (String key : dungeonMaster.getDungeonKeys()) {
                        if (sign.line(1) != null && PlainTextComponentSerializer.plainText().serialize(sign.line(1)).equals(key)) {
                            //Todo will need a whole new implementation to find the key that belongs to the sign that is being destroyed
                            plugin.getConfig().set("dungeons." + key + ".dungeon-coords.join-sign-locations." + GeneralUtils.getLocationKey(event.getBlock().getLocation()), null);
                            plugin.saveConfig();
                        }
                    }
                }
//                else if (sign.line(0) != null  && PlainTextComponentSerializer.plainText().serialize(sign.line(0)).equals("[Koth-Door]")) {
//                    for (String key : dungeonMaster.getDungeonKeys()) {
//                        if (sign.line(1) != null && PlainTextComponentSerializer.plainText().serialize(sign.line(1)).equals(key)) {
//                            List<Location> doorSignLocations = (List<Location>) plugin.getConfig().get("arenas." + key + ".coords.doorsigns");
//                            doorSignLocations.remove(event.getBlock().getLocation());
//                            plugin.getConfig().set("arenas." + key + ".coords.doorsigns", doorSignLocations);
//                        }
//                        plugin.saveConfig();
//                    }
//
//                }
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
                dungeonMaster.getDungeonByKey(PlainTextComponentSerializer.plainText().serialize(sign.line(1))).addToPlayersInGame(player);
                sign.setLine(3, "Players: " + dungeonMaster.getDungeonByKey(PlainTextComponentSerializer.plainText().serialize(sign.line(1))).getAllPlayersInGame().size());
                sign.update(true);
                if (dungeonMaster.getDungeonByKey(PlainTextComponentSerializer.plainText().serialize(sign.line(1))).getGameState().name().equalsIgnoreCase("active")) {
//                    dungeonMaster.getDungeonByKey(PlainTextComponentSerializer.plainText().serialize(sign.line(1))).getGameState().getGameTimerBossBar().addPlayer(player);
//                    player.setScoreboard(dungeonMaster.getDungeonByKey(PlainTextComponentSerializer.plainText().serialize(sign.line(1))).getGame().getScoreboard());
                    player.getInventory().clear();
//                    PlayerKit.GivePlayerKit(player);
                    player.teleport(dungeonMaster.getDungeonByKey(PlainTextComponentSerializer.plainText().serialize(sign.line(1))).getSpawnLocations().get(0));
                }
                dungeonMaster.updateSigns();
            }
        }
    }

}
