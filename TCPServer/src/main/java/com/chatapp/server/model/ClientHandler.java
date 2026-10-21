package com.chatapp.server.model;

import com.chatapp.common.protocol.Packet;
import com.chatapp.common.protocol.PacketType;
import com.chatapp.common.protocol.Protocol;
import com.chatapp.common.protocol.ProtocolException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Serves one connected client on its own virtual thread: performs the handshake, then reads
 * packets until the client leaves or the connection drops.
 *
 * <p>Reading happens only on this handler's thread, but {@link #send} is called from any thread
 * that broadcasts, so writes are serialized with a lock.
 */
final class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final Object writeLock = new Object();

    private volatile String username;
    private volatile boolean readOnly;

    ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            if (handshake()) {
                readLoop();
            }
        } catch (IOException e) {
            // The client vanished without saying BYE (closed window, network loss). Clean up below.
        } finally {
            close();
            server.clientFinished(this);
        }
    }

    private boolean handshake() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return false;
        }
        Packet hello;
        try {
            hello = Protocol.decode(line);
        } catch (ProtocolException e) {
            send(Packet.of(PacketType.REJECTED, e.getMessage()));
            return false;
        }
        if (hello.type() != PacketType.HELLO) {
            send(Packet.of(PacketType.REJECTED, "Expected HELLO but received " + hello.type()));
            return false;
        }
        return server.join(this, hello.field(0));
    }

    private void readLoop() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            Packet packet;
            try {
                packet = Protocol.decode(line);
            } catch (ProtocolException e) {
                send(Packet.of(PacketType.ERROR, e.getMessage()));
                continue;
            }
            switch (packet.type()) {
                case MESSAGE -> {
                    if (!server.handleMessage(this, packet.field(0))) {
                        return;
                    }
                }
                case BYE -> {
                    return;
                }
                default -> send(Packet.of(PacketType.ERROR, "Unexpected packet: " + packet.type()));
            }
        }
    }

    void send(Packet packet) {
        String line = Protocol.encode(packet);
        synchronized (writeLock) {
            try {
                out.write(line);
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                close();
            }
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    void assignIdentity(String username, boolean readOnly) {
        this.username = username;
        this.readOnly = readOnly;
    }

    String username() {
        return username;
    }

    boolean isReadOnly() {
        return readOnly;
    }

    String remoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }
}
