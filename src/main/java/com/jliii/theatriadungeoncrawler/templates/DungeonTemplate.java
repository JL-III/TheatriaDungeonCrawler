package com.jliii.theatriadungeoncrawler.templates;

import org.bukkit.Material;

import java.util.Random;

public class DungeonTemplate {

    private static final Random RANDOM = new Random();

    public enum DungeonType {
        ICE(new Material[]{Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK}),
        STONE(new Material[]{Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE}),
        NETHER(new Material[]{Material.NETHERRACK, Material.SOUL_SAND, Material.BASALT});

        private final Material[] materials;

        DungeonType(Material[] materials) {
            this.materials = materials;
        }

        public Material[] getMaterials() {
            return materials;
        }
    }

    public static Material getRandomMaterial(DungeonType dungeonType) {
        Material[] materials = dungeonType.getMaterials();
        return materials[RANDOM.nextInt(materials.length)];
    }
}

