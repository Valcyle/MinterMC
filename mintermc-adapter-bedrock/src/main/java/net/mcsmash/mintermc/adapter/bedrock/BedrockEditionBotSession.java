package net.mcsmash.mintermc.adapter.bedrock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;
import net.mcsmash.mintermc.core.BotOptions;
import net.mcsmash.mintermc.core.BotSession;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class BedrockEditionBotSession extends BotSession {
    private static final Logger logger = LoggerFactory.getLogger(BedrockEditionBotSession.class);
    // Protocol bot session
    private BedrockClientSession session;

    public BedrockEditionBotSession(String sessionToken) {
        super(sessionToken);
    }

    @Override
    protected void cleanup() {
        logger.info("Cleaning up Bedrock Edition bot session: {}", getSessionToken());
    }

    @Override
    protected void handleNetworkDisconnect() {
        logger.info("Handling network disconnect for Bedrock Edition bot");
        // disconnection handling
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
    public Location getLocation() {
        // TODO: Implement location retrieval logic
        return null;
    }

    @Override
    public void chat(String message) {
        // TODO: Implement chat logic
    }

    @Override
    public boolean isConnected() {
        return this.session != null && this.session.isConnected();
    }

    // Connection methods
    public void connect(String host, int port, String username, boolean onlineMode,
            BotOptions options) {
        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", 0);
        // Protocol bedrock client

        // Online mode authentication
        if (onlineMode) {
            // TODO: Implement online mode authentication
        } else {

        }

        this.setUsername(username);
        this.setVersion(options.version());
        this.setEdition("Bedrock");

        // connect
        ChannelFuture channel = new Bootstrap().channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .group(new NioEventLoopGroup())
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession session) {
                        // codec setting ex for latest
                        session.setCodec(Bedrock_v944.CODEC);
                        // TODO: Implement packet handler
                        // session.setPacketHandler(null);
                        session.sendPacketImmediately(new LoginPacket());
                    }
                })
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, 11)
                .connect(new InetSocketAddress(host, port))
                .syncUninterruptibly();
    }
}
