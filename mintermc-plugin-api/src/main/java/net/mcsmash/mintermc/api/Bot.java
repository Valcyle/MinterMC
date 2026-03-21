package net.mcsmash.mintermc.api;

import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;

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
     * Sets the username of the bot.
     * 
     * @param username The username of the bot
     */
    void setUsername(String username);

    /**
     * Sets the version of the Minecraft client.
     * 
     * @param version The version of the Minecraft client
     */
    void setVersion(String version);

    /**
     * Sets the edition of the Minecraft client.
     * 
     * @param edition The edition of the Minecraft client
     */
    void setEdition(String edition);

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
     * @param position The position to move to
     * @return A CompletableFuture representing the completion of the movement
     */
    CompletableFuture<Void> moveTo(Vector3 position);

    /**
     * Retrieves the block data around the bot that is currently recognized.
     * <br>
     * If an uncached area is requested, an exception may be thrown or an empty/air
     * block will be returned (depending on implementation).
     *
     * @param position The position to get the block at
     * @return The block information at the specified coordinates
     */
    Block getBlockAt(Vector3 position);

    /**
     * Retrieves the location of the bot.
     * 
     * @return The location of the bot
     */
    Location getLocation();

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
