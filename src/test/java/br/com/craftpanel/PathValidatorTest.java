package br.com.craftpanel;

import br.com.craftpanel.files.PathValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void testNewNestedPathInsideRootAllowed(@TempDir Path tempDir) {
        Path resolved = PathValidator.resolveAndValidate(tempDir, "plugins/NewPlugin/config/settings.yml");
        assertEquals(tempDir.toAbsolutePath().normalize().resolve("plugins/NewPlugin/config/settings.yml"), resolved);
    }

    @Test
    void testNewNestedPathThroughOutsideLinkRejected(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("server"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        boolean linked;
        try {
            Files.createSymbolicLink(root.resolve("world"), outside);
            linked = true;
        } catch (IOException | UnsupportedOperationException e) {
            linked = false;
        }
        assumeTrue(linked, "Symbolic links cannot be created on this machine");

        assertThrows(SecurityException.class, () -> PathValidator.resolveAndValidate(root, "world/config.yml"));
        assertThrows(SecurityException.class, () -> PathValidator.resolveAndValidate(root, "world/new/nested/config.yml"));
    }
}
