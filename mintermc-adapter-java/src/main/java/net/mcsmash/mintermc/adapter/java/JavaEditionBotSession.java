package net.mcsmash.mintermc.adapter.java;

import java.util.concurrent.CompletableFuture;

import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.bot.BotOptions;
import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;
import net.mcsmash.mintermc.auth.JavaAuthSession;
import net.mcsmash.mintermc.auth.MicrosoftAuthService;
import net.mcsmash.mintermc.core.BotSession;

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
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
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
        return this.session != null && this.session.isConnected();
    }

    // Connection methods
    public void connect(String host, int port, String username, boolean onlineMode,
            BotOptions options, MicrosoftAuthService authService) {
        MinecraftProtocol protocol;

        if (onlineMode) {
            if (authService == null) {
                throw new IllegalStateException("Microsoft authentication is not configured");
            }
            try {
                JavaAuthSession auth = authService.loginJava(options.account());
                username = auth.name();
                logger.info("Authenticated as {} ({})", auth.name(), auth.uuid());
                protocol = new MinecraftProtocol(
                        new GameProfile(auth.uuid(), auth.name()), auth.accessToken());
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Microsoft sign-in failed: " + e.getMessage(), e);
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
                // Update position
                if (packet instanceof ClientboundPlayerPositionPacket p) {
                    double[] pos = p.getPosition().toArray();
                    Vector3 vector3 = new Vector3(pos[0], pos[1], pos[2]);
                    setLocation(new Location(vector3, (float) p.getYRot(), (float) p.getXRot()));
                    logger.info("Position updated: {}", vector3);
                }
                // Update health
                else if (packet instanceof ClientboundSetHealthPacket p) {
                    setHealth(p.getHealth());
                    logger.info("Health updated: {}", p.getHealth());
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                logger.info("Disconnected: {} (Reason: {})", event.getReason(), event.getReason());
            }
        });

        this.session.connect();
    }
}
