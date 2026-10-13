package br.com.craftpanel.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathValidator {
    public static Path resolveAndValidate(Path rootDir, String relativePath) {
        if (rootDir == null) {
            throw new IllegalArgumentException("Root directory cannot be null");
        }

        Path normalizedRoot = rootDir.toAbsolutePath().normalize();

        if (relativePath == null || relativePath.isBlank() || "/".equals(relativePath.trim()) || "\\".equals(relativePath.trim())) {
            return normalizedRoot;
        }

        if (relativePath.contains("\0")) {
            throw new SecurityException("Null byte detected in path");
        }

        String sanitized = relativePath.trim();
        while (sanitized.startsWith("/") || sanitized.startsWith("\\")) {
            sanitized = sanitized.substring(1);
        }

        Path resolved = normalizedRoot.resolve(sanitized).normalize();

        if (!resolved.startsWith(normalizedRoot)) {
            throw new SecurityException("Path traversal attempt detected: " + relativePath);
        }

        if (Files.exists(resolved)) {
            try {
                Path realResolved = resolved.toRealPath();
                Path realRoot = normalizedRoot.toRealPath();
                if (!realResolved.startsWith(realRoot)) {
                    throw new SecurityException("Symlink target outside root directory: " + relativePath);
                }
            } catch (IOException e) {
                throw new SecurityException("Failed to verify real path: " + relativePath, e);
            }
        } else {
            Path parent = resolved.getParent();
            if (parent != null && Files.exists(parent)) {
                try {
                    Path realParent = parent.toRealPath();
                    Path realRoot = normalizedRoot.toRealPath();
                    if (!realParent.startsWith(realRoot)) {
                        throw new SecurityException("Parent path resolves outside root: " + relativePath);
                    }
                } catch (IOException e) {
                    throw new SecurityException("Failed to verify real parent path", e);
                }
            }
        }

        return resolved;
    }
}
