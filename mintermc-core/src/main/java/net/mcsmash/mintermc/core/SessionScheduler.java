package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.ScheduledTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs plugin tasks on their bot's event loop.
 * <br>
 * A single daemon thread tracks the clock; every due task is handed to the bot
 * session, so plugin callbacks never run concurrently with packet handling.
 * Tasks stop when their session ends.
 */
public final class SessionScheduler {

    /** One Minecraft tick in milliseconds. */
    public static final long TICK_MILLIS = 50;

    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "minter-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    private SessionScheduler() {
    }

    /**
     * Schedules a task on a session's event loop.
     *
     * @param session     the owning session
     * @param delayTicks  the delay before the first run
     * @param periodTicks the interval between runs, or 0 for a single run
     * @param task        the task to run
     * @return a handle that can cancel the task
     */
    public static ScheduledTask schedule(BotSession session, long delayTicks,
            long periodTicks, Runnable task) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Runnable guarded = () -> {
            if (cancelled.get()) {
                return;
            }
            if (!session.isRunning()) {
                ScheduledFuture<?> future = futureRef.get();
                if (future != null) {
                    future.cancel(false);
                }
                return;
            }
            session.execute(task);
        };

        long delay = Math.max(0, delayTicks) * TICK_MILLIS;
        ScheduledFuture<?> future = periodTicks > 0
                ? TIMER.scheduleAtFixedRate(guarded, delay, periodTicks * TICK_MILLIS,
                        TimeUnit.MILLISECONDS)
                : TIMER.schedule(guarded, delay, TimeUnit.MILLISECONDS);
        futureRef.set(future);

        return new ScheduledTask() {
            @Override
            public void cancel() {
                cancelled.set(true);
                future.cancel(false);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get() || future.isCancelled();
            }
        };
    }
}
