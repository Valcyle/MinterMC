package net.mcsmash.mintermc.adapter.java;

import java.util.concurrent.CompletableFuture;

import net.lenni0451.commons.httpclient.HttpClient;
import net.mcsmash.mintermc.api.Block;
import net.mcsmash.mintermc.core.BotOptions;
import net.mcsmash.mintermc.core.BotSession;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;

import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaEditionBotSession extends BotSession {
    private static final Logger logger = LoggerFactory.getLogger(JavaEditionBotSession.class);
    // MCProtocolLib client session
    private ClientSession session;

    public JavaEditionBotSession(String sessionToken) {
        super(sessionToken);
    }

    @Override
    protected void cleanup() {
        logger.info("Cleaning up Java Edition bot session: {}", getSessionToken());
    }

    @Override
    protected void handleNetworkDisconnect() {
        logger.info("Handling network disconnect for Java Edition bot");
        // MCprotocollib disconnection handling
    }

    @Override
    public CompletableFuture<Void> moveTo(double x, double y, double z) {
        return CompletableFuture.runAsync(() -> {
            // TODO: Implement movement logic
        });
    }

    @Override
    public Block getBlockAt(int x, int y, int z) {
        // TODO: Implement block retrieval logic
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
        MinecraftProtocol protocol;

        // Online mode authentication
        if (onlineMode) {
            // TODO: Implement online mode authentication
            try {
                HttpClient httpClient = MinecraftAuth.createHttpClient();
                StepFullJavaSession.FullJavaSession fullJavaSession = MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.getFromInput(
                        httpClient,
                        new StepMsaDeviceCode.MsaDeviceCodeCallback(msaCode -> {
                            logger.info("Authorise your Microsoft account at: {}", msaCode.getDirectVerificationUri());
                            logger.info("Waiting for authentication...");
                        }));
                logger.info("Authenticated as {}", fullJavaSession.getMcProfile().getName());
                GameProfile gameProfile = new GameProfile(fullJavaSession.getMcProfile().getId(),
                        fullJavaSession.getMcProfile().getName());
                protocol = new MinecraftProtocol(gameProfile,
                        fullJavaSession.getMcProfile().getMcToken().getAccessToken());
            } catch (Exception e) {
                logger.error("Failed to authenticate", e);
                logger.warn("Continuing in offline mode");
                protocol = new MinecraftProtocol(username);
            }
        } else {
            protocol = new MinecraftProtocol(username);
        }

        this.setUsername(username);
        this.setVersion(options.version());
        this.setEdition("Java");

        this.session = ClientNetworkSessionFactory.factory().setAddress(host, port).setProtocol(protocol).create();
        SessionService sessionService = new SessionService();
        this.session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);

        this.session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                logger.debug("Received packet: {}", packet.getClass().getSimpleName());
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                logger.info("Disconnected: {} (Reason: {})", event.getReason(), event.getReason());
            }
        });

        this.session.connect();
    }
}
