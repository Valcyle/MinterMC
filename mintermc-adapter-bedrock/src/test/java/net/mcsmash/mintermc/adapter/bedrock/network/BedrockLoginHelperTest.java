package net.mcsmash.mintermc.adapter.bedrock.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.mcsmash.mintermc.auth.BedrockAuthSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the structure of the Bedrock offline login packet without a server.
 */
class BedrockLoginHelperTest {

    @Test
    void buildsSignedSelfSignedLoginPacket() throws Exception {
        BedrockLoginHelper helper = new BedrockLoginHelper(
                null, "TestBot", "1.26.10", "localhost:19132", null);

        LoginPacket packet = helper.buildLoginPacket();

        assertEquals(2225, packet.getProtocolVersion());
        assertTrue(packet.getAuthPayload() instanceof TokenPayload);

        TokenPayload authPayload = (TokenPayload) packet.getAuthPayload();
        assertEquals(AuthType.SELF_SIGNED, authPayload.getAuthType());

        JsonWebSignature identityToken = new JsonWebSignature();
        identityToken.setCompactSerialization(authPayload.getToken());
        assertEquals(EncryptionUtils.ALGORITHM_TYPE, identityToken.getAlgorithmHeaderValue());

        Map<String, Object> identityClaims = JsonUtil.parseJson(identityToken.getUnverifiedPayload());
        assertEquals("TestBot", identityClaims.get("xname"));
        assertEquals(helper.xuid(), identityClaims.get("xid"));
        assertTrue(((String) identityClaims.get("xid")).matches("[1-9]\\d*"));
        assertNotNull(identityClaims.get("mid"));
        assertTrue(!((String) identityClaims.get("mid")).isEmpty());
        assertEquals("self", identityClaims.get("iss"));
        assertEquals("api://auth-minecraft-services/multiplayer", identityClaims.get("aud"));
        assertNotNull(identityClaims.get("exp"));

        String publicKeyBase64 = (String) identityClaims.get("cpk");
        assertNotNull(publicKeyBase64);
        PublicKey publicKey = EncryptionUtils.parseKey(publicKeyBase64);

        identityToken.setKey(publicKey);
        assertTrue(identityToken.verifySignature());

        // The client data token must be signed by the same key referenced in cpk.
        assertNotNull(EncryptionUtils.verifyClientData(packet.getClientJwt(), publicKey));

        JsonWebSignature clientDataToken = new JsonWebSignature();
        clientDataToken.setCompactSerialization(packet.getClientJwt());
        Map<String, Object> clientData = JsonUtil.parseJson(clientDataToken.getUnverifiedPayload());
        assertEquals("TestBot", clientData.get("ThirdPartyName"));
        assertEquals("1.26.10", clientData.get("GameVersion"));
        assertTrue(((Number) clientData.get("SkinImageWidth")).intValue() > 0);
        assertTrue(((Number) clientData.get("SkinImageHeight")).intValue() > 0);

        // The packet must be serializable by the codec the session uses.
        ByteBuf buffer = Unpooled.buffer();
        try {
            Bedrock_v2225.CODEC.tryEncode(Bedrock_v2225.CODEC.createHelper(), buffer, packet);
            assertTrue(buffer.readableBytes() > 0);
        } finally {
            buffer.release();
        }
    }

    @Test
    void assignsEachBotItsOwnXuid() {
        BedrockLoginHelper first = new BedrockLoginHelper(
                null, "TestBot", "1.26.60", "127.0.0.1:19132", null);
        BedrockLoginHelper second = new BedrockLoginHelper(
                null, "TestBot", "1.26.60", "127.0.0.1:19132", null);

        assertNotEquals(first.xuid(), second.xuid());
    }

