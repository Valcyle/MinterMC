package net.mcsmash.mintermc.adapter.java;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.mcsmash.mintermc.core.BotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaEditionBotSession extends BotSession {
    private static final Logger logger = LoggerFactory.getLogger(JavaEditionBotSession.class);

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
}
