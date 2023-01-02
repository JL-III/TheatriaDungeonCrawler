package com.jliii.theatriadungeoncrawler.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class GeneralUtils {

    public static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public static Location parseLocation(ConfigurationSection config, String path, World world) {
        String value = config.getString(path);
        if (value == null) return null;

        String[] parts = value.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("A location must be at least (x,y,z)");
        }
        Double x = Double.parseDouble(parts[0]);
        Double y = Double.parseDouble(parts[1]);
        Double z = Double.parseDouble(parts[2]);
        if (parts.length == 3) {
            return new Location(world, x, y, z);
        }
        if (parts.length < 5) {
            throw new IllegalArgumentException("Expected location of type (x,y,z,yaw,pitch)");
        }
        Float yaw = Float.parseFloat(parts[3]);
        Float pit = Float.parseFloat(parts[4]);
        if (world == null) {
            if (parts.length != 6) {
                throw new IllegalArgumentException("Expected location of type (x,y,z,yaw,pitch,world)");
            }
            world = Bukkit.getWorld(parts[5]);
            if (world == null) {
                throw new IllegalArgumentException("World " + parts[5] + " not found");
            }
        }
        return new Location(world, x, y, z, yaw, pit);
    }

    public static void setLocation(ConfigurationSection config, String path, Location location) {
        if (location == null) {
            config.set(path, null);
            return;
        }
        String x = twoPlaces(location.getX());
        String y = twoPlaces(location.getY());
        String z = twoPlaces(location.getZ());

        String yaw = twoPlaces(location.getYaw(),   true);
        String pit = twoPlaces(location.getPitch(), true);

        String world = location.getWorld().getName();

        String value = x + "," + y + "," + z + "," + yaw + "," + pit + "," + world;
        config.set(path, value);
    }

    private static String twoPlaces(double value, boolean force) {
        return force ? DF_FORCE.format(value) : DF_NORMAL.format(value);
    }

    private static String twoPlaces(double value) {
        return twoPlaces(value, false);
    }

    private static final DecimalFormat DF_NORMAL = new DecimalFormat("0.##");
    private static final DecimalFormat DF_FORCE  = new DecimalFormat("0.0#");
    static {
        DF_FORCE.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        DF_NORMAL.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    public static String getLocationKey(Location location) {
        return Math.round(location.getX()) + "," + Math.round(location.getY()) + "," + Math.round(location.getZ());
    }

}
