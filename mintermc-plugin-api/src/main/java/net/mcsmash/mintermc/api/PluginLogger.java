package net.mcsmash.mintermc.api;

/**
 * A small logging facade for plugins.
 * <br>
 * The kernel forwards these messages to its logging backend, so plugins do not
 * need a logging library of their own.
 */
public interface PluginLogger {

    /**
     * Logs an informational message. Arguments use {@code {}} placeholders.
     *
     * @param message the message pattern
     * @param args    the placeholder values
     */
    void info(String message, Object... args);

    /**
     * Logs a warning.
     *
     * @param message the message pattern
     * @param args    the placeholder values
     */
    void warn(String message, Object... args);

    /**
     * Logs an error.
     *
     * @param message the message pattern
     * @param args    the placeholder values
     */
    void error(String message, Object... args);
}
