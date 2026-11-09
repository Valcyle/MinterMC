package net.mcsmash.mintermc.auth;

import java.util.UUID;

/**
 * The Java Edition credentials produced by a Microsoft sign-in.
 *
 * @param uuid        the Minecraft profile UUID
 * @param name        the Minecraft profile name
 * @param accessToken the Minecraft services access token
 */
public record JavaAuthSession(UUID uuid, String name, String accessToken) {
}
