package com.jliii.theatriadungeoncrawler.templates;

import org.bukkit.Material;

import java.util.Random;

public class DungeonTemplate {

    private static final Random RANDOM = new Random();

    public enum DungeonType {
        ICE(new Material[]{Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK, Material.SEA_LANTERN}),
        STONE(new Material[]{Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE, Material.GLOWSTONE, Material.CRACKED_STONE_BRICKS}),
        NETHER(new Material[]{Material.NETHERRACK, Material.CRIMSON_NYLIUM, Material.BASALT, Material.SHROOMLIGHT}),
        DESERT(new Material[]{Material.SANDSTONE, Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE, Material.GLOWSTONE}),
        OCEAN(new Material[]{Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.SEA_LANTERN, Material.DARK_PRISMARINE}),
        END(new Material[]{Material.END_STONE, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.PEARLESCENT_FROGLIGHT}),
        DEBUG(new Material[]{Material.RED_WOOL, Material.YELLOW_WOOL});

        private final Material[] materials;
        private int lastMaterialIndex = -1;

        DungeonType(Material[] materials) {
            this.materials = materials;
        }

        public Material[] getMaterials() {
            return materials;
        }
    }


    public static Material getRandomMaterial(DungeonType dungeonType) {
        if (dungeonType == DungeonType.DEBUG) {
            dungeonType.lastMaterialIndex = (dungeonType.lastMaterialIndex + 1) % 2;
            return dungeonType.getMaterials()[dungeonType.lastMaterialIndex];
        } else {
            Material[] materials = dungeonType.getMaterials();
            return materials[RANDOM.nextInt(materials.length)];
        }
    }

    public static DungeonType getRandomTheme() {
        DungeonType[] dungeonTypes = DungeonType.values();
        int randomIndex = RANDOM.nextInt(dungeonTypes.length);
        return dungeonTypes[randomIndex];
    }
}

