package com.chatapp.client.model;

import com.chatapp.common.config.NetworkConfig;
import com.chatapp.common.protocol.ChatMessage;
import com.chatapp.server.model.ChatServer;
import com.chatapp.server.model.LogType;
import com.chatapp.server.model.ServerListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test: real {@link ChatClient} models talk to a real {@link ChatServer} over TCP.
 * No JavaFX is involved, which shows the model works without the view.
 */
class ChatClientTest {

    private final List<String> serverLogs = new CopyOnWriteArrayList<>();
    private ChatServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer(new NetworkConfig("127.0.0.1", 0, "test"), new ServerListener() {
            @Override
            public void onLog(LogType type, String message) {
                serverLogs.add(message);
            }

            @Override
            public void onClientConnected(String username, boolean readOnly) {
            }

            @Override
            public void onClientDisconnected(String username) {
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void joinsWithUsername() throws Exception {
        Events alice = new Events();
        ChatClient client = connect("alice", alice);

        assertEquals("welcome alice readOnly=false", alice.next());
        assertEquals("alice", client.getUsername());
        assertFalse(client.isReadOnly());
        client.disconnect();
    }

    @Test
    void twoClientsExchangeMessages() throws Exception {
        Events aliceEvents = new Events();
        Events bobEvents = new Events();
        ChatClient alice = connect("alice", aliceEvents);
        aliceEvents.expect("welcome alice readOnly=false");
        ChatClient bob = connect("bob", bobEvents);
        bobEvents.expect("welcome bob readOnly=false");

        alice.send("Hi Bob!");

        assertEquals("chat alice: Hi Bob!", bobEvents.expectStartingWith("chat"));
        assertEquals("chat alice: Hi Bob!", aliceEvents.expectStartingWith("chat"), "the sender sees its own message");

        bob.send("allUsers");
        assertEquals("users [alice, bob]", bobEvents.expectStartingWith("users"));

        alice.disconnect();
        bob.disconnect();
    }

    @Test
    void readOnlyClientIsBlockedLocallyButCanUseCommands() throws Exception {
        Events events = new Events();
        ChatClient guest = connect("", events);
        assertEquals("welcome Guest-1 readOnly=true", events.next());
        assertTrue(guest.isReadOnly());

        guest.send("hello?");
        assertEquals("error " + ChatClient.READ_ONLY_WARNING, events.expectStartingWith("error"));
        assertTrue(serverLogs.stream().noneMatch(log -> log.contains("hello?")),
                "the message never left the client");

        guest.send("allUsers");
        assertEquals("users [Guest-1]", events.expectStartingWith("users"));
        guest.disconnect();
    }

    @Test
    void byeCommandDisconnectsAndTellsTheServer() throws Exception {
        Events events = new Events();
        ChatClient client = connect("alice", events);
        events.expect("welcome alice readOnly=false");

        client.send("bye");

        assertEquals("disconnected You left the chat.", events.expectStartingWith("disconnected"));
        assertFalse(client.isConnected());
        awaitTrue(() -> server.activeUsers().isEmpty());
    }

    @Test
    void endCommandAlsoDisconnects() throws Exception {
        Events events = new Events();
        ChatClient client = connect("alice", events);
        events.expect("welcome alice readOnly=false");

        client.send("END");

        assertEquals("disconnected You left the chat.", events.expectStartingWith("disconnected"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        Events first = new Events();
        ChatClient alice = connect("alice", first);
        first.expect("welcome alice readOnly=false");

        Events second = new Events();
        connect("alice", second);
        assertEquals("rejected The username 'alice' is already taken.", second.next());
        alice.disconnect();
    }

    @Test
    void serverShutdownIsReported() throws Exception {
        Events events = new Events();
        ChatClient client = connect("alice", events);
        events.expect("welcome alice readOnly=false");

        server.stop();

        assertEquals("info The server is shutting down. Goodbye!", events.expectStartingWith("info The server"));
        events.expectStartingWith("disconnected");
        assertFalse(client.isConnected());
    }

    @Test
    void unreachableServerThrows() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        ChatClient client = new ChatClient("127.0.0.1", closedPort, new Events());
        assertThrows(IOException.class, () -> client.connect("alice"));
    }

    private ChatClient connect(String username, Events events) throws IOException {
        ChatClient client = new ChatClient("127.0.0.1", server.getPort(), events);
        client.connect(username);
        return client;
    }

    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 3 seconds");
            }
            Thread.onSpinWait();
        }
    }

    /** Records listener callbacks as short strings, in arrival order. */
    private static final class Events implements ClientListener {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        String next() throws InterruptedException {
            String event = queue.poll(3, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("No event within 3 seconds");
            }
            return event;
        }

        void expect(String expected) throws InterruptedException {
            assertEquals(expected, next());
        }

        /** Skips unrelated events (e.g. join notices) until one starts with {@code prefix}. */
        String expectStartingWith(String prefix) throws InterruptedException {
            String event;
            do {
                event = next();
            } while (!event.startsWith(prefix));
            return event;
        }

        @Override
        public void onWelcome(String username, boolean readOnly) {
            queue.add("welcome " + username + " readOnly=" + readOnly);
        }

        @Override
        public void onRejected(String reason) {
            queue.add("rejected " + reason);
        }

        @Override
        public void onChatMessage(ChatMessage message) {
            queue.add("chat " + message.sender() + ": " + message.text());
        }

        @Override
        public void onInfo(String time, String text) {
            queue.add("info " + text);
        }

        @Override
        public void onUserList(List<String> users) {
            queue.add("users " + users);
        }

        @Override
        public void onError(String message) {
            queue.add("error " + message);
        }

        @Override
        public void onDisconnected(String reason) {
            queue.add("disconnected " + reason);
        }
    }
}
