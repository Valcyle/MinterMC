package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.ScheduledTask;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.event.BotListener;
import net.mcsmash.mintermc.api.event.ChatMessageEvent;
import net.mcsmash.mintermc.api.event.HealthChangeEvent;
import net.mcsmash.mintermc.api.math.Vector3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotSessionTest {

    private final List<BotSession> sessions = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopSessions() {
        sessions.forEach(BotSession::disconnect);
        sessions.clear();
    }

    private BotSession newSession(String name) {
        BotSession session = new TestSession("token-" + name);
        session.setUsername(name);
        session.setCommandRegistry(new CommandRegistry());
        session.startEventLoop();
        sessions.add(session);
        return session;
    }

    @Test
    void deliversChatToPluginListeners() throws Exception {
        BotSession session = newSession("Listener");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatMessageEvent> received = new AtomicReference<>();
        session.subscribe(new BotListener() {
            @Override
            public void onChat(ChatMessageEvent event) {
                received.set(event);
                latch.countDown();
            }
        });

        session.notifyChatMessage("Alice", "hello");

        assertTrue(latch.await(2, TimeUnit.SECONDS), "chat event did not arrive");
        assertEquals("Alice", received.get().sender());
        assertEquals("hello", received.get().content());
        assertEquals(session, received.get().bot());
    }

    @Test
    void dispatchesCommandsFromChat() throws Exception {
        BotSession session = newSession("Commander");
        CommandRegistry registry = new CommandRegistry();
        session.setCommandRegistry(registry);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> args = new AtomicReference<>();
        registry.register("goto", (bot, command, arguments) -> {
            args.set(arguments);
            latch.countDown();
        });

        session.notifyChatMessage("Alice", ".goto 10 64 10");

        assertTrue(latch.await(2, TimeUnit.SECONDS), "command did not run");
        assertEquals(List.of("10", "64", "10"), args.get());
    }

    @Test
    void ignoresCommandsEchoedFromTheBotItself() throws Exception {
        BotSession session = newSession("Echo");
        CommandRegistry registry = new CommandRegistry();
        session.setCommandRegistry(registry);
        CountDownLatch latch = new CountDownLatch(1);
        registry.register("ping", (bot, command, arguments) -> latch.countDown());

        session.notifyChatMessage("Echo", ".ping");

        assertFalse(latch.await(300, TimeUnit.MILLISECONDS), "own echo must not run a command");
    }

    @Test
    void runsScheduledTasksOnTheSessionLoop() throws Exception {
        BotSession session = newSession("Ticker");
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<ScheduledTask> task = new AtomicReference<>();
        task.set(session.every(1, () -> {
            latch.countDown();
            if (latch.getCount() == 0) {
                task.get().cancel();
            }
        }));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "scheduled task did not run twice");
    }

    @Test
    void reportsHealthChanges() throws Exception {
        BotSession session = newSession("Healthy");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthChangeEvent> received = new AtomicReference<>();
        session.subscribe(new BotListener() {
            @Override
            public void onHealthChange(HealthChangeEvent event) {
                received.set(event);
                latch.countDown();
            }
        });

        session.setHealth(12.0f);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "health event did not arrive");
        assertEquals(12.0f, received.get().health());
        assertEquals(12.0f, session.getHealth());
    }

    /** Minimal session used to exercise the event loop. */
    private static final class TestSession extends BotSession {

        private TestSession(String token) {
            super(token);
        }

        @Override
        public CompletableFuture<Void> moveTo(Vector3 position) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Block getBlockAt(Vector3 position) {
            return null;
        }

        @Override
        public void chat(String message) {
        }

        @Override
        protected void cleanup() {
        }

        @Override
        protected void handleNetworkDisconnect() {
        }
    }
}
