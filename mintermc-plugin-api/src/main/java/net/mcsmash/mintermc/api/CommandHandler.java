package net.mcsmash.mintermc.api;

import java.util.List;

/**
 * Handles a chat command registered through
 * {@link PluginContext#registerCommand(String, CommandHandler)}.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * @param bot     the bot that received the command
     * @param command the command name without the prefix
     * @param args    the whitespace separated arguments
     */
    void execute(Bot bot, String command, List<String> args);
}
