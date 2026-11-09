package net.mcsmash.mintermc.api.event;

import net.mcsmash.mintermc.api.Bot;

/**
 * A chat or system message received by a bot.
 *
 * @param bot     the bot that received the message
 * @param sender  the player name, or an empty string for server messages
 * @param content the plain text of the message
 */
public record ChatMessageEvent(Bot bot, String sender, String content) {
}
