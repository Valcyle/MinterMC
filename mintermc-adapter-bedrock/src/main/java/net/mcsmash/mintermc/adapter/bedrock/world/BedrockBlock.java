package net.mcsmash.mintermc.adapter.bedrock.world;

import net.mcsmash.mintermc.api.block.Block;

import java.util.Set;

/**
 * A block reported by the Bedrock adapter.
 * <br>
 * Bedrock identifies blocks with a hashed network id, so the block is resolved
 * to its identifier on creation.
 */
public final class BedrockBlock implements Block {

    /** Blocks that a player can walk through. */
    private static final Set<String> PASSABLE = Set.of(
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:void_air",
            "minecraft:water",
            "minecraft:flowing_water",
            "minecraft:lava",
            "minecraft:flowing_lava",
            "minecraft:short_grass",
            "minecraft:tall_grass",
            "minecraft:fern",
            "minecraft:large_fern",
            "minecraft:deadbush",
            "minecraft:dandelion",
            "minecraft:poppy",
            "minecraft:blue_orchid",
            "minecraft:allium",
            "minecraft:azure_bluet",
            "minecraft:red_tulip",
            "minecraft:orange_tulip",
            "minecraft:white_tulip",
            "minecraft:pink_tulip",
            "minecraft:oxeye_daisy",
            "minecraft:cornflower",
            "minecraft:lily_of_the_valley",
            "minecraft:torch",
            "minecraft:wall_torch",
            "minecraft:soul_torch",
            "minecraft:redstone_torch",
            "minecraft:snow_layer",
            "minecraft:vine",
            "minecraft:fire",
            "minecraft:soul_fire");

    private final int x;
    private final int y;
    private final int z;
    private final String materialName;

    /**
     * @param x            the block X coordinate
     * @param y            the block Y coordinate
     * @param z            the block Z coordinate
     * @param materialName the block identifier, for example {@code minecraft:stone}
     */
    public BedrockBlock(int x, int y, int z, String materialName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.materialName = materialName;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public String getMaterialName() {
        return materialName;
    }

    /**
     * {@inheritDoc}
     * <br>
     * Passability is derived from the block identifier. Blocks that are not in
     * the known passable list are treated as solid.
     */
    @Override
    public boolean isPassable() {
        return PASSABLE.contains(materialName);
    }

    @Override
    public String toString() {
        return "BedrockBlock{" + materialName + " at " + x + ", " + y + ", " + z + "}";
    }
}
