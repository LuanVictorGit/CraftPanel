package br.com.craftpanel;

import br.com.craftpanel.files.PathValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathValidatorTest {

    @Test
    void testValidPathResolution(@TempDir Path tempDir) throws IOException {
        Path serverProperties = tempDir.resolve("server.properties");
        Files.createFile(serverProperties);

        Path resolved = PathValidator.resolveAndValidate(tempDir, "server.properties");
        assertEquals(serverProperties.toRealPath(), resolved.toRealPath());
    }

    @Test
    void testPathTraversalRejected(@TempDir Path tempDir) {
        assertThrows(SecurityException.class, () -> {
            PathValidator.resolveAndValidate(tempDir, "../../windows/system32");
        });

        assertThrows(SecurityException.class, () -> {
            PathValidator.resolveAndValidate(tempDir, "../");
        });
    }

    @Test
    void testNullByteRejected(@TempDir Path tempDir) {
        assertThrows(SecurityException.class, () -> {
            PathValidator.resolveAndValidate(tempDir, "server.properties\0.txt");
        });
    }
}
