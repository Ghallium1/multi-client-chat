package com.chatapp.client.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the Model-View separation: no class in the model package may depend on JavaFX. */
class ModelIndependenceTest {

    private static final Path MODEL_SOURCES = Path.of("src/main/java/com/chatapp/client/model");

    @Test
    void modelDoesNotImportJavaFx() throws IOException {
        assertTrue(Files.isDirectory(MODEL_SOURCES), "model sources not found: " + MODEL_SOURCES.toAbsolutePath());
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(MODEL_SOURCES)) {
            offenders = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(ModelIndependenceTest::mentionsJavaFx)
                    .toList();
        }
        assertEquals(List.of(), offenders, "model classes must not use JavaFX");
    }

    private static boolean mentionsJavaFx(Path file) {
        try {
            return Files.readString(file).contains("javafx.");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
