package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.MinterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
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
    private final List<MinterPlugin> loadedPlugins = new ArrayList<>();

    public PluginManager(File pluginDir) {
        this.pluginDir = pluginDir;
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

    private void loadPluginJar(File jar) throws IOException, ClassNotFoundException, ReflectiveOperationException {
        logger.info("Loading plugin from {}", jar.getName());
        try (JarFile jarFile = new JarFile(jar)) {
            URL[] urls = { jar.toURI().toURL() };
            URLClassLoader classLoader = URLClassLoader.newInstance(urls, this.getClass().getClassLoader());

            // Simple loading: we search for the first class that implements MinterPlugin.
            // A more robust implementation would use a plugin.yml or manifest file.
            for (JarEntry entry : jarFile.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                String className = entry.getName().replace('/', '.').replace(".class", "");
                Class<?> clazz = classLoader.loadClass(className);
                
                if (MinterPlugin.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    MinterPlugin plugin = (MinterPlugin) clazz.getDeclaredConstructor().newInstance();
                    loadedPlugins.add(plugin);
                    logger.info("Successfully loaded plugin class: {}", className);
                    return; // Support one plugin per JAR for now
                }
            }
        }
        logger.warn("No MinterPlugin implementation found in {}", jar.getName());
    }

    /**
     * Enables all loaded plugins sequentially.
     */
    public void enablePlugins() {
        for (MinterPlugin plugin : loadedPlugins) {
            try {
                plugin.onEnable();
            } catch (Exception e) {
                logger.error("Error while enabling plugin {}", plugin.getClass().getName(), e);
            }
        }
    }

    /**
     * Disables all loaded plugins sequentially.
     */
    public void disablePlugins() {
        for (MinterPlugin plugin : loadedPlugins) {
            try {
                plugin.onDisable();
            } catch (Exception e) {
                logger.error("Error while disabling plugin {}", plugin.getClass().getName(), e);
            }
        }
        loadedPlugins.clear();
    }
}
