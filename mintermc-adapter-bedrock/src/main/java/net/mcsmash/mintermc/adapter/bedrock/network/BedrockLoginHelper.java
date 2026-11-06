package net.mcsmash.mintermc.adapter.bedrock.network;

import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwx.HeaderParameterNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Performs the Bedrock Edition login sequence for a single bot session.
 * <br>
 * The helper owns the key pair used for the self-signed login token and for the
 * server encryption handshake. It currently implements offline (self-signed)
 * login only, which is what an offline-mode Bedrock server expects.
 */
public class BedrockLoginHelper {

    private static final Logger logger = LoggerFactory.getLogger(BedrockLoginHelper.class);

    /** Bedrock skin images are always 64x64 RGBA in the login payload. */
    private static final int SKIN_WIDTH = 64;
    private static final int SKIN_HEIGHT = 64;

    private static final String SKIN_RESOURCE_PATCH =
            "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";

    private static final String SKIN_GEOMETRY =
            "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":"
                    + "{\"identifier\":\"geometry.humanoid.custom\",\"texture_width\":64,"
                    + "\"texture_height\":64,\"visible_bounds_width\":2,"
                    + "\"visible_bounds_height\":3.5,\"visible_bounds_offset\":[0,1.5,0]},"
                    + "\"bones\":[]}]}";

    private static final String MULTIPLAYER_AUDIENCE = "api://auth-minecraft-services/multiplayer";

    /**
     * MinterMC's own generated classic skin.
     * <br>
     * This is deliberately generated instead of bundling Mojang's Steve/persona
     * skin. The retail skin textures and geometry are Mojang assets and are not
     * covered by the MIT-licensed data packages they are often copied from.
     */
    private static final Map<String, Object> DEFAULT_SKIN = createDefaultSkin();

    private final BedrockClientSession session;
    private final KeyPair keyPair;
    private final String username;
    private final String gameVersion;
    private final String serverAddress;
    private final UUID identity;

    /**
     * Creates a login helper for the given session.
     *
     * @param session       the Bedrock client session used to send login packets
     * @param username      the player name shown to the server
     * @param gameVersion   the client game version sent in the client data
     * @param serverAddress the target address in {@code host:port} form
     * @param identity      the offline UUID of the bot, or {@code null} to derive one
     */
    public BedrockLoginHelper(BedrockClientSession session, String username, String gameVersion,
            String serverAddress, UUID identity) {
        this(session, username, gameVersion, serverAddress, identity, null);
    }

