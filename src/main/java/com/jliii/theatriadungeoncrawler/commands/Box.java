package com.jliii.theatriadungeoncrawler.commands;

import com.jliii.theatriadungeoncrawler.TheatriaDungeonCrawler;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room2;
import com.jliii.theatriadungeoncrawler.runnables.DistributedFiller;
import com.jliii.theatriadungeoncrawler.runnables.WorkloadRunnable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Box implements CommandExecutor {

    private TheatriaDungeonCrawler plugin;
    private WorkloadRunnable workloadRunnable;

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

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length != 5) {
                player.sendMessage("Usage: /box create length height width material");
                return true;
            }

            int length = Integer.parseInt(args[1]);
            int height = Integer.parseInt(args[2]);
            int width = Integer.parseInt(args[3]);
            Material material = Material.matchMaterial(args[4]);
            if (material == null || !material.isBlock()) {
                player.sendMessage("Invalid material. Make sure it is a valid block type.");
                return true;
            }

            Location cornerA = player.getLocation().add(1, 0, 1); // Add an offset to not spawn the box inside the player
            Location cornerB = cornerA.clone().add(length, height, width);

            new DistributedFiller(this.workloadRunnable).fillHollowBox(cornerA, cornerB, material);
            Room2 room = new Room2(cornerA, cornerB);
            player.sendMessage("Created a hollow box with " + material.name() + ". Entry point: " + room.getEntryPoint() + ", Corridor connection point: " + room.getCorridorConnectionPoint());
            return true;
        } else {
            sender.sendMessage("Invalid argument. Usage: /box create [arguments]");
        }

        return true;
    }
}
