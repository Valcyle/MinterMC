package net.mcsmash.mintermc.api;

/**
 * The base interface for all MinterMC plugins.
 * <br>
 * Plugins are loaded from JAR files in the {@code plugins} directory. A plugin
 * receives a {@link PluginContext} when it is enabled and uses it to register
 * chat commands and event listeners.
 */
public interface MinterPlugin {

    /**
     * Called when the plugin is enabled during server startup.
     *
     * @param context the kernel services available to this plugin
     */
    void onEnable(PluginContext context);

    /**
     * Called when the plugin is disabled during server shutdown.
     */
    void onDisable();

    /**
     * Called when a bot session is registered.
     * <br>
     * The default implementation does nothing.
     *
     * @param bot the new bot
     */
    default void onBotJoin(Bot bot) {
    }

    /**
     * Called when a bot session ends.
     * <br>
     * The default implementation does nothing.
     *
     * @param bot the bot that disconnected
     */
    default void onBotLeave(Bot bot) {
    }
}