    /**
     * Creates a login helper that reuses an existing key pair.
     * <br>
     * NetherNet requires the transport identity assertion to use the same key as
     * the Bedrock login, otherwise BDS refuses the join.
     *
     * @param session       the Bedrock client session used to send login packets
     * @param username      the player name shown to the server
     * @param gameVersion   the client game version sent in the client data
     * @param serverAddress the target address in {@code host:port} form
     * @param identity      the offline UUID of the bot, or {@code null} to derive one
     * @param keyPair       the key pair to use, or {@code null} to generate one
     */
    public BedrockLoginHelper(BedrockClientSession session, String username, String gameVersion,
            String serverAddress, UUID identity, KeyPair keyPair) {
        this.session = session;
        this.keyPair = keyPair != null ? keyPair : EncryptionUtils.createKeyPair();
        this.username = username;
        this.gameVersion = gameVersion;
        this.serverAddress = serverAddress;
        this.identity = identity != null ? identity
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Logs in using a self-signed identity token and sends the login packet.
     * <br>
     * Protocol 944 and later use the OIDC-style {@link TokenPayload}; the token is
     * signed by the bot's own key and contains the client public key in {@code cpk}.
     */
    public void handleOfflineLogin() {
        try {
            session.sendPacketImmediately(buildLoginPacket());
            logger.info("Sent Bedrock offline login packet for {}", username);
        } catch (Exception e) {
            logger.error("Failed to create Bedrock login packet for {}", username, e);
            session.disconnect("Login failed: " + e.getMessage());
        }
    }

    /**
     * Builds the offline login packet without sending it.
     * <br>
     * Exposed separately so the token contents can be validated without a
     * network connection.
     *
     * @return a ready-to-send login packet
     * @throws Exception if the tokens cannot be signed
     */
    public LoginPacket buildLoginPacket() throws Exception {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        LoginPacket loginPacket = new LoginPacket();
        loginPacket.setProtocolVersion(Bedrock_v2225.CODEC.getProtocolVersion());
        loginPacket.setAuthPayload(new TokenPayload(createIdentityToken(publicKey), AuthType.SELF_SIGNED));
        loginPacket.setClientJwt(createClientDataToken(publicKey));
        return loginPacket;
    }

    /**
     * Online (Microsoft account) login is not implemented yet.
     * <br>
     * The current login flow only supports offline-mode servers. Disconnecting
     * with a clear reason avoids silently joining with an invalid identity.
     */
    public void handleOnlineLogin() {
        logger.error("Bedrock online login is not implemented yet; use an offline-mode server");
        session.disconnect("Online mode is not supported yet");
    }

    /**
     * Completes the server encryption handshake.
     * <br>
     * The server sends its public key and salt in a signed JWT. The bot derives
     * the shared AES key, installs it on the session, and answers with a
     * {@link ClientToServerHandshakePacket}.
     *
     * @param jwt the JWT from {@code ServerToClientHandshakePacket}
     * @throws Exception if the JWT cannot be parsed or the key cannot be derived
     */
    public void handleServerToClientHandshake(String jwt) throws Exception {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setCompactSerialization(jwt);

        String serverKeyBase64 = (String) signature.getHeader(HeaderParameterNames.X509_URL);
        Map<String, Object> payload = JsonUtil.parseJson(signature.getUnverifiedPayload());
        byte[] salt = Base64.getDecoder().decode((String) payload.get("salt"));
        PublicKey serverKey = EncryptionUtils.parseKey(serverKeyBase64);

        SecretKey secretKey = EncryptionUtils.getSecretKey(keyPair.getPrivate(), serverKey, salt);
        session.enableEncryption(secretKey);
        session.sendPacketImmediately(new ClientToServerHandshakePacket());
        logger.info("Completed Bedrock encryption handshake for {}", username);
    }

    /**
     * Builds the self-signed multiplayer token used as the login auth payload.
     *
     * @param publicKey the base64 encoded X.509 public key of the bot
     * @return a compact ES384 JWT
     * @throws Exception if the token cannot be signed
     */
    private String createIdentityToken(String publicKey) throws Exception {
        JwtClaims claims = new JwtClaims();
        claims.setIssuer("self");
        claims.setAudience(MULTIPLAYER_AUDIENCE);
        claims.setClaim("cpk", publicKey);
        claims.setClaim("xid", "0");
        claims.setClaim("xname", username);
        claims.setClaim("identity", identity.toString());
        claims.setClaim("leguuid", identity.toString());
        claims.setClaim("mid", "");
        claims.setIssuedAtToNow();
        claims.setNotBeforeMinutesInThePast(1);
        claims.setExpirationTimeMinutesInTheFuture(60);

        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(EncryptionUtils.ALGORITHM_TYPE);
        signature.setHeader(HeaderParameterNames.X509_URL, publicKey);
        signature.setKey(keyPair.getPrivate());
        signature.setPayload(claims.toJson());
        return signature.getCompactSerialization();
    }

    /**
     * Builds and signs the client data token sent with the login packet.
     *
     * @param publicKey the base64 encoded X.509 public key of the bot
     * @return a compact ES384 JWT containing the client and skin data
     * @throws Exception if the token cannot be signed
     */
    private String createClientDataToken(String publicKey) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>(DEFAULT_SKIN);
        data.put("GameVersion", gameVersion);
        data.put("ServerAddress", serverAddress);
        data.put("ThirdPartyName", username);
        data.put("LanguageCode", "en_US");

        data.put("PlayFabId", randomPlayFabId());
        data.put("DeviceId", UUID.randomUUID().toString());
        data.put("DeviceModel", "MinterMC");
        data.put("DeviceOS", 7); // Windows
        data.put("UIProfile", 0);
        data.put("GuiScale", -1);
        data.put("GraphicsMode", 1);
        data.put("CurrentInputMode", 1);
        data.put("DefaultInputMode", 1);
        data.put("PlatformOfflineId", "");
        data.put("PlatformOnlineId", "");
        data.put("SelfSignedId", UUID.randomUUID().toString());
        data.put("ClientRandomId", System.currentTimeMillis());
        data.put("IsEditorMode", false);
        data.put("FilterProfanity", false);
        data.put("ClientIsEditorCapable", false);
        data.put("ClientEditorConnectionIntent", 0);
        data.put("TrustedSkin", false);
        data.put("OverrideSkin", false);
        data.put("CompatibleWithClientSideChunkGen", false);
        data.put("MaxViewDistance", 0);
        data.put("MemoryTier", 0);
        data.put("PlatformType", 0);

        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(EncryptionUtils.ALGORITHM_TYPE);
        signature.setHeader(HeaderParameterNames.X509_URL, publicKey);
        signature.setKey(keyPair.getPrivate());
        signature.setPayload(JsonUtil.toJson(data));
        return signature.getCompactSerialization();
    }

    private static Map<String, Object> createDefaultSkin() {
        Map<String, Object> skin = new LinkedHashMap<>();
        skin.put("SkinId", UUID.randomUUID().toString());
        skin.put("SkinData", Base64.getEncoder().encodeToString(createSolidSkin()));
        skin.put("SkinImageWidth", SKIN_WIDTH);
        skin.put("SkinImageHeight", SKIN_HEIGHT);
        skin.put("SkinResourcePatch", encodeUtf8(SKIN_RESOURCE_PATCH));
        skin.put("SkinGeometryData", encodeUtf8(SKIN_GEOMETRY));
        skin.put("SkinGeometryDataEngineVersion", encodeUtf8("1.14.0"));
        skin.put("SkinAnimationData", "");
        skin.put("AnimatedImageData", List.of());
        skin.put("PersonaPieces", List.of());
        skin.put("PieceTintColors", List.of());
        skin.put("PersonaSkin", false);
        skin.put("PremiumSkin", false);
        skin.put("CapeId", "");
        skin.put("CapeData", "");
        skin.put("CapeImageWidth", 0);
        skin.put("CapeImageHeight", 0);
        skin.put("CapeOnClassicSkin", false);
        skin.put("ArmSize", "wide");
        skin.put("SkinColor", "#FFFFFF");
        return Map.copyOf(skin);
    }

    private static byte[] createSolidSkin() {
        byte[] skin = new byte[SKIN_WIDTH * SKIN_HEIGHT * 4];
        for (int i = 0; i < skin.length; i += 4) {
            skin[i] = (byte) 0x3F;
            skin[i + 1] = (byte) 0x8F;
            skin[i + 2] = (byte) 0xD4;
            skin[i + 3] = (byte) 0xFF;
        }
        return skin;
    }

    private static String encodeUtf8(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomPlayFabId() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return hex.substring(0, 16).toLowerCase();
    }
}


