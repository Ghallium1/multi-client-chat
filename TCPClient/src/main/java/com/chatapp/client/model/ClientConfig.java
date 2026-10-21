package com.chatapp.client.model;

import com.chatapp.common.config.NetworkConfig;

/**
 * Works out which server to connect to.
 *
 * <ul>
 *   <li>{@code TCPClient <ServerIPAddress> <PortNumber>}: the command-line arguments win.</li>
 *   <li>{@code TCPClient} with no arguments: falls back to {@code client.properties}.</li>
 * </ul>
 */
public final class ClientConfig {

    public static final String CONFIG_FILE = "client.properties";
    public static final String USAGE = "Usage: java -jar TCPClient.jar <ServerIPAddress> <PortNumber>"
            + System.lineSeparator() + "  e.g. java -jar TCPClient.jar localhost 3000";

    private ClientConfig() {
    }

    public static NetworkConfig resolve(String... args) {
        return switch (args.length) {
            case 0 -> NetworkConfig.load(CONFIG_FILE);
            case 2 -> new NetworkConfig(args[0], NetworkConfig.parsePort(args[1]), "command line");
            default -> throw new IllegalArgumentException(
                    "Expected 2 arguments (server IP address and port) but got " + args.length);
        };
    }
}
