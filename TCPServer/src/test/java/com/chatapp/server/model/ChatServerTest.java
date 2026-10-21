package com.chatapp.server.model;

import com.chatapp.common.config.NetworkConfig;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.protocol.PacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real server model on a random free port, with no user interface at all. */
class ChatServerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:15:00Z"), ZoneOffset.UTC);

    private final RecordingListener listener = new RecordingListener();
    private ChatServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer(new NetworkConfig("127.0.0.1", 0, "test"), listener, FIXED_CLOCK);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void welcomesNamedUser() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice")) {
            assertEquals(Packet.of(PacketType.WELCOME, "alice", "false"), alice.next());
            assertEquals(Packet.of(PacketType.INFO, "10:15", "alice joined the chat"), alice.next());

            awaitTrue(() -> listener.connected.contains("alice"));
            assertTrue(listener.logs.contains("Welcome alice"));
            assertTrue(listener.logs.contains("Waiting for client..."));
            assertEquals(List.of("alice"), server.activeUsers());
        }
    }

    @Test
    void emptyUsernameJoinsInReadOnlyModeAsGuest() throws IOException {
        try (TestClient guest = TestClient.join(port, "")) {
            assertEquals(Packet.of(PacketType.WELCOME, "Guest-1", "true"), guest.next());
        }
    }

    @Test
    void distributesFormattedMessageToEveryClient() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice");
             TestClient bob = TestClient.join(port, "bob")) {
            alice.nextIgnoringInfo();
            bob.nextIgnoringInfo();
            awaitTrue(() -> server.activeUsers().size() == 2);

            alice.say("Hello\nworld");

            Packet expected = Packet.of(PacketType.CHAT, "10:15", "alice", "Hello\nworld");
            assertEquals(expected, bob.nextIgnoringInfo());
            assertEquals(expected, alice.nextIgnoringInfo());
            assertTrue(listener.logs.contains("[10:15] alice: Hello\nworld"));
        }
    }

    @Test
    void allUsersRepliesOnlyToTheClientWhoAsked() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice");
             TestClient bob = TestClient.join(port, "bob")) {
            alice.nextIgnoringInfo();
            bob.nextIgnoringInfo();
            awaitTrue(() -> server.activeUsers().size() == 2);

            alice.say("allUsers");
            assertEquals(new Packet(PacketType.USERS, List.of("alice", "bob")), alice.nextIgnoringInfo());

            // Bob must not have received the list: the next thing he sees is Alice's chat message.
            alice.say("ping");
            assertEquals(PacketType.CHAT, bob.nextIgnoringInfo().type());
        }
    }

    @Test
    void readOnlyUserCannotSendButCanListUsers() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice");
             TestClient guest = TestClient.join(port, "")) {
            alice.nextIgnoringInfo();
            guest.nextIgnoringInfo();
            awaitTrue(() -> server.activeUsers().size() == 2);

            guest.say("let me in!");
            assertEquals(Packet.of(PacketType.ERROR, ChatServer.READ_ONLY_REFUSAL), guest.nextIgnoringInfo());

            guest.say("allUsers");
            assertEquals(new Packet(PacketType.USERS, List.of("alice", "Guest-1")), guest.nextIgnoringInfo());

            // The guest's text never reached Alice.
            alice.say("only me");
            assertEquals("only me", alice.nextIgnoringInfo().field(2));
        }
    }

    @Test
    void refusesDuplicateUsernameIgnoringCase() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice");
             TestClient impostor = TestClient.join(port, "ALICE")) {
            alice.next();
            assertEquals(PacketType.REJECTED, impostor.next().type());
            assertNull(impostor.next(), "the server closes a rejected connection");
            assertEquals(List.of("alice"), server.activeUsers());
        }
    }

    @Test
    void refusesInvalidUsername() throws IOException {
        try (TestClient client = TestClient.join(port, "has spaces")) {
            assertEquals(PacketType.REJECTED, client.next().type());
        }
    }

    @Test
    void byeCommandDisconnectsAndNotifiesOthers() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice");
             TestClient bob = TestClient.join(port, "bob")) {
            alice.nextIgnoringInfo();
            bob.nextIgnoringInfo();
            awaitTrue(() -> server.activeUsers().size() == 2);
            // Drain "bob joined" so the next INFO bob reads is about alice leaving.
            awaitTrue(() -> listener.connected.contains("bob"));

            alice.say("bye");

            // Alice may still have join notices buffered; after them the server closes her socket.
            assertNull(alice.nextIgnoringInfo(), "alice's connection is closed");
            Packet notice;
            do {
                notice = bob.next();
            } while (!notice.field(1).contains("left"));
            assertEquals(Packet.of(PacketType.INFO, "10:15", "alice left the chat"), notice);
            awaitTrue(() -> listener.disconnected.contains("alice"));
            assertEquals(List.of("bob"), server.activeUsers());
        }
    }

    @Test
    void clientThatDropsWithoutByeIsCleanedUp() throws IOException {
        TestClient alice = TestClient.join(port, "alice");
        alice.next();
        alice.close();

        awaitTrue(() -> listener.disconnected.contains("alice"));
        assertTrue(server.activeUsers().isEmpty());
    }

    @Test
    void servesManyConcurrentClients() throws Exception {
        int count = 200;
        List<TestClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                TestClient client = TestClient.join(port, "user" + i);
                clients.add(client);
                assertEquals(PacketType.WELCOME, client.next().type());
            }
            awaitTrue(() -> server.activeUsers().size() == count);

            clients.get(0).say("hello everyone");

            for (TestClient client : clients) {
                assertEquals("hello everyone", client.nextIgnoringInfo().field(2));
            }
        } finally {
            for (TestClient client : clients) {
                client.close();
            }
        }
    }

    @Test
    void stopDisconnectsEveryoneAndCanRestart() throws IOException {
        try (TestClient alice = TestClient.join(port, "alice")) {
            alice.next();
            awaitTrue(() -> server.activeUsers().size() == 1);

            server.stop();

            assertFalse(server.isRunning());
            Packet packet;
            do {
                packet = alice.next();
            } while (packet != null);
            assertTrue(listener.logs.contains("Server stopped"));

            server.start();
            assertTrue(server.isRunning());
        }
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

    private static final class RecordingListener implements ServerListener {
        final List<String> logs = new CopyOnWriteArrayList<>();
        final List<String> connected = new CopyOnWriteArrayList<>();
        final List<String> disconnected = new CopyOnWriteArrayList<>();

        @Override
        public void onLog(LogType type, String message) {
            logs.add(message);
        }

        @Override
        public void onClientConnected(String username, boolean readOnly) {
            connected.add(username);
        }

        @Override
        public void onClientDisconnected(String username) {
            disconnected.add(username);
        }
    }
}
