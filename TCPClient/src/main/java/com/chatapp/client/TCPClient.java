package com.chatapp.client;

import com.chatapp.client.model.ClientConfig;
import javafx.application.Application;

/**
 * Launcher for the chat client.
 *
 * <pre>
 *   java -jar TCPClient.jar &lt;ServerIPAddress&gt; &lt;PortNumber&gt;     e.g.  java -jar TCPClient.jar localhost 3000
 *   java -jar TCPClient.jar                                  uses client.properties
 * </pre>
 *
 * <p>This class deliberately does not extend {@link Application}: a plain {@code main} lets the
 * shaded JAR start JavaFX from the classpath.
 */
public final class TCPClient {

    private TCPClient() {
    }

    public static void main(String[] args) {
        try {
            var config = ClientConfig.resolve(args);
            System.out.println("Connecting to " + config.host() + ":" + config.port() + " (" + config.source() + ")");
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(ClientConfig.USAGE);
            System.exit(1);
        }
        Application.launch(ClientApp.class, args);
    }
}
