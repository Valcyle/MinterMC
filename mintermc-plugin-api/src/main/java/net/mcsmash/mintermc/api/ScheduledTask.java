package net.mcsmash.mintermc.api;

/**
 * A task scheduled on a bot's event loop.
 */
public interface ScheduledTask {

    /**
     * Cancels the task. Cancelling an already finished task has no effect.
     */
    void cancel();

    /**
     * @return true when the task will not run again
     */
    boolean isCancelled();
}
