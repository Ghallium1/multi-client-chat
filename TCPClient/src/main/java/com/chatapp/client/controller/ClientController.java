package com.chatapp.client.controller;

import com.chatapp.client.model.ChatClient;
import com.chatapp.client.model.ClientListener;
import com.chatapp.client.view.ChatEntry;
import com.chatapp.client.view.ChatView;
import com.chatapp.client.view.LoginView;
import com.chatapp.common.config.NetworkConfig;
import com.chatapp.common.protocol.ChatMessage;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Connects the {@link ChatClient} model to the {@link LoginView} and {@link ChatView}.
 *
 * <p>It turns user actions into model calls and model events into screen updates. Model events
 * arrive on the network thread, so each one is passed to the JavaFX thread with
 * {@link Platform#runLater}.
 */
public final class ClientController implements ClientListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Stage stage;
    private final NetworkConfig config;
    private final LoginView loginView = new LoginView();
    private final ChatView chatView = new ChatView();
    private final Scene scene;

    private ChatClient client;
    private boolean inChat;

    public ClientController(Stage stage, NetworkConfig config) {
        this.stage = stage;
        this.config = config;

        scene = new Scene(loginView.getRoot(), 560, 680);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/client.css")).toExternalForm());

        loginView.setServerAddress(config.host() + ":" + config.port());
        loginView.setOnJoin(this::join);
        chatView.setOnSend(this::send);
        chatView.setOnLeave(this::leave);
        stage.setOnCloseRequest(event -> shutdown());
    }

    public void show() {
        stage.setTitle("Group Chat");
        stage.setMinWidth(420);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
        loginView.focus();
    }

    /** Leaves the chat politely (sends BYE) if still connected. */
    public void shutdown() {
        if (client != null) {
            client.disconnect();
        }
    }

    // ----------------------------------------------------------------------- user actions (FX)

    private void join(String username) {
        loginView.setBusy(true);
        client = new ChatClient(config.host(), config.port(), this);
        ChatClient connecting = client;
        // Connecting can block for a few seconds, so keep it off the JavaFX thread.
        Thread.ofVirtual().name("connect").start(() -> {
            try {
                connecting.connect(username);
            } catch (IOException e) {
                Platform.runLater(() -> {
                    loginView.setBusy(false);
                    loginView.showError("Cannot reach the server at " + connecting.getServerAddress()
                            + ". Is TCPServer running?");
                });
            }
        });
    }

    private void send(String text) {
        client.send(text);
        chatView.clearInput();
    }

    private void leave() {
        if (client != null && client.isConnected()) {
            client.disconnect();
        } else {
            stage.close();
        }
    }

    // ------------------------------------------------------ ClientListener (network thread)

    @Override
    public void onWelcome(String username, boolean readOnly) {
        Platform.runLater(() -> {
            inChat = true;
            loginView.setBusy(false);
            chatView.setUser(username, client.getServerAddress());
            chatView.setReadOnly(readOnly);
            chatView.setOnline(true);
            chatView.addEntry(ChatEntry.system(now(), readOnly
                    ? "Connected as " + username + " in READ-ONLY MODE"
                    : "Connected as " + username));
            stage.setTitle("Group Chat — " + username);
            scene.setRoot(chatView.getRoot());
            chatView.focusInput();
        });
    }

    @Override
    public void onRejected(String reason) {
        Platform.runLater(() -> {
            loginView.setBusy(false);
            loginView.showError(reason);
        });
    }

    @Override
    public void onChatMessage(ChatMessage message) {
        Platform.runLater(() -> chatView.addEntry(message.sender().equals(client.getUsername())
                ? ChatEntry.own(message.time(), message.text())
                : ChatEntry.other(message.time(), message.sender(), message.text())));
    }

    @Override
    public void onInfo(String time, String text) {
        Platform.runLater(() -> chatView.addEntry(ChatEntry.system(time, text)));
    }

    @Override
    public void onUserList(List<String> users) {
        Platform.runLater(() -> chatView.addEntry(ChatEntry.users(now(), users)));
    }

    @Override
    public void onError(String message) {
        Platform.runLater(() -> {
            if (inChat) {
                chatView.addEntry(ChatEntry.error(now(), message));
            } else {
                loginView.showError(message);
            }
        });
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            if (inChat) {
                chatView.setOnline(false);
                chatView.addEntry(ChatEntry.system(now(), reason + " Press EXIT to close the window."));
            } else {
                loginView.setBusy(false);
                loginView.showError(reason);
            }
        });
    }

    private static String now() {
        return LocalTime.now().format(TIME);
    }
}
