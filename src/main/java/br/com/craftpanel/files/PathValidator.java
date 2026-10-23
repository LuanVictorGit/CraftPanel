package br.com.craftpanel.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        if (Files.isSymbolicLink(resolved) && !Files.exists(resolved)) {
            throw new SecurityException("Broken symbolic link: " + relativePath);
        }

        // A path that does not exist yet is checked through its nearest existing ancestor, so nested folders
        // cannot be created through a link that points outside the root.
        try {
            if (!realPathAllowingMissing(resolved).startsWith(realPathAllowingMissing(normalizedRoot))) {
                throw new SecurityException("Path resolves outside root directory: " + relativePath);
            }
        } catch (IOException e) {
            throw new SecurityException("Failed to verify real path: " + relativePath, e);
        }

        return resolved;
    }

    /**
     * Real path of {@code path} with links resolved, also for paths that do not exist yet (resolved through their
     * nearest existing ancestor). Falls back to the absolute path when the file system cannot be queried.
     */
    public static Path realPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return realPathAllowingMissing(absolute);
        } catch (IOException e) {
            return absolute;
        }
    }

    private static Path realPathAllowingMissing(Path absolute) throws IOException {
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        return existing.toRealPath().resolve(existing.relativize(absolute));
    }
}
