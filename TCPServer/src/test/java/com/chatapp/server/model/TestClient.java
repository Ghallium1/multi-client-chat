package com.chatapp.server.model;

import com.chatapp.common.protocol.Packet;
import com.chatapp.common.protocol.PacketType;
import com.chatapp.common.protocol.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A bare-bones socket client that speaks the protocol, used to drive the server in tests. */
final class TestClient implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    TestClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(3_000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    static TestClient join(int port, String username) throws IOException {
        TestClient client = new TestClient(port);
        client.send(Packet.of(PacketType.HELLO, username));
        return client;
    }

    void send(Packet packet) {
        out.println(Protocol.encode(packet));
    }

    void say(String text) {
        send(Packet.of(PacketType.MESSAGE, text));
    }

    /** Next packet, or {@code null} once the server has closed the connection. */
    Packet next() throws IOException {
        String line = in.readLine();
        return line == null ? null : Protocol.decode(line);
    }

    /** Skips join/leave notices and returns the next packet of any other type. */
    Packet nextIgnoringInfo() throws IOException {
        Packet packet;
        do {
            packet = next();
        } while (packet != null && packet.type() == PacketType.INFO);
        return packet;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
