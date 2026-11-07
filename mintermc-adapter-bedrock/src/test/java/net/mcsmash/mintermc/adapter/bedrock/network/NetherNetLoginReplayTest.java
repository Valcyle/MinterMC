package net.mcsmash.mintermc.adapter.bedrock.network;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.ZlibCompression;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.Zlib;
import org.jose4j.json.JsonUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Replays a login captured from a working bedrock-protocol client.
 * <br>
 * This is a diagnostic integration test. It is skipped unless
 * {@code MINTER_REPLAY_LOGIN} points at the captured JSON file.
 */
class NetherNetLoginReplayTest {

    private static final BedrockCodec CODEC = Bedrock_v2225.CODEC.toBuilder()
            .updateSerializer(LoginPacket.class, new MinterLoginSerializer())
            .build();

    @Test
    void replaysCapturedLogin() throws Exception {
        String file = System.getenv("MINTER_REPLAY_LOGIN");
        assumeTrue(file != null && !file.isBlank(), "MINTER_REPLAY_LOGIN is not set");
        String rawFile = System.getenv("MINTER_REPLAY_RAW");

        Map<String, Object> data = JsonUtil.parseJson(Files.readString(Path.of(file)));
        String privateKeyDer = (String) data.get("privateKeyDer");
        String publicKeyDer = (String) data.get("publicKeyDer");
        String identityToken = (String) data.get("identityToken");
        String clientJwt = (String) data.get("clientJwt");
        String host = System.getenv().getOrDefault("MINTER_REPLAY_HOST", (String) data.get("host"));
        int port = Integer.parseInt(System.getenv().getOrDefault("MINTER_REPLAY_PORT",
                String.valueOf(((Number) data.get("port")).intValue())));

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PrivateKey privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyDer)));
        PublicKey publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyDer)));
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        OperatorIdentity identity = OperatorIdentity.fromToken(keyPair, identityToken, "MinterMC");

        NioEventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch response = new CountDownLatch(1);
        try {
            ChannelFuture future = BedrockNetherNetConnector.connect(group, host, port, identity, false,
                    session -> {
                        session.setCodec(CODEC);
                        session.setPacketHandler(
                                new ReplayHandler(identityToken, clientJwt, response, session, rawFile));
                    });
            assertTrue(future.isSuccess(), "NetherNet connection failed");
            Channel channel = future.channel();
            BedrockPeer peer = channel.pipeline().get(BedrockPeer.class);
            peer.setCodec(CODEC);

            RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
            request.setProtocolVersion(CODEC.getProtocolVersion());
            peer.sendPacketImmediately(0, 0, request);

            boolean answered = response.await(20, TimeUnit.SECONDS);
            System.out.println("Replay result: " + (answered ? "GOT RESPONSE" : "NO RESPONSE"));
            assertTrue(answered, "BDS did not answer the captured login");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static final class ReplayHandler implements BedrockPacketHandler {
        private final String identityToken;
        private final String clientJwt;
        private final CountDownLatch response;
        private final BedrockClientSession session;
        private final String rawFile;

        private ReplayHandler(String identityToken, String clientJwt, CountDownLatch response,
                BedrockClientSession session, String rawFile) {
            this.identityToken = identityToken;
            this.clientJwt = clientJwt;
            this.response = response;
            this.session = session;
            this.rawFile = rawFile;
        }

        @Override
        public PacketSignal handle(NetworkSettingsPacket packet) {
            ChannelPipeline pipeline = session.getPeer().getChannel().pipeline();
            pipeline.replace(CompressionCodec.NAME, CompressionCodec.NAME,
                    new TolerantCompressionCodec(
                            new SimpleCompressionStrategy(new ZlibCompression(Zlib.RAW)), true));

            if (rawFile != null) {
                try {
                    byte[] raw = Files.readAllBytes(Path.of(rawFile));
                    BedrockBatchWrapper batch = BedrockBatchWrapper.newInstance();
                    batch.setUncompressed(Unpooled.wrappedBuffer(raw));
                    System.out.println("Replay: active=" + session.getPeer().getChannel().isActive()
                            + " writable=" + session.getPeer().getChannel().isWritable());
                    session.getPeer().getChannel().writeAndFlush(batch)
                            .addListener(f -> System.out.println(
                                    "Replay: raw batch write success=" + f.isSuccess() + " cause=" + f.cause()));
                    System.out.println("Replay: sent raw captured batch");
                    return PacketSignal.HANDLED;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            LoginPacket login = new LoginPacket();
            login.setProtocolVersion(CODEC.getProtocolVersion());
            login.setAuthPayload(new TokenPayload(identityToken, AuthType.SELF_SIGNED));
            login.setClientJwt(clientJwt);
            System.out.println("Replay: active=" + session.getPeer().getChannel().isActive()
                    + " writable=" + session.getPeer().getChannel().isWritable());
            session.sendPacketImmediately(login);
            System.out.println("Replay: sent captured login packet");
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(PlayStatusPacket packet) {
            System.out.println("Replay: PlayStatus " + packet.getStatus());
            response.countDown();
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(StartGamePacket packet) {
            System.out.println("Replay: StartGame");
            response.countDown();
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handle(PacketViolationWarningPacket packet) {
            System.out.println("Replay: violation " + packet.getType() + " " + packet.getSeverity()
                    + " " + packet.getContext());
            response.countDown();
            return PacketSignal.HANDLED;
        }

        @Override
        public PacketSignal handlePacket(BedrockPacket packet) {
            System.out.println("Replay inbound: " + packet.getClass().getSimpleName());
            return BedrockPacketHandler.super.handlePacket(packet);
        }
    }
}
