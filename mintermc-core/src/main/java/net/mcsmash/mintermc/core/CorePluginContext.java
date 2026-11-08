package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.Bot;
import net.mcsmash.mintermc.api.CommandHandler;
import net.mcsmash.mintermc.api.PluginContext;
import net.mcsmash.mintermc.api.PluginLogger;
import net.mcsmash.mintermc.api.event.BotListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

/**
 * The kernel services handed to a plugin when it is enabled.
 */
final class CorePluginContext implements PluginContext {

    private final String pluginName;
    private final BotManager botManager;
    private final PluginManager pluginManager;
    private final CommandRegistry commandRegistry;
    private final File dataFolder;
    private final Logger logger;
    private final PluginLogger pluginLogger;

    CorePluginContext(String pluginName, BotManager botManager, PluginManager pluginManager,
            CommandRegistry commandRegistry, File dataFolder) {
        this.pluginName = pluginName;
        this.botManager = botManager;
        this.pluginManager = pluginManager;
        this.commandRegistry = commandRegistry;
        this.dataFolder = dataFolder;
        this.logger = LoggerFactory.getLogger("plugin." + pluginName);
        this.pluginLogger = new PluginLogger() {
            @Override
            public void info(String message, Object... args) {
                logger.info(message, args);
            }

            @Override
            public void warn(String message, Object... args) {
                logger.warn(message, args);
            }

            @Override
            public void error(String message, Object... args) {
                logger.error(message, args);
            }
        };
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warn("Could not create the data folder {}", dataFolder.getAbsolutePath());
        }
    }

    @Override
    public PluginLogger getLogger() {
        return pluginLogger;
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public Collection<Bot> getBots() {
        return java.util.List.copyOf(botManager.getAllSessions());
    }

    @Override
    public Bot getBot(UUID uniqueId) {
        return botManager.getSessionByUniqueId(uniqueId);
    }

    @Override
    public void registerCommand(String name, CommandHandler handler) {
        commandRegistry.register(name, handler);
    }

    @Override
    public void subscribe(BotListener listener) {
        pluginManager.addGlobalListener(listener);
    }

    @Override
    public void unsubscribe(BotListener listener) {
        pluginManager.removeGlobalListener(listener);
    }

    @Override
    public String toString() {
        return "PluginContext{" + pluginName + "}";
    }
}
