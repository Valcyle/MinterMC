package net.mcsmash.mintermc.adapter.bedrock.network;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Computes the hashed Bedrock block network ID used when {@code StartGame}
 * enables {@code blockNetworkIdsAreHashed}.
 * <br>
 * The byte layout mirrors dragonfly's {@code network_block_hash.go}: a
 * canonical little-endian NBT compound containing {@code {name, states}},
 * hashed with FNV-1a 32.
 */
final class BedrockBlockHash {

    private static final int FNV1A_32_INIT = 0x811C9DC5;
    private static final int FNV1A_32_PRIME = 0x01000193;
    private static final int UNKNOWN_HASH = 0xFFFFFFFE;

    private BedrockBlockHash() {
    }

    /**
     * Computes the network ID for a block state.
     *
     * @param name   the block identifier, for example {@code minecraft:stone}
     * @param states the block state properties
     * @return the FNV-1a 32 hash, or {@code -2} for {@code minecraft:unknown}
     */
    static int hash(String name, Map<String, Object> states) {
        if ("minecraft:unknown".equals(name)) {
            return UNKNOWN_HASH;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        out.write(10); // TAG_Compound
        out.write(0);  // empty root name (uint16 LE)
        out.write(0);

        out.write(8); // TAG_String
        writeString(out, "name");
        writeString(out, name);

        out.write(10); // TAG_Compound
        writeString(out, "states");

        List<String> keys = new ArrayList<>(states.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            writeTag(out, key, states.get(key));
        }

        out.write(0); // end states
        out.write(0); // end root

        int hash = FNV1A_32_INIT;
        for (byte b : out.toByteArray()) {
            hash ^= (b & 0xFF);
            hash *= FNV1A_32_PRIME;
        }
        return hash;
    }

    private static void writeTag(ByteArrayOutputStream out, String key, Object value) {
        if (value instanceof String string) {
            out.write(8);
            writeString(out, key);
            writeString(out, string);
        } else if (value instanceof Byte b) {
            out.write(1);
            writeString(out, key);
            out.write(b & 0xFF);
        } else if (value instanceof Boolean b) {
            out.write(1);
            writeString(out, key);
            out.write(b ? 1 : 0);
        } else if (value instanceof Short s) {
            out.write(2);
            writeString(out, key);
            writeShort(out, s);
        } else if (value instanceof Integer i) {
            out.write(3);
            writeString(out, key);
            writeInt(out, i);
        } else if (value instanceof Long l) {
            out.write(4);
            writeString(out, key);
            writeLong(out, l);
        } else if (value instanceof Float f) {
            out.write(5);
            writeString(out, key);
            writeInt(out, Float.floatToRawIntBits(f));
        } else if (value instanceof Double d) {
            out.write(6);
            writeString(out, key);
            writeLong(out, Double.doubleToRawLongBits(d));
        } else {
            throw new IllegalArgumentException("Unsupported block state value type: "
                    + (value == null ? "null" : value.getClass().getName()));
        }
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        out.write(length & 0xFF);
        out.write((length >>> 8) & 0xFF);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeShort(ByteArrayOutputStream out, short value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >>> (i * 8)) & 0xFF));
        }
    }
}
