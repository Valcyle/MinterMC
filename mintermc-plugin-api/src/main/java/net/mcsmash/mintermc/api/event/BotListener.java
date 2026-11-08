package net.mcsmash.mintermc.api.event;

import net.mcsmash.mintermc.api.Bot;

/**
 * Receives events from a bot.
 * <br>
 * All methods have empty defaults, so a listener only overrides the events it
 * needs. Callbacks run on the bot's event loop and must not block.
 */
public interface BotListener {

    /**
     * Called for chat and system messages the server sends to this bot.
     *
     * @param event the message
     */
    default void onChat(ChatMessageEvent event) {
    }

    /**
     * Called when the bot enters the world.
     *
     * @param event the spawn notification
     */
    default void onSpawn(BotSpawnEvent event) {
    }

    /**
     * Called when the session ends, either from a disconnect or a shutdown.
     *
     * @param event the disconnect notification
     */
    default void onDisconnect(BotDisconnectEvent event) {
    }

    /**
     * Called when the reported health changes.
     *
     * @param event the health change
     */
    default void onHealthChange(HealthChangeEvent event) {
    }
}
