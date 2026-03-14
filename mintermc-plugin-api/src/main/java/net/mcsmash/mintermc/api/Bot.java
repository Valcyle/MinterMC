package net.mcsmash.mintermc.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The main interface for interacting with a virtualized Minecraft bot client.
 * <br>
 * Protocol differences (Java Edition / Bedrock Edition) are abstracted away
 * behind this interface.
 * Plugin developers will use this interface to perform movements, retrieve
 * environment data, and send high-level packets.
 */
public interface Bot {

    /**
     * Retrieves the unique identifier of the bot within the server.
     *
     * @return The unique UUID of this bot
     */
    UUID getUniqueId();

    /**
     * Retrieves the in-game username of the bot.
     *
     * @return The name (player name) of this bot on the server
     */
    String getUsername();

    /**
     * Retrieves the version of the Minecraft client.
     * 
     * @return The version of the Minecraft client
     */
    String getVersion();

    /**
     * Retrieves the edition of the Minecraft client.
     * 
     * @return The edition of the Minecraft client
     */
    String getEdition();

    /**
     * Asynchronously moves the bot towards the specified coordinates.
     * <br>
     * This method itself does not perform pathfinding. It may emulate "linear
     * movement" or
     * "simple forward progression" packets provided by the kernel.
     * More advanced pathfinding (like A*) is intended to be implemented or
     * overridden by plugins
     * by combining this interface with surrounding block data.
     *
     * @param x The target X coordinate
     * @param y The target Y coordinate
     * @param z The target Z coordinate
     * @return A CompletableFuture representing the completion of the movement
     */
    CompletableFuture<Void> moveTo(double x, double y, double z);

    /**
     * Retrieves the block data around the bot that is currently recognized.
     * <br>
     * If an uncached area is requested, an exception may be thrown or an empty/air
     * block will be returned (depending on implementation).
     *
     * @param x The target X coordinate
     * @param y The target Y coordinate
     * @param z The target Z coordinate
     * @return The block information at the specified coordinates
     */
    Block getBlockAt(int x, int y, int z);

    /**
     * Sends a message to the in-game chat.
     *
     * @param message The string message to send
     */
    void chat(String message);

    /**
     * Returns the current connection state of this bot (e.g., logged in,
     * disconnected).
     *
     * @return True if connected, false otherwise
     */
    boolean isConnected();
}
