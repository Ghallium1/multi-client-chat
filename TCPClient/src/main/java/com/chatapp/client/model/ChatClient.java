package com.chatapp.client.model;

import com.chatapp.common.protocol.ChatCommand;
import com.chatapp.common.protocol.ChatMessage;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.protocol.PacketType;
import com.chatapp.common.protocol.Protocol;
import com.chatapp.common.protocol.ProtocolException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One connection to the chat server.
 *
 * <p>Sending happens on the caller's thread. A background virtual thread reads everything the
 * server sends and reports it through a {@link ClientListener}. This class has no UI code, so the
 * same client works behind JavaFX, in a console, or in a unit test.
 *
 * <p>A {@code ChatClient} is used for a single connection; create a new one to reconnect.
 */
public final class ChatClient {

    public static final String READ_ONLY_WARNING =
            "READ-ONLY MODE: you cannot send messages. Commands still work: allUsers, end, bye.";

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    private final String host;
    private final int port;
    private final ClientListener listener;
    private final Object writeLock = new Object();
    private final AtomicBoolean connected = new AtomicBoolean();

    private volatile Socket socket;
    private volatile BufferedWriter out;
    private volatile String username;
    private volatile boolean readOnly;
    private volatile boolean leaving;
    private volatile boolean rejected;

    public ChatClient(String host, int port, ClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /**
     * Opens the connection and asks to join as {@code requestedUsername}. A blank name requests
     * READ-ONLY MODE. The answer arrives asynchronously as {@link ClientListener#onWelcome} or
     * {@link ClientListener#onRejected}.
     *
     * @throws IOException if the server cannot be reached
     */
    public void connect(String requestedUsername) throws IOException {
        if (socket != null) {
            throw new IllegalStateException("This client has already been used; create a new one");
        }
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            newSocket.close();
            throw e;
        }
        BufferedReader in = new BufferedReader(
                new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));
        socket = newSocket;
        connected.set(true);

        Thread.ofVirtual().name("server-listener").start(() -> readLoop(in));
        sendPacket(Packet.of(PacketType.HELLO, requestedUsername == null ? "" : requestedUsername.strip()));
    }

    /**
     * Sends what the user typed. {@code end} and {@code bye} disconnect, {@code allUsers} asks the
     * server for the user list, and anything else is a chat message (refused in READ-ONLY MODE).
     */
    public void send(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!isConnected()) {
            listener.onError("You are not connected to the server.");
            return;
        }
        Optional<ChatCommand> command = ChatCommand.parse(text);
        if (command.isPresent() && command.get().isDisconnect()) {
            disconnect();
            return;
        }
        if (command.isEmpty() && readOnly) {
            listener.onError(READ_ONLY_WARNING);
            return;
        }
        sendPacket(Packet.of(PacketType.MESSAGE, text.strip()));
    }

    /** Tells the server we are leaving, then closes the connection. Safe to call more than once. */
    public void disconnect() {
        if (!isConnected() || leaving) {
            return;
        }
        leaving = true;
        sendPacket(Packet.of(PacketType.BYE));
        closeSocket();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** The name the server assigned, available once {@link ClientListener#onWelcome} has fired. */
    public String getUsername() {
        return username;
    }

    public String getServerAddress() {
        return host + ":" + port;
    }

    // ------------------------------------------------------------------------- listener thread

    private void readLoop(BufferedReader in) {
        String reason = "The server closed the connection.";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    dispatch(Protocol.decode(line));
                } catch (ProtocolException e) {
                    listener.onError("Ignored an invalid packet from the server: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            reason = "Lost the connection to the server.";
        } finally {
            closeSocket();
            if (connected.compareAndSet(true, false) && !rejected) {
                listener.onDisconnected(leaving ? "You left the chat." : reason);
            }
        }
    }

    private void dispatch(Packet packet) {
        switch (packet.type()) {
            case WELCOME -> {
                username = packet.field(0);
                readOnly = Boolean.parseBoolean(packet.field(1));
                listener.onWelcome(username, readOnly);
            }
            case REJECTED -> {
                rejected = true;
                listener.onRejected(packet.field(0));
            }
            case CHAT -> listener.onChatMessage(ChatMessage.fromPacket(packet));
            case INFO -> listener.onInfo(packet.field(0), packet.field(1));
            case USERS -> listener.onUserList(packet.fields());
            case ERROR -> listener.onError(packet.field(0));
            default -> listener.onError("Unexpected packet from the server: " + packet.type());
        }
    }

    // --------------------------------------------------------------------------------- helpers

    private void sendPacket(Packet packet) {
        String line = Protocol.encode(packet);
        synchronized (writeLock) {
            try {
                out.write(line);
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                // The listener thread notices the broken connection and reports it.
                closeSocket();
            }
        }
    }

    private void closeSocket() {
        Socket current = socket;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
