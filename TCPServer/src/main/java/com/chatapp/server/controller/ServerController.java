package com.chatapp.server.controller;

import com.chatapp.common.config.NetworkConfig;
import com.chatapp.server.model.ChatServer;
import com.chatapp.server.model.LogType;
import com.chatapp.server.model.ServerListener;
import com.chatapp.server.view.ServerView;
import javafx.application.Platform;

import java.io.IOException;

/**
 * Connects the {@link ChatServer} model to the {@link ServerView}.
 *
 * <p>It listens to the model and moves each event onto the JavaFX thread with
 * {@link Platform#runLater}, because the model calls back from its own network threads. It also
 * turns the view's button clicks into model calls.
 */
public final class ServerController implements ServerListener {

    private final ServerView view;
    private final ChatServer server;

    public ServerController(ServerView view, NetworkConfig config) {
        this.view = view;
        this.server = new ChatServer(config, this);
        view.setEndpoint("Listening on " + config.host() + ":" + config.port());
        view.appendLog(LogType.SYSTEM, "Configuration loaded from " + config.source());
        view.setOnToggleServer(this::toggleServer);
    }

    public void startServer() {
        try {
            server.start();
            view.showRunning(true);
        } catch (IOException e) {
            view.appendLog(LogType.WARNING, "Could not start the server: " + e.getMessage());
            view.showRunning(false);
        }
    }

    public void stopServer() {
        server.stop();
        view.showRunning(false);
    }

    private void toggleServer() {
        if (server.isRunning()) {
            stopServer();
        } else {
            startServer();
        }
    }

    // ------------------------------------------------------------- ServerListener (network threads)

    @Override
    public void onLog(LogType type, String message) {
        Platform.runLater(() -> view.appendLog(type, message));
    }

    @Override
    public void onClientConnected(String username, boolean readOnly) {
        Platform.runLater(() -> view.addUser(username, readOnly));
    }

    @Override
    public void onClientDisconnected(String username) {
        Platform.runLater(() -> view.removeUser(username));
    }
}
