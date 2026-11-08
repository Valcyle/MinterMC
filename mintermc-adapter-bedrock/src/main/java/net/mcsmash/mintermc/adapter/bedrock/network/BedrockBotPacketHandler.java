package net.mcsmash.mintermc.adapter.bedrock.network;

import net.mcsmash.mintermc.adapter.bedrock.BedrockEditionBotSession;
import net.mcsmash.mintermc.adapter.bedrock.world.BedrockSubChunk;
import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SnappyCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.ZlibCompression;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.util.Zlib;
import io.netty.channel.ChannelPipeline;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Handles the packet flow needed to join a Bedrock Edition server.
 * <br>
 * The handler drives the login state machine (network settings, encryption,
 * resource packs, start game) and updates the owning bot session as packets
 * arrive.
 */
public class BedrockBotPacketHandler implements BedrockPacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(BedrockBotPacketHandler.class);

    private static final int CHUNK_RADIUS = 8;

    /** Number of level chunks received so far, used to confirm world streaming. */
    private int receivedChunks;

    private final BedrockEditionBotSession bot;
    private final BedrockClientSession session;
    private final BedrockLoginHelper loginHelper;
    private final boolean netherNet;

    /**
     * @param bot         the bot session that owns this connection
     * @param session     the low-level Bedrock session
     * @param loginHelper the helper used to build login and handshake packets
     * @param netherNet   whether the connection uses NetherNet instead of RakNet
     */
    public BedrockBotPacketHandler(BedrockEditionBotSession bot, BedrockClientSession session,
            BedrockLoginHelper loginHelper, boolean netherNet) {
        this.bot = bot;
        this.session = session;
        this.loginHelper = loginHelper;
        this.netherNet = netherNet;
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket packet) {
        logger.debug("Received Bedrock network settings (compression={}, threshold={})",
                packet.getCompressionAlgorithm(), packet.getCompressionThreshold());
        // Bedrock clients send compressed batches without the algorithm header byte;
        // the negotiated algorithm is implicit for the rest of the connection.
        // Cloudburst's peer helper always prefixes client connections, so the
        // compression handler is replaced directly instead.
        ChannelPipeline pipeline = session.getPeer().getChannel().pipeline();
        pipeline.replace(CompressionCodec.NAME, CompressionCodec.NAME,
                new TolerantCompressionCodec(compressionStrategy(packet.getCompressionAlgorithm()), true));
        loginHelper.handleOfflineLogin();
        return PacketSignal.HANDLED;
    }

    private static org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy compressionStrategy(
            PacketCompressionAlgorithm algorithm) {
        if (algorithm == PacketCompressionAlgorithm.SNAPPY) {
            return new SimpleCompressionStrategy(new SnappyCompression());
        }
        if (algorithm == PacketCompressionAlgorithm.NONE) {
            return new SimpleCompressionStrategy(new NoopCompression());
        }
        return new SimpleCompressionStrategy(new ZlibCompression(Zlib.RAW));
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket packet) {
        if (netherNet) {
            // NetherNet already encrypts the transport with DTLS. The retail
            // client answers the handshake without installing another cipher.
            logger.debug("Ignoring Bedrock encryption handshake on NetherNet");
            session.sendPacketImmediately(new ClientToServerHandshakePacket());
            return PacketSignal.HANDLED;
        }
        try {
            loginHelper.handleServerToClientHandshake(packet.getJwt());
        } catch (Exception e) {
            logger.error("Bedrock encryption handshake failed", e);
            session.disconnect("Encryption handshake failed");
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(PlayStatusPacket packet) {
        switch (packet.getStatus()) {
            case LOGIN_SUCCESS -> {
                bot.setLoggedIn(true);
                logger.info("Bedrock login accepted for {}", bot.getUsername());
            }
            case PLAYER_SPAWN -> {
                bot.setSpawned(true);
                sendLocalPlayerInitialized();
                bot.startAuthInputLoop();
                bot.notifySpawn();
                logger.info("Bedrock bot {} spawned", bot.getUsername());
            }
            default -> {
                logger.error("Bedrock login failed: {}", packet.getStatus());
                session.disconnect("Login failed: " + packet.getStatus());
            }
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePacksInfoPacket packet) {
        // Resource pack downloading is not implemented yet. Most servers send an
        // empty pack list, in which case completing the response is enough.
        if (!packet.getResourcePackInfos().isEmpty() || !packet.getBehaviorPackInfos().isEmpty()) {
            logger.warn("Server offers resource packs; MinterMC will continue without downloading them");
        }
        sendResourcePackResponse(ResourcePackClientResponsePacket.Status.COMPLETED);

        // Cache support is not implemented, so tell the server not to use it.
        ClientCacheStatusPacket cacheStatus = new ClientCacheStatusPacket();
        cacheStatus.setSupported(false);
        session.sendPacketImmediately(cacheStatus);

        // Mirror the vanilla client: request chunks shortly after resource packs.
        session.getPeer().getChannel().eventLoop().schedule(() -> {
            RequestChunkRadiusPacket chunkRadius = new RequestChunkRadiusPacket();
            chunkRadius.setRadius(CHUNK_RADIUS);
            session.sendPacketImmediately(chunkRadius);
        }, 500, TimeUnit.MILLISECONDS);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackStackPacket packet) {
        sendResourcePackResponse(ResourcePackClientResponsePacket.Status.COMPLETED);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(StartGamePacket packet) {
        installDefinitions(packet);
        bot.setRuntimeEntityId(packet.getRuntimeEntityId());
        bot.setDimension(packet.getDimensionId());

        Vector3f position = packet.getPlayerPosition();
        if (position != null) {
            // StartGame sends pitch first and yaw second, unlike MovePlayer which
            // also carries a head rotation.
            bot.setLocation(new Location(
                    new Vector3(position.getX(), position.getY(), position.getZ()),
                    packet.getRotation().getY(),
                    packet.getRotation().getX()));
        }

        // Tell the server the local player is ready only after the spawn status
        // arrives; sending it earlier is treated as a protocol violation by BDS.
        // If a rare spawn-before-start-game race happens, the spawn handler
        // already set the flag and we initialize here instead.
        if (bot.isSpawned()) {
            sendLocalPlayerInitialized();
            bot.startAuthInputLoop();
        }

        logger.info("Bedrock bot {} joined the world at {}", bot.getUsername(), bot.getLocation());
        return PacketSignal.HANDLED;
    }

    /**
     * Applies server-driven position corrections.
     * <br>
     * StartGame carries the level's spawn point, which can be a placeholder when
     * its Y value is unset. The authoritative position for the player arrives in a
     * MovePlayer packet after the world starts streaming, so it is applied here.
     */
    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
        if (packet.getRuntimeEntityId() != bot.getRuntimeEntityId()
                || packet.getMode() == MovePlayerPacket.Mode.HEAD_ROTATION) {
            return PacketSignal.HANDLED;
        }
        Vector3f position = packet.getPosition();
        Vector3f rotation = packet.getRotation();
        // MovePlayer rotation is (pitch, yaw, head yaw).
        bot.setLocation(new Location(
                new Vector3(position.getX(), position.getY(), position.getZ()),
                rotation.getY(), rotation.getX()));
        logger.debug("Bedrock bot {} moved to {}", bot.getUsername(), bot.getLocation());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ChunkRadiusUpdatedPacket packet) {
        logger.debug("Bedrock server accepted chunk radius {}", packet.getRadius());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LevelChunkPacket packet) {
        if (receivedChunks++ == 0) {
            logger.debug("Bedrock bot {} is receiving world chunks at ({}, {})",
                    bot.getUsername(), packet.getChunkX(), packet.getChunkZ());
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(SubChunkPacket packet) {
        for (SubChunkData data : packet.getSubChunks()) {
            Vector3i center = packet.getCenterPosition();
            Vector3i offset = data.getPosition();
            int chunkX = center.getX() + offset.getX();
            int sectionY = center.getY() + offset.getY();
            int chunkZ = center.getZ() + offset.getZ();
            if (data.getResult() == SubChunkRequestResult.SUCCESS && data.getData() != null) {
                try {
                    bot.acceptSection(chunkX, sectionY, chunkZ,
                            BedrockSubChunk.decode(data.getData()));
                } catch (Exception e) {
                    logger.warn("Could not decode sub-chunk {} {} {}", chunkX, sectionY, chunkZ, e);
                    bot.rejectSection(chunkX, sectionY, chunkZ);
                }
            } else if (data.getResult() == SubChunkRequestResult.SUCCESS_ALL_AIR) {
                int[] air = new int[BedrockSubChunk.BLOCK_COUNT];
                java.util.Arrays.fill(air, BedrockStaticDefinitions.airNetworkId());
                bot.acceptSection(chunkX, sectionY, chunkZ, air);
            } else {
                bot.rejectSection(chunkX, sectionY, chunkZ);
            }
        }
        return PacketSignal.HANDLED;
    }

    /**
     * Installs the block and item registries the server sent with StartGame.
     * <br>
     * Without these, Cloudburst cannot decode UpdateBlock, UpdateSubChunkBlocks or
     * MobEquipment packets and logs a decode failure for each one. When the server
     * enables block network ID hashes, the registry key is the same FNV-1a hash
     * the retail client computes from the block state.
     */
    private void installDefinitions(StartGamePacket packet) {
        BedrockCodecHelper helper = session.getPeer().getCodecHelper();

        NbtList<NbtMap> palette = packet.getBlockPalette();
        if (palette != null && !palette.isEmpty()) {
            SimpleDefinitionRegistry.Builder<BlockDefinition> blocks = SimpleDefinitionRegistry.builder();
            for (int runtimeId = 0; runtimeId < palette.size(); runtimeId++) {
                NbtMap state = palette.get(runtimeId);
                String name = state.getString("name", "minecraft:unknown");
                NbtMap states = state.getCompound("states", NbtMap.EMPTY);
                int id = packet.isBlockNetworkIdsHashed()
                        ? BedrockBlockHash.hash(name, states)
                        : runtimeId;
                blocks.add(new SimpleBlockDefinition(name, id, states));
            }
            helper.setBlockDefinitions(blocks.build());
        }

        List<ItemDefinition> items = packet.getItemDefinitions();
        if (items != null && !items.isEmpty()) {
            helper.setItemDefinitions(SimpleDefinitionRegistry.<ItemDefinition>builder()
                    .addAll(items)
                    .build());
        }

        logger.debug("StartGame definitions: palette={}, items={}, hashed={}",
                palette == null ? -1 : palette.size(),
                items == null ? -1 : items.size(),
                packet.isBlockNetworkIdsHashed());
    }

    /**
     * Sends the packet that lets the server finish placing the player in the world.
     */
    private void sendLocalPlayerInitialized() {
        if (bot.getRuntimeEntityId() == 0) {
            return;
        }
        SetLocalPlayerAsInitializedPacket initialized = new SetLocalPlayerAsInitializedPacket();
        initialized.setRuntimeEntityId(bot.getRuntimeEntityId());
        session.sendPacketImmediately(initialized);
    }

    @Override
    public PacketSignal handle(SetHealthPacket packet) {
        bot.setHealth(packet.getHealth());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(TextPacket packet) {
        String sender = "";
        String content;
        switch (packet.getType()) {
            case CHAT, WHISPER, ANNOUNCEMENT -> {
                sender = packet.getSourceName() == null
                        ? ""
                        : stripFormatting(packet.getSourceName());
                content = stripFormatting(
                        packet.getMessage() == null ? "" : packet.getMessage().toString());
            }
            default -> content = renderMessage(packet);
        }
        logger.info("[Bedrock chat] {}: {}", sender.isEmpty() ? "server" : sender, content);
        bot.notifyChatMessage(sender, content);
        return PacketSignal.HANDLED;
    }

    /**
     * Resolves a server message to plain text.
     * <br>
     * BDS sends player notices and announcements as translation keys with
     * parameters. The full client translation table is not bundled, so the common
     * keys are rendered and any unknown key keeps its parameters visible.
     */
    private static String renderMessage(TextPacket packet) {
        String message = packet.getMessage() == null ? "" : packet.getMessage().toString();
        message = stripFormatting(message);
        if (packet.getType() != TextPacket.Type.TRANSLATION && !packet.isNeedsTranslation()) {
            return message;
        }
        String key = message.startsWith("%") ? message.substring(1) : message;
        String template = switch (key) {
            case "multiplayer.player.joined" -> "%s joined the game";
            case "multiplayer.player.left" -> "%s left the game";
            case "chat.type.text" -> "<%s> %s";
            case "chat.type.announcement" -> "[%s] %s";
            default -> null;
        };
        if (template == null) {
            return packet.getParameters().isEmpty()
                    ? key
                    : key + " " + String.join(" ", packet.getParameters());
        }
        StringBuilder rendered = new StringBuilder();
        int cursor = 0;
        for (int index = 0; ; index++) {
            int at = template.indexOf("%s", cursor);
            if (at < 0) {
                break;
            }
            rendered.append(template, cursor, at);
            if (index < packet.getParameters().size()) {
                rendered.append(packet.getParameters().get(index));
            }
            cursor = at + 2;
        }
        rendered.append(template.substring(cursor));
        return rendered.toString();
    }

    private static String stripFormatting(String message) {
        return message.replaceAll("\u00A7.", "");
    }

    @Override
    public PacketSignal handle(NetworkStackLatencyPacket packet) {
        if (packet.isFromServer()) {
            NetworkStackLatencyPacket response = new NetworkStackLatencyPacket();
            response.setTimestamp(packet.getTimestamp());
            response.setFromServer(false);
            session.sendPacketImmediately(response);
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(DisconnectPacket packet) {
        logger.warn("Bedrock server disconnected {}: {}", bot.getUsername(), packet.getKickMessage());
        bot.notifyDisconnect(packet.getKickMessage() == null
                ? "server disconnected"
                : packet.getKickMessage());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(PacketViolationWarningPacket packet) {
        logger.warn("Bedrock packet violation from server: type={} severity={} cause={} context={}",
                packet.getType(), packet.getSeverity(), packet.getPacketCauseId(), packet.getContext());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        logger.debug("Bedrock inbound packet: {}", packet.getClass().getSimpleName());
        return BedrockPacketHandler.super.handlePacket(packet);
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        logger.info("Bedrock session for {} closed: {}", bot.getUsername(), reason);
        bot.notifyDisconnect(reason == null ? "connection closed" : reason.toString());
    }

    private void sendResourcePackResponse(ResourcePackClientResponsePacket.Status status) {
        ResourcePackClientResponsePacket response = new ResourcePackClientResponsePacket();
        response.setStatus(status);
        session.sendPacketImmediately(response);
    }
}



