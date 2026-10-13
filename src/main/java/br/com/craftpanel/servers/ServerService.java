package br.com.craftpanel.servers;

import br.com.craftpanel.config.PanelConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ServerService {
    private final ServerRepository serverRepo;
    private final PanelConfig config;

    public ServerService(ServerRepository serverRepo, PanelConfig config) {
        this.serverRepo = serverRepo;
        this.config = config;
    }

    public List<MinecraftServer> listServers() {
        return serverRepo.findAll();
    }

    public Optional<MinecraftServer> getServer(String id) {
        return serverRepo.findById(id);
    }

    public MinecraftServer createServer(
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
            boolean autoStart
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Server name cannot be empty");
        }
        if (software == null || software.isBlank()) {
            throw new IllegalArgumentException("Server software must be specified");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version must be specified");
        }
        if (minMemoryMb <= 0 || maxMemoryMb < minMemoryMb) {
            throw new IllegalArgumentException("Invalid memory parameters");
        }
        if (serverPort < 1024 || serverPort > 65535) {
            throw new IllegalArgumentException("Server port must be between 1024 and 65535");
        }

        String id = UUID.randomUUID().toString();
        Path dirPath = (serverDir != null && !serverDir.isBlank())
                ? Paths.get(serverDir)
                : Paths.get(config.storage().serversDirectory(), id);

        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create server directory", e);
        }

        String actualJarPath = (jarPath != null && !jarPath.isBlank())
                ? jarPath
                : dirPath.resolve("server.jar").toString();

        String javaExec = (javaExecutable != null && !javaExecutable.isBlank())
                ? javaExecutable
                : "java";

        Instant now = Instant.now();
        MinecraftServer server = new MinecraftServer(
                id,
                name.trim(),
                description != null ? description.trim() : "",
                software.trim(),
                minecraftVersion.trim(),
                actualJarPath,
                dirPath.toAbsolutePath().normalize().toString(),
                javaExec,
                minMemoryMb,
                maxMemoryMb,
                serverPort,
                jvmArgs != null ? jvmArgs.trim() : "",
                autoStart,
                now,
                now
        );

        serverRepo.create(server);
        return server;
    }

    public MinecraftServer updateServer(
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
            boolean autoStart
    ) {
        MinecraftServer existing = serverRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Server not found"));

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Server name cannot be empty");
        }
        if (minMemoryMb <= 0 || maxMemoryMb < minMemoryMb) {
            throw new IllegalArgumentException("Invalid memory parameters");
        }
        if (serverPort < 1024 || serverPort > 65535) {
            throw new IllegalArgumentException("Server port must be between 1024 and 65535");
        }

        String javaExec = (javaExecutable != null && !javaExecutable.isBlank())
                ? javaExecutable
                : existing.javaExecutable();

        MinecraftServer updated = new MinecraftServer(
                existing.id(),
                name.trim(),
                description != null ? description.trim() : "",
                software != null ? software.trim() : existing.software(),
                minecraftVersion != null ? minecraftVersion.trim() : existing.minecraftVersion(),
                jarPath != null && !jarPath.isBlank() ? jarPath : existing.jarPath(),
                serverDir != null && !serverDir.isBlank() ? serverDir : existing.serverDir(),
                javaExec,
                minMemoryMb,
                maxMemoryMb,
                serverPort,
                jvmArgs != null ? jvmArgs.trim() : "",
                autoStart,
                existing.createdAt(),
                Instant.now()
        );

        serverRepo.update(updated);
        return updated;
    }

    public void deleteServer(String id, boolean deleteFiles) {
        MinecraftServer server = serverRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Server not found"));

        serverRepo.delete(id);

        if (deleteFiles && server.serverDir() != null) {
            try {
                Path path = Paths.get(server.serverDir());
                if (Files.exists(path)) {
                    try (var stream = Files.walk(path)) {
                        stream.sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    }
                }
            } catch (IOException ignored) {}
        }
    }
}
