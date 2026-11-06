package net.mcsmash.mintermc.adapter.bedrock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockBotPacketHandler;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockLoginHelper;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.bot.BotOptions;
import net.mcsmash.mintermc.api.math.Vector3;
import net.mcsmash.mintermc.core.BotSession;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bedrock Edition implementation of a bot session.
 * <br>
 * Establishes a RakNet connection, performs the offline login handshake and
 * keeps the resulting {@link BedrockClientSession} for future packet handling.
 * The current implementation covers joining a server; movement, chat and block
 * access will be added on top of the same session.
 */
public class BedrockEditionBotSession extends BotSession {
    private static final Logger logger = LoggerFactory.getLogger(BedrockEditionBotSession.class);

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
     * The method blocks only until the RakNet connection is established. The
     * Bedrock login itself continues asynchronously on the Netty event loop.
     *
     * @param host      the target server host
     * @param port      the target server port
     * @param username  the player name to join with
     * @param onlineMode whether to use Microsoft account authentication
     * @param options   the bot options such as the client version
     */
    public void connect(String host, int port, String username, boolean onlineMode,
            BotOptions options) {
        this.setUsername(username);
        this.setVersion(options.version());
        this.setEdition("Bedrock");

        NioEventLoopGroup group = new NioEventLoopGroup();
        this.eventLoopGroup = group;

        ChannelFuture future = new Bootstrap()
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .group(group)
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession bedrockSession) {
                        BedrockEditionBotSession.this.session = bedrockSession;
                        bedrockSession.setCodec(Bedrock_v2225.CODEC);
                        BedrockLoginHelper loginHelper = new BedrockLoginHelper(
                                bedrockSession,
                                username,
                                options.version(),
                                host + ":" + port,
                                resolveOfflineIdentity(username));
                        bedrockSession.setPacketHandler(
                                new BedrockBotPacketHandler(BedrockEditionBotSession.this, bedrockSession, loginHelper));
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

        // The client is responsible for asking for network settings before the
        // login packet can be sent. No Bedrock session exists yet at this point,
        // so the request is written directly through the peer.
        peer.setCodec(Bedrock_v2225.CODEC);
        RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
        request.setProtocolVersion(Bedrock_v2225.CODEC.getProtocolVersion());
        peer.sendPacketImmediately(0, 0, request);

        logger.info("Bedrock connection established to {}:{}, waiting for network settings", host, port);
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


