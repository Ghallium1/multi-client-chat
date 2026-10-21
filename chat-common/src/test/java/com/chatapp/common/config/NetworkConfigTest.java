package com.chatapp.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkConfigTest {

    @AfterEach
    void clearOverride() {
        System.clearProperty(NetworkConfig.CONFIG_PATH_PROPERTY);
    }

    @Test
    void loadsBundledDefaults() {
        NetworkConfig config = NetworkConfig.load("test-network.properties");

        assertEquals("127.0.0.1", config.host());
        assertEquals(4000, config.port());
        assertTrue(config.source().startsWith("bundled"));
    }

    @Test
    void externalFileOverridesBundledDefaults(@TempDir Path dir) throws IOException {
        Path external = dir.resolve("custom.properties");
        Files.writeString(external, "server.ip=192.168.1.20\nserver.port=5050\n");
        System.setProperty(NetworkConfig.CONFIG_PATH_PROPERTY, external.toString());

        NetworkConfig config = NetworkConfig.load("test-network.properties");

        assertEquals("192.168.1.20", config.host());
        assertEquals(5050, config.port());
        assertEquals(external.toAbsolutePath().normalize().toString(), config.source());
    }

    @Test
    void missingOverrideFileIsAnError() {
        System.setProperty(NetworkConfig.CONFIG_PATH_PROPERTY, "does/not/exist.properties");
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.load("test-network.properties"));
    }

    @Test
    void missingConfigurationIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.load("nothing-here.properties"));
    }

    @Test
    void validatesPorts() {
        assertEquals(3000, NetworkConfig.parsePort(" 3000 "));
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.parsePort("abc"));
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.parsePort("0"));
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.parsePort("70000"));
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.parsePort(""));
    }
}
