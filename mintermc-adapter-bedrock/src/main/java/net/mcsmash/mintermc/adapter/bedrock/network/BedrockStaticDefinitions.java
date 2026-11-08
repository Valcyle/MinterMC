package net.mcsmash.mintermc.adapter.bedrock.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Static Bedrock block and item definitions for protocol 2225.
 * <br>
 * BDS 1.26 uses hashed block network IDs and does not send a block palette in
 * {@code StartGame}, so the client needs the canonical block state table to
 * build the runtime ID registry. The bundled data is generated from
 * minecraft-data 1.26.60 (MIT).
 */
public final class BedrockStaticDefinitions {

    private static final Logger logger = LoggerFactory.getLogger(BedrockStaticDefinitions.class);
    private static final String BLOCKS_RESOURCE = "/bedrock/bedrock_block_states.json.gz";
    private static final String ITEMS_RESOURCE = "/bedrock/bedrock_items.json.gz";

    /** Network id of {@code minecraft:air}, used for fully empty sub-chunks. */
    private static final int AIR_NETWORK_ID = BedrockBlockHash.hash("minecraft:air", Map.of());

    private static volatile DefinitionRegistry<BlockDefinition> blocks;
    private static volatile DefinitionRegistry<ItemDefinition> items;
    private static volatile Map<Integer, String> blockNames;

    private BedrockStaticDefinitions() {
    }

    /**
     * Installs the static registries on a session's codec helper.
     *
     * @param helper the helper created for the Bedrock session
     */
    public static void install(BedrockCodecHelper helper) {
        helper.setEncodingSettings(EncodingSettings.CLIENT.toBuilder()
                .maxListSize(16384)
                .build());
        helper.setBlockDefinitions(blocks());
        helper.setItemDefinitions(items());
    }

    public static DefinitionRegistry<BlockDefinition> blocks() {
        DefinitionRegistry<BlockDefinition> current = blocks;
        if (current == null) {
            synchronized (BedrockStaticDefinitions.class) {
                current = blocks;
                if (current == null) {
                    current = loadBlocks();
                    blocks = current;
                }
            }
        }
        return current;
    }

    public static DefinitionRegistry<ItemDefinition> items() {
        DefinitionRegistry<ItemDefinition> current = items;
        if (current == null) {
            synchronized (BedrockStaticDefinitions.class) {
                current = items;
                if (current == null) {
                    current = loadItems();
                    items = current;
                }
            }
        }
        return current;
    }

    private static DefinitionRegistry<BlockDefinition> loadBlocks() {
        try {
            JsonArray array = readResource(BLOCKS_RESOURCE).getAsJsonArray("blocks");
            List<BlockDefinition> definitions = new ArrayList<>(array.size());
            Map<Integer, String> names = new java.util.HashMap<>(array.size());
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String name = entry.get("name").getAsString();
                JsonObject stateObject = entry.getAsJsonObject("states");
                Map<String, Object> states = new LinkedHashMap<>();
                NbtMapBuilder nbt = NbtMap.builder();
                for (String key : stateObject.keySet()) {
                    addState(states, nbt, key, stateObject.getAsJsonObject(key));
                }
                int runtimeId = BedrockBlockHash.hash(name, states);
                definitions.add(new SimpleBlockDefinition(name, runtimeId, nbt.build()));
                names.put(runtimeId, name);
            }
            blockNames = names;
            logger.info("Loaded {} Bedrock block definitions", definitions.size());
            return SimpleDefinitionRegistry.<BlockDefinition>builder().addAll(definitions).build();
        } catch (Exception e) {
            logger.error("Failed to load Bedrock block definitions", e);
            blockNames = Map.of();
            return SimpleDefinitionRegistry.<BlockDefinition>builder().build();
        }
    }

    /**
     * @return the network id of {@code minecraft:air}
     */
    public static int airNetworkId() {
        return AIR_NETWORK_ID;
    }

    /**
     * Resolves a hashed block network id to its identifier.
     *
     * @param networkId the hashed block state id
     * @return the identifier, or {@code minecraft:unknown} when it is not bundled
     */
    public static String blockName(int networkId) {
        blocks();
        Map<Integer, String> names = blockNames;
        return names == null
                ? "minecraft:unknown"
                : names.getOrDefault(networkId, "minecraft:unknown");
    }

    private static DefinitionRegistry<ItemDefinition> loadItems() {
        try {
            JsonArray array = readResource(ITEMS_RESOURCE).getAsJsonArray("items");
            List<ItemDefinition> definitions = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                definitions.add(new SimpleItemDefinition(
                        entry.get("name").getAsString(),
                        entry.get("id").getAsInt(),
                        entry.get("componentBased").getAsBoolean()));
            }
            logger.info("Loaded {} Bedrock item definitions", definitions.size());
            return SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(definitions).build();
        } catch (Exception e) {
            logger.error("Failed to load Bedrock item definitions", e);
            return SimpleDefinitionRegistry.<ItemDefinition>builder().build();
        }
    }

    private static JsonObject readResource(String path) throws IOException {
        try (InputStream in = BedrockStaticDefinitions.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing resource " + path);
            }
            try (GZIPInputStream gzip = new GZIPInputStream(in)) {
                String json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
                return JsonParser.parseString(json).getAsJsonObject();
            }
        }
    }

    private static void addState(Map<String, Object> states, NbtMapBuilder nbt,
            String key, JsonObject state) {
        String type = state.get("type").getAsString();
        JsonElement value = state.get("value");
        switch (type) {
            case "byte" -> {
                byte v = (byte) value.getAsInt();
                states.put(key, v);
                nbt.putByte(key, v);
            }
            case "int" -> {
                int v = value.getAsInt();
                states.put(key, v);
                nbt.putInt(key, v);
            }
            case "short" -> {
                short v = (short) value.getAsInt();
                states.put(key, v);
                nbt.putShort(key, v);
            }
            case "long" -> {
                long v = value.getAsLong();
                states.put(key, v);
                nbt.putLong(key, v);
            }
            case "float" -> {
                float v = value.getAsFloat();
                states.put(key, v);
                nbt.putFloat(key, v);
            }
            case "double" -> {
                double v = value.getAsDouble();
                states.put(key, v);
                nbt.putDouble(key, v);
            }
            case "string" -> {
                String v = value.getAsString();
                states.put(key, v);
                nbt.putString(key, v);
            }
            case "bool", "boolean" -> {
                boolean v = value.getAsBoolean();
                states.put(key, v);
                nbt.putBoolean(key, v);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported block state type " + type + " for " + key);
        }
    }
}
