package com.chatapp.server;

import com.chatapp.common.config.NetworkConfig;
import com.chatapp.server.controller.ServerController;
import com.chatapp.server.view.ServerView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.Objects;

/** JavaFX entry point of the server: wires model, view and controller together and shows the window. */
public final class ServerApp extends Application {

    private ServerController controller;

    @Override
    public void start(Stage stage) {
        NetworkConfig config;
        try {
            config = NetworkConfig.load(TCPServer.CONFIG_FILE);
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, "Invalid configuration: " + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        ServerView view = new ServerView();
        controller = new ServerController(view, config);

        Scene scene = new Scene(view.getRoot(), 900, 580);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/server.css")).toExternalForm());
        stage.setTitle("Chat Server — port " + config.port());
        stage.setMinWidth(680);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();

        controller.startServer();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.stopServer();
        }
    }
}
