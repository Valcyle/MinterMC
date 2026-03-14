package net.mcsmash.mintermc.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;

/**
 * Manages all active bot sessions within the MinterMC kernel.
 */
public class BotManager {

    private final ConcurrentHashMap<String, BotSession> sessionsByToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BotSession> sessionsByUuid = new ConcurrentHashMap<>();

    /**
     * Registers a new bot session with the manager.
     *
     * @param session The bot session to register
     */
    public void registerSession(BotSession session) {
        sessionsByToken.put(session.getSessionToken(), session);
        if (session.getUniqueId() != null) {
            sessionsByUuid.put(session.getUniqueId(), session);
        }
    }

    /**
     * Retrieves a bot session by its authentication token.
     *
     * @param token The session token generated during login
     * @return The associated BotSession, or null if not found
     */
    public BotSession getSessionByToken(String token) {
        return sessionsByToken.get(token);
    }

    /**
     * Removes a session from the manager and shuts down its virtual thread.
     * 
     * @param token The session token
     */
    public void removeSession(String token) {
        BotSession session = sessionsByToken.remove(token);
        if (session != null) {
            if (session.getUniqueId() != null) {
                sessionsByUuid.remove(session.getUniqueId());
            }
            session.disconnect();
        }
    }

    /**
     * @return A collection of all currently active bot sessions
     */
    public Collection<BotSession> getAllSessions() {
        return sessionsByToken.values();
    }
}
