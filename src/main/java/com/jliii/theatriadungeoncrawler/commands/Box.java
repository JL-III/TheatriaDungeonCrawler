package com.jliii.theatriadungeoncrawler.commands;

import com.jliii.theatriadungeoncrawler.TheatriaDungeonCrawler;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room2;
import com.jliii.theatriadungeoncrawler.runnables.DistributedFiller;
import com.jliii.theatriadungeoncrawler.runnables.WorkloadRunnable;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Box implements CommandExecutor {

    private TheatriaDungeonCrawler plugin;
    private WorkloadRunnable workloadRunnable;
    private List<Room2> rooms = new ArrayList<>();

    public Box(TheatriaDungeonCrawler plugin, WorkloadRunnable workloadRunnable) {
        this.plugin = plugin;
        this.workloadRunnable = workloadRunnable;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return false;
        }

        Player player = (Player) sender;
        World world = player.getWorld();

        if (args.length == 0) {
            sender.sendMessage("Usage: /box create [arguments]");
            return false;
        }

        if (args[0].equalsIgnoreCase("corridor")) {
            createCorridor(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length != 5) {
                player.sendMessage("Usage: /box create length height width theme");
                return true;
            }

            int length = Integer.parseInt(args[1]);
            int height = Integer.parseInt(args[2]);
            int width = Integer.parseInt(args[3]);
            DungeonTemplate.DungeonType dungeonType;

            try {
                dungeonType = DungeonTemplate.DungeonType.valueOf(args[4].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid theme. Make sure it is a valid theme type.");
                return true;
            }

            Location cornerA = player.getLocation().add(1, 0, 1); // Add an offset to not spawn the box inside the player
            Location cornerB = cornerA.clone().add(length, height, width);

            new DistributedFiller(this.workloadRunnable).fillHollowBox(cornerA, cornerB, dungeonType);
            Room2 room = new Room2(cornerA, cornerB);
            rooms.add(room);
            player.sendMessage("Created a themed hollow box. Entry point: " + room.getEntryPoint() + ", Corridor connection point: " + room.getCorridorConnectionPoint());
            return true;
        } else {
            sender.sendMessage("Invalid argument. Usage: /box create [arguments]");
        }

        if (args[0].equalsIgnoreCase("parkour")) {
            if (args.length != 5) {
                player.sendMessage("Usage: /box create length height width theme");
                return true;
            }

            int length = Integer.parseInt(args[1]);
            int height = Integer.parseInt(args[2]);
            int width = Integer.parseInt(args[3]);
            DungeonTemplate.DungeonType dungeonType;

            try {
                dungeonType = DungeonTemplate.DungeonType.valueOf(args[4].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid theme. Make sure it is a valid theme type.");
                return true;
            }

            Location cornerA = player.getLocation().add(1, 0, 1); // Add an offset to not spawn the box inside the player
            Location cornerB = cornerA.clone().add(length, height, width);

            new DistributedFiller(this.workloadRunnable).fillObstacleCourse(cornerA, cornerB, dungeonType);
            Room2 room = new Room2(cornerA, cornerB);
            rooms.add(room);
            player.sendMessage("Created a themed hollow box. Entry point: " + room.getEntryPoint() + ", Corridor connection point: " + room.getCorridorConnectionPoint());
            return true;
        } else {
            sender.sendMessage("Invalid argument. Usage: /box create [arguments]");
        }


        return true;
    }

    private void createCorridor(Player player) {
        Location playerLocation = player.getLocation();
        Room2 room = findRoomByPlayerLocation(playerLocation);

        if (room == null) {
            player.sendMessage("You are not standing in a valid room.");
            return;
        }

        Location entryPoint = room.getEntryPoint();
        Location corridorConnectionPoint = room.getCorridorConnectionPoint();

        // Set your desired corridor dimensions
        int corridorLength = 10;
        int corridorHeight = 4;
        int corridorWidth = 3;
        Material corridorMaterial = Material.STONE;

        // Calculate the start and end points of the corridor
        Location corridorStart = corridorConnectionPoint.clone().add(0, 1, 0); // Add an offset to create an opening in the wall
        Location corridorEnd = corridorStart.clone();

        // Check the direction of the corridor
        if (corridorConnectionPoint.getBlockX() == room.getCornerA().getBlockX()) {
            corridorEnd.add(-corridorLength, corridorHeight - 1, corridorWidth - 1);
        } else if (corridorConnectionPoint.getBlockX() == room.getCornerB().getBlockX()) {
            corridorEnd.add(corridorLength, corridorHeight - 1, corridorWidth - 1);
        } else if (corridorConnectionPoint.getBlockZ() == room.getCornerA().getBlockZ()) {
            corridorEnd.add(corridorWidth - 1, corridorHeight - 1, -corridorLength);
        } else {
            corridorEnd.add(corridorWidth - 1, corridorHeight - 1, corridorLength);
        }

        // Create the corridor
        new DistributedFiller(this.workloadRunnable).fillHollowCorridor(corridorStart, corridorEnd, corridorMaterial);
        player.sendMessage("Created a corridor with " + corridorMaterial.name() + " connecting to the room.");
    }


    private Room2 findRoomByPlayerLocation(Location playerLocation) {
        // This method should search for a room that contains the player's location
        // You should maintain a list of Room2 objects and check if any of them contain the player's location
        // You can compare the coordinates of the player's location to the coordinates of the room's corners

        // For example:
        for (Room2 room : rooms) {
            if (isLocationInsideRoom(playerLocation, room)) {
                return room;
            }
        }

        return null;
    }

    private boolean isLocationInsideRoom(Location location, Room2 room) {
        Location cornerA = room.getCornerA();
        Location cornerB = room.getCornerB();

        int x1 = Math.min(cornerA.getBlockX(), cornerB.getBlockX());
        int x2 = Math.max(cornerA.getBlockX(), cornerB.getBlockX());
        int y1 = Math.min(cornerA.getBlockY(), cornerB.getBlockY());
        int y2 = Math.max(cornerA.getBlockY(), cornerB.getBlockY());
        int z1 = Math.min(cornerA.getBlockZ(), cornerB.getBlockZ());
        int z2 = Math.max(cornerA.getBlockZ(), cornerB.getBlockZ());

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

}
