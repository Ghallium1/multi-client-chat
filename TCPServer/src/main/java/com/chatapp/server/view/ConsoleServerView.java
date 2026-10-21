package com.chatapp.server.view;

import com.chatapp.server.model.LogType;
import com.chatapp.server.model.ServerListener;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A second, text-only view of the same server, used with {@code --nogui}.
 *
 * <p>It exists to show the separation of concerns: the {@code ChatServer} model runs unchanged
 * whether a JavaFX window or this console is listening.
 */
public final class ConsoleServerView implements ServerListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onLog(LogType type, String message) {
        System.out.printf("%s  %-10s %s%n", LocalTime.now().format(TIME), type, message);
    }

    @Override
    public void onClientConnected(String username, boolean readOnly) {
        // The log line "Welcome <user>" already announces it.
    }

    @Override
    public void onClientDisconnected(String username) {
        // The log line "<user> left the chat" already announces it.
    }
}
