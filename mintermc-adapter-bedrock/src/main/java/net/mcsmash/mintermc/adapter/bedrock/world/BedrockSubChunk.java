package net.mcsmash.mintermc.adapter.bedrock.world;

import io.netty.buffer.ByteBuf;

/**
 * Decodes a network sub-chunk into block state hashes.
 * <br>
 * Since Bedrock 1.18.30 the level chunk packet only carries biome and height
 * data. Block storage arrives in {@code SubChunkPacket} responses and uses the
 * version 8 or 9 layout: a version byte, a storage count, an optional sub-chunk
 * index, and one paletted storage per layer.
 */
public final class BedrockSubChunk {

    /** Number of blocks in one sub-chunk (16 x 16 x 16). */
    public static final int BLOCK_COUNT = 4096;

    /** Header value that marks a layer reusing the previous palette. */
    private static final int REUSED_PALETTE = 0xFF;

    private BedrockSubChunk() {
    }

    /**
     * Decodes the first block layer of a sub-chunk.
     *
     * @param data the serialized sub-chunk
     * @return 4096 block state hashes in XZY order
     * @throws IllegalArgumentException if the payload uses an unsupported format
     */
    public static int[] decode(ByteBuf data) {
        int version = data.readUnsignedByte();
        if (version != 8 && version != 9) {
            throw new IllegalArgumentException("Unsupported sub-chunk version " + version);
        }
        int storageCount = data.readUnsignedByte();
        if (version == 9) {
            data.readByte(); // sub-chunk index, already known from the packet
        }

        int[] blocks = null;
        for (int storage = 0; storage < storageCount; storage++) {
            int header = data.readUnsignedByte();
            if (header == REUSED_PALETTE) {
                continue;
            }
            int bitsPerBlock = header >> 1;
            int[] indices = null;
            int uniformValue = 0;
            int[] palette;

            if (bitsPerBlock == 0) {
                palette = new int[] {readSignedVarInt(data)};
            } else {
                int blocksPerWord = 32 / bitsPerBlock;
                int wordCount = (BLOCK_COUNT + blocksPerWord - 1) / blocksPerWord;
                int mask = (1 << bitsPerBlock) - 1;
                indices = new int[BLOCK_COUNT];
                int cursor = 0;
                for (int word = 0; word < wordCount; word++) {
                    int packed = data.readIntLE();
                    for (int slot = 0; slot < blocksPerWord && cursor < BLOCK_COUNT; slot++) {
                        indices[cursor++] = (packed >>> (slot * bitsPerBlock)) & mask;
                    }
                }
                int paletteSize = readSignedVarInt(data);
                palette = new int[paletteSize];
                for (int entry = 0; entry < paletteSize; entry++) {
                    palette[entry] = readSignedVarInt(data);
                }
            }

            if (blocks == null) {
                // Only the first layer holds the visible block; later layers
                // describe waterlogging and are skipped.
                blocks = new int[BLOCK_COUNT];
                if (indices == null) {
                    java.util.Arrays.fill(blocks, palette[0]);
                } else {
                    for (int index = 0; index < BLOCK_COUNT; index++) {
                        int paletteIndex = indices[index];
                        blocks[index] = paletteIndex < palette.length ? palette[paletteIndex] : 0;
                    }
                }
            }
        }
        return blocks == null ? new int[BLOCK_COUNT] : blocks;
    }

    /**
     * Reads a zigzag encoded signed varint.
     * <br>
     * Block state hashes exceed the positive integer range, so Bedrock writes
     * palette sizes and entries as signed varints.
     */
    private static int readSignedVarInt(ByteBuf data) {
        int raw = readUnsignedVarInt(data);
        return (raw >>> 1) ^ -(raw & 1);
    }

    private static int readUnsignedVarInt(ByteBuf data) {
        int value = 0;
        int shift = 0;
        while (true) {
            int current = data.readUnsignedByte();
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IllegalArgumentException("Varint is too long");
            }
        }
    }
}
