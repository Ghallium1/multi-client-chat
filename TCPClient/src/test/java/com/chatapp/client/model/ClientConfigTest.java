package com.chatapp.client.model;

import com.chatapp.common.config.NetworkConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientConfigTest {

    @Test
    void commandLineArgumentsWin() {
        NetworkConfig config = ClientConfig.resolve("192.168.0.12", "3000");

        assertEquals("192.168.0.12", config.host());
        assertEquals(3000, config.port());
        assertEquals("command line", config.source());
    }

    @Test
    void noArgumentsFallsBackToPropertiesFile() {
        NetworkConfig config = ClientConfig.resolve();

        assertEquals("localhost", config.host());
        assertEquals(3000, config.port());
    }

    @Test
    void rejectsWrongArgumentCount() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve("localhost"));
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve("localhost", "port"));
    }
}
