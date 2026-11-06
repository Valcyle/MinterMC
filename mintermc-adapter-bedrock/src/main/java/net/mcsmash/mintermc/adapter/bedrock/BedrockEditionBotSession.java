package net.mcsmash.mintermc.adapter.bedrock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockBotPacketHandler;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockLoginHelper;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockNetherNetConnector;
import net.mcsmash.mintermc.adapter.bedrock.network.MinterLoginSerializer;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.bot.BotOptions;
import net.mcsmash.mintermc.api.math.Vector3;
import net.mcsmash.mintermc.core.BotSession;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bedrock Edition implementation of a bot session.
 * <br>
 * Supports both transports used by Bedrock servers: RakNet (older and
 * third-party servers) and NetherNet (Minecraft 1.26 and later). The login
 * handshake and packet handling are shared; only the Netty transport and its
 * framing differ.
 */
public class BedrockEditionBotSession extends BotSession {
    private static final Logger logger = LoggerFactory.getLogger(BedrockEditionBotSession.class);

    /** Protocol 2225 (1.26.60) with the login serializer BDS expects. */
    private static final BedrockCodec CODEC = Bedrock_v2225.CODEC.toBuilder()
            .updateSerializer(LoginPacket.class, new MinterLoginSerializer())
            .build();

    private volatile BedrockClientSession session;
    private volatile NioEventLoopGroup eventLoopGroup;
    private volatile boolean loggedIn;
    private volatile boolean spawned;
    private volatile long runtimeEntityId;

    public BedrockEditionBotSession(String sessionToken) {
        super(sessionToken);
    }

    @Override
    protected void cleanup() {
        closeConnection();
    }

    @Override
    protected void handleNetworkDisconnect() {
        closeConnection();
    }

    @Override
    public CompletableFuture<Void> moveTo(Vector3 position) {
        return CompletableFuture.runAsync(() -> {
            // TODO: Implement movement logic
        });
    }

    @Override
    public Block getBlockAt(Vector3 position) {
        // TODO: Implement block retrieval logic
        return null;
    }

    @Override
    public void chat(String message) {
        // TODO: Implement chat logic
    }

    @Override
    public boolean isConnected() {
        BedrockClientSession current = this.session;
        return current != null && current.isConnected() && this.loggedIn;
    }

    /**
     * Connects the bot to a Bedrock Edition server.
     * <br>
     * The method blocks until the selected transport is established. The
     * Bedrock login itself continues asynchronously on the Netty event loop.
     *
     * @param host       the target server host
     * @param port       the target server port
     * @param username   the player name to join with
     * @param onlineMode whether to use Microsoft account authentication
     * @param options    the bot options such as the client version and transport
     */
    public void connect(String host, int port, String username, boolean onlineMode,
            BotOptions options) {
        this.setUsername(username);
        this.setVersion(options.version());
        this.setEdition("Bedrock");

        String transport = options.transport() != null ? options.transport() : "raknet";
        if (transport.toLowerCase().startsWith("nethernet")) {
            connectNetherNet(host, port, username, options,
                    "nethernet-lan".equalsIgnoreCase(transport));
        } else {
            connectRakNet(host, port, username, options);
        }
    }

    private void connectRakNet(String host, int port, String username, BotOptions options) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        this.eventLoopGroup = group;

        ChannelFuture future = new Bootstrap()
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .group(group)
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession bedrockSession) {
                        initializeSession(bedrockSession, username, options, host, port, false, null);
                    }
                })
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, 11)
                .connect(new InetSocketAddress(host, port))
                .syncUninterruptibly();

        if (!future.isSuccess()) {
            Throwable cause = future.cause();
            logger.error("Failed to open RakNet connection to {}:{}", host, port, cause);
            group.shutdownGracefully();
            throw new IllegalStateException("Failed to connect to " + host + ":" + port, cause);
        }

        BedrockPeer peer = future.channel().pipeline().get(BedrockPeer.class);
        if (peer == null) {
            group.shutdownGracefully();
            throw new IllegalStateException("Bedrock peer was not initialized");
        }

        peer.setCodec(CODEC);
        sendNetworkSettingsRequest(peer);
        logger.info("Bedrock RakNet connection established to {}:{}, waiting for network settings", host, port);
    }

    private void connectNetherNet(String host, int port, String username, BotOptions options,
            boolean lanDiscovery) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        this.eventLoopGroup = group;

        KeyPair keyPair = EncryptionUtils.createKeyPair();
        OperatorIdentity identity;
        try {
            identity = new OperatorIdentity(keyPair.getPrivate(), keyPair.getPublic(), null, "MinterMC")
                    .forPlayer("0", username);
        } catch (Exception e) {
            group.shutdownGracefully();
            throw new IllegalStateException("Failed to create NetherNet identity", e);
        }

        ChannelFuture future = BedrockNetherNetConnector.connect(group, host, port, identity, lanDiscovery,
                bedrockSession -> initializeSession(bedrockSession, username, options, host, port, true, keyPair));

        if (!future.isSuccess()) {
            Throwable cause = future.cause();
            logger.error("Failed to open NetherNet connection to {}:{}", host, port, cause);
            group.shutdownGracefully();
            throw new IllegalStateException("Failed to connect to " + host + ":" + port, cause);
        }

        BedrockPeer peer = future.channel().pipeline().get(BedrockPeer.class);
        if (peer == null) {
            group.shutdownGracefully();
            throw new IllegalStateException("Bedrock peer was not initialized");
        }

        peer.setCodec(CODEC);
        sendNetworkSettingsRequest(peer);
        logger.info("Bedrock NetherNet connection established to {}:{}, waiting for network settings", host, port);
    }

    private void initializeSession(BedrockClientSession bedrockSession, String username, BotOptions options,
            String host, int port, boolean netherNet, KeyPair keyPair) {
        this.session = bedrockSession;
        bedrockSession.setCodec(CODEC);
        BedrockLoginHelper loginHelper = new BedrockLoginHelper(
                bedrockSession,
                username,
                options.version(),
                host + ":" + port,
                resolveOfflineIdentity(username),
                keyPair);
        bedrockSession.setPacketHandler(new BedrockBotPacketHandler(
                this, bedrockSession, loginHelper, netherNet));
    }

    private static void sendNetworkSettingsRequest(BedrockPeer peer) {
        RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
        request.setProtocolVersion(CODEC.getProtocolVersion());
        peer.sendPacketImmediately(0, 0, request);
    }

    private void closeConnection() {
        BedrockClientSession current = this.session;
        if (current != null && current.isConnected()) {
            current.disconnect("MinterMC session closed");
        }
        NioEventLoopGroup group = this.eventLoopGroup;
        if (group != null) {
            group.shutdownGracefully();
            this.eventLoopGroup = null;
        }
    }

    private static UUID resolveOfflineIdentity(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public long getRuntimeEntityId() {
        return runtimeEntityId;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public void setSpawned(boolean spawned) {
        this.spawned = spawned;
    }

    public void setRuntimeEntityId(long runtimeEntityId) {
        this.runtimeEntityId = runtimeEntityId;
    }
}
