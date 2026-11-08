package net.mcsmash.mintermc.adapter.bedrock.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockSubChunkTest {

    private static final int AIR_HASH = 0x11223344;
    private static final int STONE_HASH = -0x55667788;

    @Test
    void decodesAPalettedStorage() {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(9);   // version
        data.writeByte(1);   // storage count
        data.writeByte(0);   // sub-chunk index
        data.writeByte(0x03); // 1 bit per block, runtime palette
        for (int word = 0; word < 128; word++) {
            // The first block uses palette index 1, the rest use index 0.
            data.writeIntLE(word == 0 ? 1 : 0);
        }
        writeSignedVarInt(data, 2);
        writeSignedVarInt(data, AIR_HASH);
        writeSignedVarInt(data, STONE_HASH);

        int[] blocks = BedrockSubChunk.decode(data);

        assertEquals(4096, blocks.length);
        assertEquals(STONE_HASH, blocks[0]);
        assertEquals(AIR_HASH, blocks[1]);
        assertEquals(AIR_HASH, blocks[4095]);
    }

    @Test
    void decodesAUniformStorage() {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(9);
        data.writeByte(1);
        data.writeByte(0);
        data.writeByte(0x01); // 0 bits per block, runtime palette
        writeSignedVarInt(data, AIR_HASH);

        int[] blocks = BedrockSubChunk.decode(data);

        int[] expected = new int[4096];
        java.util.Arrays.fill(expected, AIR_HASH);
        assertArrayEquals(expected, blocks);
    }

    /** Writes a zigzag encoded signed varint, as Bedrock palettes use. */
    private static void writeSignedVarInt(ByteBuf buffer, int value) {
        int zigzag = (value << 1) ^ (value >> 31);
        while ((zigzag & ~0x7F) != 0) {
            buffer.writeByte((zigzag & 0x7F) | 0x80);
            zigzag >>>= 7;
        }
        buffer.writeByte(zigzag);
    }
}
