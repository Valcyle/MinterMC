package net.mcsmash.mintermc.auth;

import java.security.KeyPair;
import java.util.UUID;

/**
 * The Bedrock Edition credentials produced by a Microsoft sign-in.
 *
 * @param displayName      the Xbox gamertag
 * @param xuid             the Xbox user id
 * @param identity         the Bedrock identity UUID
 * @param keyPair          the session key pair referenced by both tokens
 * @param mojangJwt        the Mojang-signed certificate chain entry
 * @param identityJwt      the client-signed certificate chain entry
 * @param multiplayerToken the token used for the NetherNet identity assertion
 * @param issuer           the issuer domain of {@code multiplayerToken}
 */
public record BedrockAuthSession(
        String displayName,
        String xuid,
        UUID identity,
        KeyPair keyPair,
        String mojangJwt,
        String identityJwt,
        String multiplayerToken,
        String issuer) {
}
