package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.MinterPlugin;
import net.mcsmash.mintermc.api.event.BotDisconnectEvent;
import net.mcsmash.mintermc.api.event.BotListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Responsible for loading, enabling, and managing third-party plugins.
 * <br>
 * Searches the 'plugins' directory for JAR files, identifies classes
 * implementing the MinterPlugin interface, and loads them dynamically.
 */
public class PluginManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

    private final File pluginDir;
    private final File dataRoot;
    private final BotManager botManager;
    private final CommandRegistry commandRegistry;
    private final List<LoadedPlugin> loadedPlugins = new CopyOnWriteArrayList<>();
    private final List<BotListener> globalListeners = new CopyOnWriteArrayList<>();
    private final Set<BotSession> wiredSessions =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final BotListener leaveListener = new BotListener() {
        @Override
        public void onDisconnect(BotDisconnectEvent event) {
            for (LoadedPlugin loaded : loadedPlugins) {
                try {
                    loaded.plugin().onBotLeave(event.bot());
                } catch (Exception e) {
                    logger.error("Error in plugin {} while handling a bot leave",
                            loaded.name(), e);
                }
            }
        }
    };

    /**
     * @param pluginDir       the directory that holds plugin JARs
     * @param dataRoot        the directory that holds per-plugin data folders
     * @param botManager      the bot registry
     * @param commandRegistry the registry used for chat commands
     */
    public PluginManager(File pluginDir, File dataRoot, BotManager botManager,
            CommandRegistry commandRegistry) {
        this.pluginDir = pluginDir;
        this.dataRoot = dataRoot;
        this.botManager = botManager;
        this.commandRegistry = commandRegistry;
        if (!this.pluginDir.exists()) {
            this.pluginDir.mkdirs();
        }
    }

    /**
     * Scans the plugins directory and loads any valid plugin JARs.
     */
    public void loadPlugins() {
        File[] files = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            logger.info("No plugins found in {}", pluginDir.getAbsolutePath());
            return;
        }

        for (File file : files) {
            try {
                loadPluginJar(file);
            } catch (Exception e) {
                logger.error("Failed to load plugin jar: {}", file.getName(), e);
            }
        }
    }

    private void loadPluginJar(File jar) throws IOException, ClassNotFoundException,
            ReflectiveOperationException {
        logger.info("Loading plugin from {}", jar.getName());
        try (JarFile jarFile = new JarFile(jar)) {
            URL[] urls = { jar.toURI().toURL() };
            URLClassLoader classLoader =
                    URLClassLoader.newInstance(urls, this.getClass().getClassLoader());

            // Simple loading: we search for the first class that implements MinterPlugin.
            // A more robust implementation would use a plugin.yml or manifest file.
            for (JarEntry entry : jarFile.stream()
                    .filter(e -> e.getName().endsWith(".class")).toList()) {
                String className = entry.getName().replace('/', '.').replace(".class", "");
                Class<?> clazz = classLoader.loadClass(className);

                if (MinterPlugin.class.isAssignableFrom(clazz) && !clazz.isInterface()
                        && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    MinterPlugin plugin =
                            (MinterPlugin) clazz.getDeclaredConstructor().newInstance();
                    loadedPlugins.add(new LoadedPlugin(plugin, clazz.getSimpleName()));
                    logger.info("Successfully loaded plugin class: {}", className);
                    return; // Support one plugin per JAR for now
                }
            }
        }
        logger.warn("No MinterPlugin implementation found in {}", jar.getName());
    }

    /**
     * Enables all loaded plugins with a context bound to their data folder.
     */
    public void enablePlugins() {
        for (LoadedPlugin loaded : loadedPlugins) {
            File dataFolder = new File(dataRoot, loaded.name());
            CorePluginContext context = new CorePluginContext(
                    loaded.name(), botManager, this, commandRegistry, dataFolder);
            try {
                loaded.plugin().onEnable(context);
                logger.info("Enabled plugin {}", loaded.name());
            } catch (Exception e) {
                logger.error("Error while enabling plugin {}", loaded.name(), e);
            }
        }
    }

    /**
     * Disables all loaded plugins sequentially.
     */
    public void disablePlugins() {
        for (LoadedPlugin loaded : loadedPlugins) {
            try {
                loaded.plugin().onDisable();
            } catch (Exception e) {
                logger.error("Error while disabling plugin {}", loaded.name(), e);
            }
        }
        loadedPlugins.clear();
        globalListeners.clear();
        wiredSessions.clear();
    }

    /**
     * Wires a new session to the plugins.
     *
     * @param session the session that was registered
     */
    public void notifyBotJoin(BotSession session) {
        if (wiredSessions.add(session)) {
            for (BotListener listener : globalListeners) {
                session.subscribe(listener);
            }
            session.subscribe(leaveListener);
        }
        for (LoadedPlugin loaded : loadedPlugins) {
            try {
                loaded.plugin().onBotJoin(session);
            } catch (Exception e) {
                logger.error("Error in plugin {} while handling a bot join",
                        loaded.name(), e);
            }
        }
    }

    /**
     * Adds a listener that receives events from every current and future bot.
     *
     * @param listener the listener to add
     */
    public void addGlobalListener(BotListener listener) {
        if (globalListeners.add(listener)) {
            for (BotSession session : botManager.getAllSessions()) {
                session.subscribe(listener);
            }
        }
    }

    /**
     * Removes a global listener from every bot.
     *
     * @param listener the listener to remove
     */
    public void removeGlobalListener(BotListener listener) {
        if (globalListeners.remove(listener)) {
            for (BotSession session : botManager.getAllSessions()) {
                session.unsubscribe(listener);
            }
        }
    }

    /**
     * A plugin instance together with the name used for its data folder.
     *
     * @param plugin the plugin instance
     * @param name   the simple class name
     */
    private record LoadedPlugin(MinterPlugin plugin, String name) {
    }
}
