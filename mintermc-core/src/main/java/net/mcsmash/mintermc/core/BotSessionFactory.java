package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.protocol.LoginRequest;

/**
 * A factory interface for creating BotSession instances.
 * <br>
 * This allows the core module to remain independent of specific protocol adapters.
 */
@FunctionalInterface
public interface BotSessionFactory {
    /**
     * Creates a new BotSession based on the login request.
     *
     * @param sessionToken The unique session token for this bot
     * @param request      The gRPC login request containing connection details
     * @return A new BotSession implementation
     */
    BotSession create(String sessionToken, LoginRequest request);
}
