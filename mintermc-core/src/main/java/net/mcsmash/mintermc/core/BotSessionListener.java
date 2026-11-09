package net.mcsmash.mintermc.core;

/**
 * Receives asynchronous events from a bot session.
 * <br>
 * Adapters call the matching notification method, and the kernel forwards the
 * event to every SDK that is subscribed through {@code EventStream}. Listener
 * callbacks run on the bot's own event loop, so implementations must not block.
 */
public interface BotSessionListener {

    /**
     * Called when the server sends a chat message or a system notice.
     *
     * @param sender  the player name, or an empty string for server messages
     * @param content the plain text of the message
     */
    void onChatMessage(String sender, String content);
}
