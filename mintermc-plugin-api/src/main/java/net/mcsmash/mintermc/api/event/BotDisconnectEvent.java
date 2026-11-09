package net.mcsmash.mintermc.api.event;

import net.mcsmash.mintermc.api.Bot;

/**
 * Signals that a bot session ended.
 *
 * @param bot    the bot whose session ended
 * @param reason a short description of why the session ended
 */
public record BotDisconnectEvent(Bot bot, String reason) {
}
