package net.mcsmash.mintermc.api.bot;

/**
 * Selects how a bot proves its identity to the server.
 */
public enum AuthMode {

    /**
     * Offline identity. The bot presents a self-signed token derived from its
     * name. Only offline-mode servers accept it.
     */
    OFFLINE,

    /**
     * Microsoft account. The bot signs in through the Microsoft device code
     * flow and presents the Xbox/Minecraft tokens issued by Mojang. Required
     * for online-mode servers.
     */
    MICROSOFT
}
