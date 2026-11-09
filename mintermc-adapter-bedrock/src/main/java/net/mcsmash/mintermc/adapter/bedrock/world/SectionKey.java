package net.mcsmash.mintermc.adapter.bedrock.world;

/**
 * Identifies one 16x16x16 sub-chunk of the world.
 *
 * @param x the chunk X coordinate
 * @param y the sub-chunk index, where 0 covers Y 0-15
 * @param z the chunk Z coordinate
 */
public record SectionKey(int x, int y, int z) {
}
