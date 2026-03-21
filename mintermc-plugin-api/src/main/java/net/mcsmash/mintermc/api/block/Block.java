package net.mcsmash.mintermc.api.block;

/**
 * Represents a single block within the virtualized world.
 * <br>
 * Plugins will retrieve the physical properties and location of a block from this class.
 */
public interface Block {

    /**
     * Retrieves the X coordinate of this block.
     *
     * @return The X coordinate of this block
     */
    int getX();

    /**
     * Retrieves the Y coordinate of this block.
     *
     * @return The Y coordinate of this block
     */
    int getY();

    /**
     * Retrieves the Z coordinate of this block.
     *
     * @return The Z coordinate of this block
     */
    int getZ();

    /**
     * Retrieves the common block ID or name (e.g., "minecraft:stone"), similar to Bukkit or Nukkit.
     *
     * @return The material name of the block
     */
    String getMaterialName();

    /**
     * Checks if this block can be moved through (e.g., air, water).
     *
     * @return True if the block is passable, false otherwise
     */
    boolean isPassable();
}
