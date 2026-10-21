package com.chatapp.server;

import com.chatapp.common.config.NetworkConfig;
import com.chatapp.server.model.ChatServer;
import com.chatapp.server.view.ConsoleServerView;
import javafx.application.Application;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Launcher for the chat server.
 *
 * <pre>
 *   java -jar TCPServer.jar            JavaFX window
 *   java -jar TCPServer.jar --nogui    same server model, console output only
 * </pre>
 *
 * The IP address and port come from {@code server.properties} (see {@link NetworkConfig}).
 *
 * <p>This class deliberately does not extend {@link Application}: a plain {@code main} lets the
 * shaded JAR start JavaFX from the classpath.
 */
public final class TCPServer {

    static final String CONFIG_FILE = "server.properties";

    private TCPServer() {
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--nogui")) {
            runHeadless();
        } else {
            Application.launch(ServerApp.class, args);
        }
    }

    private static void runHeadless() throws InterruptedException {
        NetworkConfig config = NetworkConfig.load(CONFIG_FILE);
        ChatServer server = new ChatServer(config, new ConsoleServerView());
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Could not start the server: " + e.getMessage());
            System.exit(1);
        }
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            shutdown.countDown();
        }));
        // Virtual threads do not keep the JVM alive, so the main thread waits for Ctrl+C.
        shutdown.await();
    }
}
