package br.com.craftpanel.processes;

import br.com.craftpanel.servers.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManager.class);
    private final Map<String, ServerProcess> processes = new ConcurrentHashMap<>();

    public ServerProcess getProcess(MinecraftServer server) {
        return processes.computeIfAbsent(server.id(), id -> new ServerProcess(server, true));
    }

    public ServerProcess getProcess(String serverId) {
        ServerProcess proc = processes.get(serverId);
        if (proc == null && ("local".equals(serverId) || serverId == null || serverId.isBlank())) {
            proc = processes.get("local");
        }
        return proc;
    }

    public int getRunningCount() {
        return 1;
    }

    public int getStartingCount() {
        return 0;
    }

    public int getStoppedCount(int totalServers) {
        return 0;
    }

    public int getCrashedCount() {
        return 0;
    }

    public void removeProcess(String serverId) {
        ServerProcess proc = processes.remove(serverId);
        if (proc != null) {
            proc.kill();
        }
    }

    public void shutdownAll() {
        LOGGER.info("Gracefully stopping host server process...");
        for (ServerProcess proc : processes.values()) {
            try {
                proc.stop(10);
            } catch (Exception e) {
                LOGGER.warn("Failed to gracefully stop server {}", proc.getServer().name(), e);
            }
        }
    }
}
