package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches chat commands to plugin handlers.
 * <br>
 * Commands use a dot prefix, for example {@code .goto 100 64 100}. Messages
 * that are not commands, or that the bot itself sent, are ignored.
 */
public final class CommandRegistry {

    private static final Logger logger = LoggerFactory.getLogger(CommandRegistry.class);

    /** Prefix that marks a chat message as a command. */
    public static final String PREFIX = ".";

    private final Map<String, CommandHandler> commands = new ConcurrentHashMap<>();

    /**
     * Registers a command handler.
     *
     * @param name    the command name, without the prefix
     * @param handler the handler to invoke
     */
    public void register(String name, CommandHandler handler) {
        if (name == null || name.isBlank() || handler == null) {
            throw new IllegalArgumentException("A command needs a name and a handler");
        }
        commands.put(name.toLowerCase(Locale.ROOT), handler);
    }

    /**
     * @param name the command name, without the prefix
     * @return true when a handler is registered
     */
    public boolean isRegistered(String name) {
        return commands.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Dispatches a chat message when it is a registered command.
     *
     * @param session the bot that received the message
     * @param sender  the sender name reported by the server
     * @param content the message text
     * @return true when a command was executed
     */
    public boolean dispatch(BotSession session, String sender, String content) {
        if (content == null || !content.startsWith(PREFIX)) {
            return false;
        }
        if (sender != null && sender.equals(session.getUsername())) {
            // The server echoes the bot's own chat; do not run its commands.
            return false;
        }
        String body = content.substring(PREFIX.length()).trim();
        if (body.isEmpty()) {
            return false;
        }
        String[] parts = body.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        CommandHandler handler = commands.get(name);
        if (handler == null) {
            return false;
        }
        List<String> args = parts.length > 1
                ? new ArrayList<>(Arrays.asList(parts).subList(1, parts.length))
                : List.of();
        try {
            handler.execute(session, name, args);
        } catch (Exception e) {
            logger.warn("Command '{}' failed for {}", name, session.getUsername(), e);
        }
        return true;
    }
}
