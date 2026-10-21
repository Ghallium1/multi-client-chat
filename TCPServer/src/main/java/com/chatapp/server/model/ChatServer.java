package com.chatapp.server.model;

import com.chatapp.common.config.NetworkConfig;
import com.chatapp.common.protocol.ChatCommand;
import com.chatapp.common.protocol.ChatMessage;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.protocol.PacketType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The central chat server. It accepts TCP connections, gives each client its own virtual thread,
 * and relays every chat message to all connected clients.
 *
 * <p>This class knows nothing about JavaFX. Everything it wants to report goes through a
 * {@link ServerListener}, so the same server runs behind the GUI, in a console, or in a unit test.
 */
public final class ChatServer {

    static final Pattern VALID_USERNAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,20}");
    static final String READ_ONLY_REFUSAL =
            "You are in READ-ONLY MODE: reconnect with a username to send messages.";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final NetworkConfig config;
    private final ServerListener listener;
    private final Clock clock;
    private final ClientRegistry registry = new ClientRegistry();
    private final AtomicInteger guestCounter = new AtomicInteger();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService clientThreads;
    private volatile boolean stopping;

    public ChatServer(NetworkConfig config, ServerListener listener) {
        this(config, listener, Clock.systemDefaultZone());
    }

    ChatServer(NetworkConfig config, ServerListener listener, Clock clock) {
        this.config = config;
        this.listener = listener;
        this.clock = clock;
    }

    /** Binds the configured address and starts accepting clients in the background. */
    public synchronized void start() throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("Server is already running");
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        try {
            socket.bind(new InetSocketAddress(config.host(), config.port()));
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        stopping = false;
        serverSocket = socket;
        // One cheap virtual thread per client, named client-1, client-2, ... for easy debugging.
        clientThreads = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("client-", 1).factory());

        listener.onLog(LogType.SYSTEM, "Server started on " + config.host() + ":" + socket.getLocalPort());
        Thread.ofVirtual().name("accept-loop").start(() -> acceptLoop(socket));
    }

    /** Tells every client goodbye, closes all connections and stops listening. */
    public synchronized void stop() {
        if (!isRunning()) {
            return;
        }
        broadcast(Packet.of(PacketType.INFO, now(), "The server is shutting down. Goodbye!"));
        stopping = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Closing is best effort.
        }
        registry.snapshot().forEach(ClientHandler::close);
        clientThreads.shutdown();
        try {
            clientThreads.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.onLog(LogType.SYSTEM, "Server stopped");
    }

    public boolean isRunning() {
        ServerSocket socket = serverSocket;
        return socket != null && !socket.isClosed();
    }

    /** The port actually bound (useful when the configuration asks for port 0). */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public List<String> activeUsers() {
        return registry.usernames();
    }

    private void acceptLoop(ServerSocket socket) {
        while (!socket.isClosed()) {
            listener.onLog(LogType.SYSTEM, "Waiting for client...");
            Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    listener.onLog(LogType.WARNING, "Could not accept connection: " + e.getMessage());
                }
                continue;
            }
            listener.onLog(LogType.CONNECTION, "Client connected from " + client.getRemoteSocketAddress());
            try {
                clientThreads.execute(new ClientHandler(client, this));
            } catch (IOException | RejectedExecutionException e) {
                closeQuietly(client);
            }
        }
    }

    /**
     * Completes the handshake for {@code client}. Returns {@code false} if it was refused,
     * in which case the handler closes the connection.
     */
    boolean join(ClientHandler client, String requestedName) {
        String name = requestedName == null ? "" : requestedName.strip();
        boolean readOnly = name.isEmpty();
        if (readOnly) {
            name = "Guest-" + guestCounter.incrementAndGet();
        } else if (!VALID_USERNAME.matcher(name).matches()) {
            return reject(client, "Usernames are 1-20 letters, digits, '.', '_' or '-' (no spaces).");
        }

        client.assignIdentity(name, readOnly);
        if (!registry.register(client)) {
            return reject(client, "The username '" + name + "' is already taken.");
        }

        client.send(Packet.of(PacketType.WELCOME, name, String.valueOf(readOnly)));
        listener.onLog(LogType.CONNECTION, "Welcome " + name + (readOnly ? " (READ-ONLY MODE)" : ""));
        listener.onClientConnected(name, readOnly);
        broadcast(Packet.of(PacketType.INFO, now(),
                name + (readOnly ? " joined in read-only mode" : " joined the chat")));
        return true;
    }

    private boolean reject(ClientHandler client, String reason) {
        client.send(Packet.of(PacketType.REJECTED, reason));
        listener.onLog(LogType.WARNING, "Refused " + client.remoteAddress() + ": " + reason);
        return false;
    }

    /**
     * Handles one line typed by {@code sender}. Returns {@code false} when the sender asked to leave.
     */
    boolean handleMessage(ClientHandler sender, String text) {
        Optional<ChatCommand> command = ChatCommand.parse(text);
        if (command.isPresent()) {
            if (command.get().isDisconnect()) {
                return false;
            }
            List<String> users = registry.usernames();
            sender.send(new Packet(PacketType.USERS, users));
            listener.onLog(LogType.CONNECTION,
                    sender.username() + " requested the active users (" + users.size() + " online)");
            return true;
        }

        if (text.isBlank()) {
            return true;
        }
        if (sender.isReadOnly()) {
            sender.send(Packet.of(PacketType.ERROR, READ_ONLY_REFUSAL));
            listener.onLog(LogType.WARNING, "Blocked a message from read-only user " + sender.username());
            return true;
        }

        ChatMessage message = new ChatMessage(now(), sender.username(), text.strip());
        listener.onLog(LogType.MESSAGE, message.format());
        broadcast(message.toPacket());
        return true;
    }

    /** Called by a handler's thread when its connection is over, however it ended. */
    void clientFinished(ClientHandler client) {
        if (!registry.unregister(client)) {
            return;
        }
        listener.onClientDisconnected(client.username());
        listener.onLog(LogType.CONNECTION, client.username() + " left the chat");
        if (!stopping) {
            broadcast(Packet.of(PacketType.INFO, now(), client.username() + " left the chat"));
        }
    }

    /** Sends {@code packet} to every connected client, including the sender. */
    private void broadcast(Packet packet) {
        for (ClientHandler client : registry.snapshot()) {
            client.send(packet);
        }
    }

    private String now() {
        return LocalTime.now(clock).format(TIME_FORMAT);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing else to do.
        }
    }
}
