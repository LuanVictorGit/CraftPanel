package br.com.craftpanel.plugins;

import java.util.List;

public record InstalledPlugin(
        String filename,
        String name,
        String version,
        String author,
        String description,
        String mainClass,
        String apiVersion,
        boolean foliaSupported,
        boolean enabled,
        long sizeBytes,
        List<String> dependencies,
        List<String> softDependencies
) {}
