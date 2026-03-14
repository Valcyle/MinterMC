package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.Bot;
import net.mcsmash.mintermc.api.Block;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single bot instance managed by the kernel.
 * <br>
 * This class implements the "Agent-per-Bot" model using Virtual Threads.
 * Each BotSession runs its own event loop and maintains its own state,
 * preventing interference between hundreds of connected bots.
 */
public abstract class BotSession implements Bot, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BotSession.class);

    private final String sessionToken;
    private final BlockingQueue<Runnable> eventQueue;
    private volatile boolean running;
    private Thread virtualThread;

    public BotSession(String sessionToken) {
        this.sessionToken = sessionToken;
        this.eventQueue = new LinkedBlockingQueue<>();
        this.running = false;
    }

    /**
     * @return The authentication token assigned to this specific bot connection
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Starts the bot's dedicated Virtual Thread event loop.
     */
    public void startEventLoop() {
        if (running)
            return;
        running = true;
        this.virtualThread = Thread.ofVirtual().name("bot-session-" + sessionToken.substring(0, 8)).start(this);
    }

    /**
     * The main event loop for this bot session.
     * All protocol events and API requests routed to this bot will be processed
     * sequentially here.
     */
    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Wait for the next event or command
                Runnable action = eventQueue.take();
                // Execute the action in the context of this bot's Virtual Thread
                action.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log and continue, preventing the bot from crashing due to a bad plugin/event
                logger.error("Error executing action in BotSession {}", sessionToken, e);
            }
        }
        cleanup();
    }

    /**
     * Enqueues an action to be executed on this bot's Virtual Thread.
     *
     * @param action The runnable to execute
     */
    public void execute(Runnable action) {
        eventQueue.offer(action);
    }

    /**
     * Stops the event loop and disconnects the bot.
     */
    public void disconnect() {
        running = false;
        if (virtualThread != null) {
            virtualThread.interrupt();
        }
        // Specific adapter implementation will handle actual network disconnect
        handleNetworkDisconnect();
    }

    /**
     * Called when the session event loop terminates.
     */
    protected abstract void cleanup();

    /**
     * Instructs the protocol adapter to sever the connection to the server.
     */
    protected abstract void handleNetworkDisconnect();

    @Override
    public UUID getUniqueId() {
        // TODO: Implement actual UUID retrieval from protocol
        return null;
    }

    @Override
    public String getUsername() {
        // TODO: Implement actual username retrieval
        return "Unknown";
    }

    @Override
    public CompletableFuture<Void> moveTo(double x, double y, double z) {
        // TODO: Implement movement packet handling
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Block getBlockAt(int x, int y, int z) {
        // TODO: Implement world data access
        return null;
    }

    @Override
    public void chat(String message) {
        // TODO: Implement chat packet sending
        logger.info("Chating: {}", message);
    }

    /**
     * @return true if the bot is connected to the server
     */
    @Override
    public boolean isConnected() {
        return running;
    }
}
