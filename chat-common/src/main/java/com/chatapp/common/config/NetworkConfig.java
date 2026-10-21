package com.chatapp.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server IP address and port, read at runtime from a {@code .properties} file so the
 * deployment can change without recompiling.
 *
 * <p>Lookup order (later sources override earlier ones):
 * <ol>
 *   <li>the default file bundled inside the JAR (classpath resource),</li>
 *   <li>a file with the same name in the current working directory, or the file given by
 *       {@code -Dchat.config=path/to/file.properties}.</li>
 * </ol>
 *
 * @param host   IP address or host name ({@code server.ip})
 * @param port   TCP port ({@code server.port})
 * @param source human readable description of where the values came from
 */
public record NetworkConfig(String host, int port, String source) {

    public static final String HOST_KEY = "server.ip";
    public static final String PORT_KEY = "server.port";
    public static final String CONFIG_PATH_PROPERTY = "chat.config";

    public NetworkConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Missing server IP address (" + HOST_KEY + ")");
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got " + port);
        }
        host = host.strip();
    }

    /** Loads {@code fileName} using the lookup order described on this class. */
    public static NetworkConfig load(String fileName) {
        Properties properties = new Properties();
        String source = null;

        try (InputStream bundled = NetworkConfig.class.getClassLoader().getResourceAsStream(fileName)) {
            if (bundled != null) {
                properties.load(bundled);
                source = "bundled " + fileName;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read bundled " + fileName, e);
        }

        String overridePath = System.getProperty(CONFIG_PATH_PROPERTY);
        Path external = overridePath != null ? Path.of(overridePath) : Path.of(fileName);
        if (Files.isRegularFile(external)) {
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                properties.load(reader);
                source = external.toAbsolutePath().normalize().toString();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + external, e);
            }
        } else if (overridePath != null) {
            throw new IllegalArgumentException("Configuration file not found: " + external.toAbsolutePath());
        }

        if (source == null) {
            throw new IllegalArgumentException("No configuration found: " + fileName);
        }
        return new NetworkConfig(
                properties.getProperty(HOST_KEY),
                parsePort(properties.getProperty(PORT_KEY)),
                source);
    }

    /** Parses a user-supplied port number, rejecting anything outside 1-65535. */
    public static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing port number (" + PORT_KEY + ")");
        }
        int port;
        try {
            port = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number, got '" + value.strip() + "'");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
        }
        return port;
    }
}
