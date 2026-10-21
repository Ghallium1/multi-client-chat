package com.chatapp.server.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-safe set of the clients that completed the handshake, in join order.
 * Usernames are unique ignoring case.
 */
final class ClientRegistry {

    private final Map<String, ClientHandler> clients = new LinkedHashMap<>();

    /** Adds the client, or returns {@code false} if its username is already taken. */
    synchronized boolean register(ClientHandler client) {
        return clients.putIfAbsent(key(client.username()), client) == null;
    }

    /** Removes the client, returning {@code true} only if it was registered. */
    synchronized boolean unregister(ClientHandler client) {
        return client.username() != null && clients.remove(key(client.username()), client);
    }

    /** A copy that callers may iterate without holding the lock (e.g. while writing to sockets). */
    synchronized List<ClientHandler> snapshot() {
        return List.copyOf(clients.values());
    }

    synchronized List<String> usernames() {
        return clients.values().stream().map(ClientHandler::username).toList();
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
