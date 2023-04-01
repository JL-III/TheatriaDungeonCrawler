package com.jliii.theatriadungeoncrawler.templates;

import org.bukkit.Material;

import java.util.Random;

public class DungeonTemplate {

    private static final Random RANDOM = new Random();

    public enum DungeonType {
        ICE(new Material[]{Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK}),
        STONE(new Material[]{Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE}),
        NETHER(new Material[]{Material.NETHERRACK, Material.SOUL_SAND, Material.BASALT}),
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
}

