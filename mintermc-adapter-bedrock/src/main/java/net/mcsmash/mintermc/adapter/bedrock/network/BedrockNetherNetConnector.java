package net.mcsmash.mintermc.adapter.bedrock.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.NetherNetConstants;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetDiscoverySignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

/**
 * Opens a NetherNet connection to a Bedrock server.
 * <br>
 * NetherNet exchanges SDP offers through the server's HTTP signaling endpoint
 * and then carries whole Bedrock batches over WebRTC data channels. The
 * pipeline mirrors the RakNet one but replaces the frame ID codec with
 * {@link NetherNetBatchCodec}.
 */
public final class BedrockNetherNetConnector {

    private BedrockNetherNetConnector() {
    }

    /**
     * Connects to the given server and blocks until the WebRTC handshake finishes.
     *
     * @param group              the event loop group that owns the connection
     * @param host               the server host
     * @param port               the server NetherNet signaling port
     * @param identity           the transport identity presented in the SDP offer, or {@code null}
     * @param lanDiscovery       whether to discover the server over the LAN instead of HTTP signaling
     * @param sessionInitializer called for every Bedrock session created by the peer
     * @return the connect future, already completed or failed
     */
    public static ChannelFuture connect(NioEventLoopGroup group, String host, int port,
            OperatorIdentity identity, boolean lanDiscovery,
            Consumer<BedrockClientSession> sessionInitializer) {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channelFactory(lanDiscovery
                        ? NetherNetChannelFactory.client(new NetherNetDiscoverySignaling())
                        : NetherNetChannelFactory.client(new NetherNetHTTPClientSignaling()))
                .option(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, 15_000)
                .option(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 1)
                .option(NetherChannelOption.NETHER_CLIENT_IDENTITY, identity)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        BedrockPeer peer = new BedrockPeer(channel, (bedrockPeer, subClientId) -> {
                            BedrockClientSession session = new BedrockClientSession(bedrockPeer, subClientId);
                            sessionInitializer.accept(session);
                            return session;
                        });

                        channel.pipeline()
                                .addLast(NetherNetBatchCodec.NAME, new NetherNetBatchCodec())
                                .addLast(CompressionCodec.NAME,
                                        new CompressionCodec(
                                                new SimpleCompressionStrategy(new NoopCompression()), false))
                                .addLast(BedrockBatchDecoder.NAME, new BedrockBatchDecoder())
                                .addLast(BedrockBatchEncoder.NAME, new BedrockBatchEncoder())
                                .addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3())
                                .addLast(BedrockPeer.NAME, peer);
                    }
                });

        return bootstrap.connect(lanDiscovery
                ? new InetSocketAddress(host, NetherNetConstants.DISCOVERY_PORT)
                : new InetSocketAddress(host, port)).syncUninterruptibly();
    }
}
