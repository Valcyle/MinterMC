package net.mcsmash.mintermc.api;

import net.mcsmash.mintermc.api.event.BotListener;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

/**
 * The kernel services available to a plugin.
 * <br>
 * A plugin receives one context in {@link MinterPlugin#onEnable(PluginContext)}
 * and keeps it for its lifetime. Commands and event listeners registered here
 * stay active until the kernel shuts down.
 */
public interface PluginContext {

    /**
     * @return a logger tagged with the plugin name
     */
    PluginLogger getLogger();

    /**
     * @return a directory the plugin may use for its own files
     */
    File getDataFolder();

    /**
     * @return all bot sessions that are currently registered
     */
    Collection<Bot> getBots();

    /**
     * Finds a bot by its MinterMC unique id.
     *
     * @param uniqueId the bot uuid
     * @return the bot, or {@code null} when it is not connected
     */
    Bot getBot(UUID uniqueId);

    /**
     * Registers a chat command that is triggered by {@code .<name>} messages
     * received from other players.
     *
     * @param name    the command name without the prefix
     * @param handler the handler to invoke
     */
    void registerCommand(String name, CommandHandler handler);

    /**
     * Subscribes a listener to the events of every bot, including bots that
     * connect later.
     *
     * @param listener the listener to add
     */
    void subscribe(BotListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void unsubscribe(BotListener listener);
}
