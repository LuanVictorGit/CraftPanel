package br.com.craftpanel.files;

import java.time.Instant;

public record FileItem(
        String name,
        String relativePath,
        boolean isDirectory,
        long sizeBytes,
        Instant lastModified,
        String extension
) {}
