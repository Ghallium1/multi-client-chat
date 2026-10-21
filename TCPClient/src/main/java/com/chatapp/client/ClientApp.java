package com.chatapp.client;

import com.chatapp.client.controller.ClientController;
import com.chatapp.client.model.ClientConfig;
import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX entry point of the client: creates the controller, which builds the views and the model. */
public final class ClientApp extends Application {

    private ClientController controller;

    @Override
    public void start(Stage stage) {
        // Arguments were already validated by TCPClient.main, so this cannot fail here.
        var config = ClientConfig.resolve(getParameters().getRaw().toArray(String[]::new));
        controller = new ClientController(stage, config);
        controller.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }
}
