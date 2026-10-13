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
        return processes.computeIfAbsent(server.id(), id -> new ServerProcess(server));
    }

    public ServerProcess getProcess(String serverId) {
        return processes.get(serverId);
    }

    public int getRunningCount() {
        return (int) processes.values().stream()
                .filter(p -> p.getState() == ProcessState.RUNNING)
                .count();
    }

    public int getStartingCount() {
        return (int) processes.values().stream()
                .filter(p -> p.getState() == ProcessState.STARTING)
                .count();
    }

    public int getStoppedCount(int totalServers) {
        int runningOrStarting = (int) processes.values().stream()
                .filter(p -> p.getState() == ProcessState.RUNNING || p.getState() == ProcessState.STARTING)
                .count();
        return Math.max(0, totalServers - runningOrStarting);
    }

    public int getCrashedCount() {
        return (int) processes.values().stream()
                .filter(p -> p.getState() == ProcessState.CRASHED)
                .count();
    }

    public void removeProcess(String serverId) {
        ServerProcess proc = processes.remove(serverId);
        if (proc != null) {
            proc.kill();
        }
    }

    public void shutdownAll() {
        LOGGER.info("Gracefully stopping all active Minecraft servers...");
        for (ServerProcess proc : processes.values()) {
            if (proc.getState() == ProcessState.RUNNING || proc.getState() == ProcessState.STARTING) {
                try {
                    proc.stop(10);
                } catch (Exception e) {
                    LOGGER.warn("Failed to gracefully stop server {}", proc.getServer().name(), e);
                    proc.kill();
                }
            }
        }
    }
}
