package br.com.craftpanel.servers;

import br.com.craftpanel.config.PanelConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        throw new UnsupportedOperationException("Only one server instance exists in CraftPanel (the host server).");
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

        MinecraftServer updated = new MinecraftServer(
                existing.id(),
                name != null && !name.isBlank() ? name.trim() : existing.name(),
                description != null ? description.trim() : existing.description(),
                software != null && !software.isBlank() ? software.trim() : existing.software(),
                minecraftVersion != null && !minecraftVersion.isBlank() ? minecraftVersion.trim() : existing.minecraftVersion(),
                jarPath != null && !jarPath.isBlank() ? jarPath.trim() : existing.jarPath(),
                existing.serverDir(),
                javaExecutable != null && !javaExecutable.isBlank() ? javaExecutable.trim() : existing.javaExecutable(),
                minMemoryMb > 0 ? minMemoryMb : existing.minMemoryMb(),
                maxMemoryMb >= minMemoryMb ? maxMemoryMb : existing.maxMemoryMb(),
                serverPort > 0 ? serverPort : existing.serverPort(),
                jvmArgs != null ? jvmArgs.trim() : existing.jvmArgs(),
                autoStart,
                existing.createdAt(),
                Instant.now()
        );

        serverRepo.update(updated);
        return updated;
    }

    public void deleteServer(String id, boolean deleteFiles) {
        throw new UnsupportedOperationException("The host server cannot be deleted.");
    }
}
