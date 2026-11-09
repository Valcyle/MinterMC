package net.mcsmash.mintermc.api.event;

import net.mcsmash.mintermc.api.Bot;

/**
 * Reports a change of the bot's health value.
 *
 * @param bot    the bot whose health changed
 * @param health the new health value
 */
public record HealthChangeEvent(Bot bot, float health) {
}
