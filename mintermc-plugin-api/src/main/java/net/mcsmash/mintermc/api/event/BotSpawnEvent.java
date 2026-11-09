package net.mcsmash.mintermc.api.event;

import net.mcsmash.mintermc.api.Bot;

/**
 * Signals that a bot entered the world and can act.
 *
 * @param bot the bot that spawned
 */
public record BotSpawnEvent(Bot bot) {
}
