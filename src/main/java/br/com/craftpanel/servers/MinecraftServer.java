package br.com.craftpanel.servers;

import java.time.Instant;

public record MinecraftServer(
        String id,
        String name,
        String description,
        String software,
        String minecraftVersion,
        String jarPath,
        String serverDir,
        String javaExecutable,
        int minMemoryMb,
        int maxMemoryMb,
        int serverPort,
        String jvmArgs,
        boolean autoStart,
        Instant createdAt,
        Instant updatedAt
) {}
