package net.mcsmash.mintermc.adapter.bedrock.network;

import net.mcsmash.mintermc.adapter.bedrock.BedrockEditionBotSession;
import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final BedrockEditionBotSession bot;
    private final BedrockClientSession session;
    private final BedrockLoginHelper loginHelper;

    /**
     * @param bot         the bot session that owns this connection
     * @param session     the low-level Bedrock session
     * @param loginHelper the helper used to build login and handshake packets
     */
    public BedrockBotPacketHandler(BedrockEditionBotSession bot, BedrockClientSession session,
            BedrockLoginHelper loginHelper) {
        this.bot = bot;
        this.session = session;
        this.loginHelper = loginHelper;
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket packet) {
        logger.debug("Received Bedrock network settings (compression={})", packet.getCompressionAlgorithm());
        // From this point on every packet uses the negotiated compression.
        session.setCompression(packet.getCompressionAlgorithm());
        loginHelper.handleOfflineLogin();
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket packet) {
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
        bot.setRuntimeEntityId(packet.getRuntimeEntityId());

        Vector3f position = packet.getPlayerPosition();
        if (position != null) {
            bot.setLocation(new Location(
                    new Vector3(position.getX(), position.getY(), position.getZ()),
                    packet.getRotation().getX(),
                    packet.getRotation().getY()));
        }

        // Tell the server the local player is ready only after the spawn status
        // arrives; sending it earlier is treated as a protocol violation by BDS.
        // If a rare spawn-before-start-game race happens, the spawn handler
        // already set the flag and we initialize here instead.
        if (bot.isSpawned()) {
            sendLocalPlayerInitialized();
        }

        logger.info("Bedrock bot {} joined the world at {}", bot.getUsername(), bot.getLocation());
        return PacketSignal.HANDLED;
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
        logger.info("[Bedrock chat] {}: {}", packet.getSourceName(), packet.getMessage());
        return PacketSignal.HANDLED;
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
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        return BedrockPacketHandler.super.handlePacket(packet);
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        logger.info("Bedrock session for {} closed: {}", bot.getUsername(), reason);
    }

    private void sendResourcePackResponse(ResourcePackClientResponsePacket.Status status) {
        ResourcePackClientResponsePacket response = new ResourcePackClientResponsePacket();
        response.setStatus(status);
        session.sendPacketImmediately(response);
    }
}