    @Test
    void serializesMicrosoftLoginPacket() throws Exception {
        KeyPair keyPair = EncryptionUtils.createKeyPair();
        BedrockAuthSession auth = new BedrockAuthSession(
                "TestBot", "1234567890", UUID.randomUUID(), keyPair,
                "mojang.jwt", "identity.jwt", "multiplayer.jwt",
                "https://authorization.franchise.minecraft-services.net",
                "0123456789abcdef");

        BedrockLoginHelper helper = BedrockLoginHelper.forMicrosoft(
                null, auth, "1.26.60", "127.0.0.1:19132");
        LoginPacket packet = helper.buildLoginPacket();

        assertTrue(packet.getAuthPayload() instanceof CertificateChainPayload);
        MicrosoftAuthPayload payload = (MicrosoftAuthPayload) packet.getAuthPayload();
        assertEquals(3, payload.getChain().size());
        assertEquals("mojang.jwt", payload.getChain().get(1));
        assertEquals("identity.jwt", payload.getChain().get(2));
        assertEquals("multiplayer.jwt", payload.token());
        assertEquals("1234567890", helper.xuid());

        BedrockCodec codec = Bedrock_v2225.CODEC.toBuilder()
                .updateSerializer(LoginPacket.class, new MinterLoginSerializer())
                .build();

        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.tryEncode(codec.createHelper(), buffer, packet);

            assertEquals(2225, buffer.readInt(), "protocol version");
            int tokensLength = VarInts.readUnsignedInt(buffer);
            ByteBuf tokens = buffer.readSlice(tokensLength);

            int authLength = tokens.readIntLE();
            String authJson = tokens.readCharSequence(authLength, StandardCharsets.UTF_8).toString();
            int clientLength = tokens.readIntLE();
            String clientJson = tokens.readCharSequence(clientLength, StandardCharsets.UTF_8).toString();
            assertEquals(0, tokens.readableBytes(), "tokens must be fully consumed");

            Map<String, Object> parsed = JsonUtil.parseJson(authJson);
            assertEquals(0, ((Number) parsed.get("AuthenticationType")).intValue());
            assertEquals("multiplayer.jwt", parsed.get("Token"));
            Map<String, Object> certificate = JsonUtil.parseJson((String) parsed.get("Certificate"));
            List<?> chain = (List<?>) certificate.get("chain");
            assertEquals(3, chain.size());
            assertEquals("mojang.jwt", chain.get(1));
            assertEquals("identity.jwt", chain.get(2));

            JsonWebSignature clientToken = new JsonWebSignature();
            clientToken.setCompactSerialization(clientJson);
            Map<String, Object> clientData = JsonUtil.parseJson(clientToken.getUnverifiedPayload());
            assertEquals("0123456789abcdef", clientData.get("PlayFabId"));
        } finally {
            buffer.release();
        }
    }

    @Test
    void serializesLoginWithSessionCodec() throws Exception {
        BedrockLoginHelper helper = new BedrockLoginHelper(
                null, "TestBot", "1.26.60", "127.0.0.1:19132", null);
        LoginPacket packet = helper.buildLoginPacket();

        BedrockCodec codec = Bedrock_v2225.CODEC.toBuilder()
                .updateSerializer(LoginPacket.class, new MinterLoginSerializer())
                .build();

        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.tryEncode(codec.createHelper(), buffer, packet);

            assertEquals(2225, buffer.readInt(), "protocol version");

            int tokensLength = VarInts.readUnsignedInt(buffer);
            ByteBuf tokens = buffer.readSlice(tokensLength);

            int authLength = tokens.readIntLE();
            String auth = tokens.readCharSequence(authLength, StandardCharsets.UTF_8).toString();
            int clientLength = tokens.readIntLE();
            String client = tokens.readCharSequence(clientLength, StandardCharsets.UTF_8).toString();
            assertEquals(0, tokens.readableBytes(), "tokens must be fully consumed");

            Map<String, Object> authJson = JsonUtil.parseJson(auth);
            assertEquals(2, ((Number) authJson.get("AuthenticationType")).intValue());
            Map<String, Object> certificate = JsonUtil.parseJson((String) authJson.get("Certificate"));
            assertEquals(List.of(""), certificate.get("chain"));

            JsonWebSignature token = new JsonWebSignature();
            token.setCompactSerialization((String) authJson.get("Token"));
            assertEquals(EncryptionUtils.ALGORITHM_TYPE, token.getAlgorithmHeaderValue());

            assertTrue(client.contains("."), "client JWT is compact-serialized");
            System.out.println("Serialized login auth=" + auth);
        } finally {
            buffer.release();
        }
    }
}


