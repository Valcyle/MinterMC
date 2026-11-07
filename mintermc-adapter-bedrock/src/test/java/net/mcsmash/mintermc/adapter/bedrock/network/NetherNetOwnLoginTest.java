package net.mcsmash.mintermc.adapter.bedrock.network;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.ZlibCompression;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.Zlib;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: sends MinterMC's own offline login over NetherNet.
 * <br>
 * This exercises the production packet path, including the compression codec and the shared
 * NetherNet/login token. It is skipped unless a NetherNet test server is configured with
 * {@code MINTER_REPLAY_HOST} and {@code MINTER_REPLAY_PORT}.
 */
class NetherNetOwnLoginTest {

    private static final BedrockCodec CODEC = Bedrock_v2225.CODEC.toBuilder()
            .updateSerializer(LoginPacket.class, new MinterLoginSerializer())
            .build();

    @Test
    void sendsOwnLogin() throws Exception {
        String host = System.getenv("MINTER_REPLAY_HOST");
        String portValue = System.getenv("MINTER_REPLAY_PORT");
        assumeTrue(host != null && portValue != null, "NetherNet test server is not set");
        int port = Integer.parseInt(portValue);

        KeyPair keyPair = EncryptionUtils.createKeyPair();
        BedrockLoginHelper helper = new BedrockLoginHelper(
                null, "MinterOwn", "1.26.60", host + ":" + port, null, keyPair);
        LoginPacket login = helper.buildLoginPacket();
        OperatorIdentity identity = OperatorIdentity.fromToken(
                keyPair, helper.identityToken(), "MinterMC");

        NioEventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch response = new CountDownLatch(1);
        try {
            ChannelFuture future = BedrockNetherNetConnector.connect(group, host, port, identity, false,
                    session -> {
                        session.setCodec(CODEC);
                        session.setPacketHandler(new Handler(login, response, session));
                    });
            assertTrue(future.isSuccess(), "NetherNet connection failed");

            BedrockPeer peer = future.channel().pipeline().get(BedrockPeer.class);
            peer.setCodec(CODEC);
            RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
            request.setProtocolVersion(CODEC.getProtocolVersion());
            peer.sendPacketImmediately(0, 0, request);

            boolean answered = response.await(20, TimeUnit.SECONDS);
            System.out.println("Own login result: " + (answered ? "GOT RESPONSE" : "NO RESPONSE"));
            assertTrue(answered, "BDS did not answer MinterMC's own login");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static final class Handler implements BedrockPacketHandler {
        private final LoginPacket login;
        private final CountDownLatch response;
        private final BedrockClientSession session;

        private Handler(LoginPacket login, CountDownLatch response, BedrockClientSession session) {
            this.login = login;
            this.response = response;
            this.session = session;
        }

        @Override
        public PacketSignal handle(NetworkSettingsPacket packet) {
            ChannelPipeline pipeline = session.getPeer().getChannel().pipeline();
            pipeline.replace(CompressionCodec.NAME, CompressionCodec.NAME,
                    new TolerantCompressionCodec(
                            new SimpleCompressionStrategy(new ZlibCompression(Zlib.RAW)), true));
            session.sendPacketImmediately(login);
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(ServerToClientHandshakePacket packet) {
            System.out.println("Own login: ServerToClientHandshake");
            response.countDown();
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(PlayStatusPacket packet) {
            System.out.println("Own login: PlayStatus " + packet.getStatus());
            response.countDown();
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(StartGamePacket packet) {
            System.out.println("Own login: StartGame");
            response.countDown();
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(PacketViolationWarningPacket packet) {
            System.out.println("Own login: violation " + packet.getType() + " " + packet.getSeverity()
                    + " " + packet.getContext());
            response.countDown();
            return PacketSignal.HANDLED;
        }
    }
}
