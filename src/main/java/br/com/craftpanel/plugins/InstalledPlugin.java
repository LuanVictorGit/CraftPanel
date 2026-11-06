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
        boolean loadedInServer,
        boolean pendingRestart,
        boolean pendingRemoval,
        long sizeBytes,
        /** When the jar was placed in the plugins folder, in epoch milliseconds; 0 if the file system cannot tell. */
        long installedAt,
        List<String> dependencies,
        List<String> softDependencies
) {}
